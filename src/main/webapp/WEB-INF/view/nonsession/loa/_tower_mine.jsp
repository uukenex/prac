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
  var WALK = '.,:VHcrdbPTsnYupeX', PORTALS = 'GMUSBXZo'; // [2026-11-18] o = 바다로 나가는 물가(물처럼 보임) // [2026-11-18] X 워프 포인트 -- 위를 걸어 다닌다(멈춰 서서 1.5초 / 공격 버튼이면 창) // [2026-10-24] 나무(T)도 지나갈 수 있다(광석은 없음), s 성 바닥 / n 눈밭 / Y 눈 덮인 소나무
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
  ORE_NAME[31] = '어둠의 증표'; ORE_COL[31] = ['#7a2a8a', '#f0c8ff']; // [2026-11-18] 예전 타락한 대주교의 인장
  ORE_NAME[78] = '어둠의 심장'; ORE_COL[78] = ['#3a1a5a', '#c88aff']; RARE_GEM[78] = 1; // [2026-11-18] 타락한 대주교(반드시)
  ORE_NAME[84] = '태초의 보석'; ORE_COL[84] = ['#fff4b0', '#ffffff']; RARE_GEM[84] = 1; // [2026-11-20] 최후의 방어선 세공사 -- 검기 폭 3칸
  ORE_NAME[83] = '나무'; ORE_COL[83] = ['#7a4a22', '#c8945a']; // [2026-11-19] 하피의 둥지 나무를 곡괭이로 -- 배 수리 재료
  ORE_NAME[82] = '거룡의 심장 조각'; /* [2026-11-20] 예전 이름 내구도 회복 키트 */ ORE_COL[82] = ['#c8a040', '#8ad8ff']; RARE_GEM[82] = 1; // [2026-11-18] 거룡 토벌 1·2위 -- 워프 포인트 내구도 +20
  ORE_NAME[81] = '물의 심장'; ORE_COL[81] = ['#2a7ad8', '#bfe8ff']; RARE_GEM[81] = 1; // [2026-11-18] 심해의 크라켄
  ORE_NAME[80] = '정제된 마석'; ORE_COL[80] = ['#5a1a6a', '#ff8ae8']; RARE_GEM[80] = 1; // [2026-11-18] 마계 마을 채광
  ORE_NAME[79] = '심판의 증표'; ORE_COL[79] = ['#c8a040', '#fff4c0']; RARE_GEM[79] = 1; // [2026-11-18] 심판자 보로네오 50% -- 마계 입구 통행
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
  ORE_NAME[72] = '탑의 증표'; ORE_COL[72] = ['#3a5aa8', '#ffe9a8']; RARE_GEM[72] = 1; // [2026-11-15] 탑의 증표(그림은 시안 전까지 기본 광석 모양)
  ORE_NAME[77] = '빛의 심장'; ORE_COL[77] = ['#f0c040', '#fffbe0']; RARE_GEM[77] = 1; // [2026-11-17] 심판자 보로네오
  ORE_NAME[73] = '황혼의 인장'; ORE_COL[73] = ['#c8501a', '#ffd08a']; RARE_GEM[73] = 1; // [2026-11-17] 서쪽 악마의 들판
  ORE_NAME[74] = '여명의 인장'; ORE_COL[74] = ['#4a8ad8', '#fff0b0']; RARE_GEM[74] = 1; // 동쪽 악마의 들판
  ORE_NAME[75] = '악마의 광석 조각'; ORE_COL[75] = ['#7a1a2a', '#ff6a5a']; // 악마의 땅 채광(정제 10% 미스릴)
  ORE_NAME[76] = '종말의 파편'; ORE_COL[76] = ['#1a0a24', '#c86aff']; RARE_GEM[76] = 1; // 혼돈스러운 마계
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
                    's': '성 바닥', 'W': '성벽', 'n': '눈밭', 'Y': '눈 덮인 숲', 'K': '건물', 'Q': '성당', 'A': '조각상', 'i': '수정 가로등', 'g': '꽃밭', 'X': '텔레포트 게이트', 'u': '바다 모래', 'p': '용궁 길', 'e': '해초', 'O': '산호', 'L': '용궁 기둥', 'Z': '물' };
  var MINI_COL = { 'F': [24, 58, 30], '.': [95, 174, 69], ',': [201, 164, 106], ':': [143, 133, 120], 'V': [232, 215, 168], 'H': [139, 90, 43], '~': [58, 120, 212],
                   'T': [47, 125, 50], '^': [90, 78, 68], 'c': [107, 91, 75], 'r': [125, 109, 92], '#': [30, 24, 20],
                   'h': [190, 140, 60], 'j': [190, 140, 60], 'k': [190, 140, 60], 'l': [190, 140, 60],
                   'd': [227, 196, 122], 'R': [184, 135, 90], 'C': [63, 143, 58], 'b': [236, 217, 160], 'P': [139, 90, 43], 'q': [107, 66, 32], 'o': [58, 120, 212], 'N': [64, 120, 255],
                   's': [169, 164, 154], 'W': [74, 69, 64], 'n': [242, 246, 250], 'Y': [47, 93, 74], 'K': [168, 68, 58], 'Q': [223, 230, 242], 'A': [217, 180, 74], 'i': [122, 216, 255], 'g': [226, 120, 160], 'X': [122, 92, 255], 'u': [111, 159, 192], 'p': [184, 72, 58], 'e': [47, 122, 106], 'O': [224, 122, 138], 'L': [138, 30, 30], 'Z': [58, 120, 212], 'I': [127, 208, 240] };

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
  // [2026-11-18] 텔레포트 게이트 -> 3x3 워프 포인트("디아블로 웨이포인트 / 로스트아크 트라이포트 느낌" -- 모티브만):
  //  둥근 돌 단(계단 테두리 + 매끈한 원판), 가운데 금빛 룬 원(사방 별 문양 -- 숨 쉬듯 밝아졌다 어두워지고 바깥 룬 고리가 돈다),
  //  네 귀퉁이에 위로 솟은 돌 오벨리스크(앞면 푸른 룬, 꼭대기 푸른 불꽃). 단은 칸 그림(9칸), 오벨리스크/빛은 사람 위에 따로(wpDraw).
  function wpTiles(stone, T, mk, px) { // 단(3x3) -- [프레임 0/1] 48x48
    return [0, 1].map(function (f) {
      var S = T * 3, c = mk(S, S), g = c.getContext('2d'), C = S / 2 - 0.5;
      for (var q = 0; q < 9; q++) g.drawImage(stone, (q % 3) * T, Math.floor(q / 3) * T);
      for (var y = 0; y < S; y++) for (var x = 0; x < S; x++) {
        var dx = x - C, dy = y - C, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx), lit = dy < 0 || dx < -4; // 빛은 왼위에서
        if (r > 23.6) { if (r < 25 && dy > 0) px(g, 'rgba(0,0,0,.25)', x, y); continue; }
        var col;
        if (r > 22.6) col = '#3c3a36';
        else if (r > 18) { // 바깥 계단 -- 돌 블록(22.5도마다 이음매)
          var seg = Math.floor((a + Math.PI) / (Math.PI / 8)), edge = Math.abs(((a + Math.PI) / (Math.PI / 8)) % 1) < 0.08;
          col = edge || r < 18.7 ? '#4e4a43' : r > 21.8 ? (lit ? '#c9c1ae' : '#7e776a') : (seg % 2 ? '#a69f8e' : '#9a9382');
        }
        else if (r > 16.8) col = '#5a554c'; // 단 사이 그림자
        else if (r > 15.6) col = lit ? '#d6cfbc' : '#8a8374'; // 원판 테두리
        else if (r > 13.4) col = '#b8b09c';
        else if (r > 12.4) col = '#6e6858'; // 새긴 원
        else if (r > 10.6) { var rs = Math.floor((a + Math.PI) / (Math.PI / 12)); col = rs % 3 === 0 ? '#6e6858' : (rs + f) % 2 ? '#ffe9a0' : '#e8b84a'; } // 룬 고리
        else if (r > 9.8) col = '#6e6858';
        else {
          var ax = Math.abs(dx), ay = Math.abs(dy), star = Math.min(ax, ay) < 1.4 + (9 - Math.max(ax, ay)) * 0.28 && Math.max(ax, ay) < 9.2, diag = Math.abs(ax - ay) < 1.1 && r < 6.5;
          col = r < 2.2 ? '#fffbe8' : star ? (f ? '#fff1b8' : '#f2c860') : diag ? '#d8a840' : r < 4.5 ? '#c9a24a' : '#a89e88';
        }
        px(g, col, x, y);
      }
      return c;
    });
  }
  function wpSplit(frames, T, mk) { // [프레임][0~8 왼위부터]
    return frames.map(function (c) {
      return [0, 1, 2, 3, 4, 5, 6, 7, 8].map(function (q) { var o = mk(T, T); o.getContext('2d').drawImage(c, (q % 3) * T, Math.floor(q / 3) * T, T, T, 0, 0, T, T); return o; });
    });
  }
  /** 솟은 부분/빛(사람 위에): cx, cy = 3x3 단 가운데(화면 좌표), s = 한 칸 크기. */
  function wpDraw(ctx, cx, cy, s, now) {
    var u = s / 16, k = 0.5 + 0.5 * Math.sin(now / 650);
    ctx.save();
    ctx.imageSmoothingEnabled = false;
    ctx.globalCompositeOperation = 'lighter';
    // 룬 원 빛(숨 쉬듯) + 위로 오르는 금빛 알갱이
    var gr = ctx.createRadialGradient(cx, cy, 0, cx, cy, s * 0.75);
    gr.addColorStop(0, 'rgba(255,210,120,' + (0.12 + 0.14 * k) + ')'); gr.addColorStop(1, 'rgba(255,170,60,0)');
    ctx.fillStyle = gr; ctx.beginPath(); ctx.arc(cx, cy, s * 0.75, 0, Math.PI * 2); ctx.fill();
    for (var i = 0; i < 10; i++) {
      var t = ((now / 1900) + i / 10) % 1, ang = i * 2.39, r = s * 0.62 * (0.3 + 0.7 * ((i * 7) % 10) / 10), z = Math.max(1, Math.round(u * (i % 3 ? 1 : 2)));
      ctx.fillStyle = 'rgba(255,230,150,' + (0.9 * (1 - t)) + ')';
      ctx.fillRect(Math.round(cx + Math.cos(ang) * r), Math.round(cy + Math.sin(ang) * r * 0.6 - t * s * 1.6), z, z);
    }
    ctx.globalCompositeOperation = 'source-over';
    // [2026-11-18] "기둥 없애고 불꽃" -- 네 귀퉁이 낮은 돌 화로 + 푸른 불꽃(뒤 두 개 먼저)
    var off = 1.25 * s;
    [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(function (p, i) {
      var bx = cx + p[0] * off, by = cy + p[1] * off + 0.2 * s; // 화로 아래 끝
      ctx.fillStyle = 'rgba(0,0,0,.28)'; ctx.fillRect(Math.round(bx - 5 * u), Math.round(by - 1 * u), Math.round(11 * u), Math.round(3 * u));
      // 돌 화로(사발): 받침 + 넓은 그릇, 테두리 위 밝게
      ctx.fillStyle = '#3a3732'; ctx.fillRect(Math.round(bx - 2.5 * u), Math.round(by - 3 * u), Math.round(5 * u), Math.round(3 * u));
      ctx.fillStyle = '#8a8374'; ctx.fillRect(Math.round(bx - 1.5 * u), Math.round(by - 3 * u), Math.round(3 * u), Math.round(2 * u));
      ctx.fillStyle = '#3a3732'; ctx.fillRect(Math.round(bx - 5 * u), Math.round(by - 7 * u), Math.round(10 * u), Math.round(4 * u));
      ctx.fillStyle = '#a69f8e'; ctx.fillRect(Math.round(bx - 4 * u), Math.round(by - 6 * u), Math.round(8 * u), Math.round(2 * u));
      ctx.fillStyle = '#d6cfbc'; ctx.fillRect(Math.round(bx - 4 * u), Math.round(by - 7 * u), Math.round(8 * u), Math.round(1 * u));
      ctx.fillStyle = '#1c2a3a'; ctx.fillRect(Math.round(bx - 3 * u), Math.round(by - 7 * u), Math.round(6 * u), Math.round(1 * u));
      var fx = bx, fy = by - 7 * u, fl = 0.75 + 0.25 * Math.sin(now / 90 + i * 1.7) * Math.sin(now / 37 + i);
      ctx.globalCompositeOperation = 'lighter';
      var fg = ctx.createRadialGradient(fx, fy - 4 * u, 0, fx, fy - 4 * u, 12 * u);
      fg.addColorStop(0, 'rgba(120,200,255,' + (0.5 * fl) + ')'); fg.addColorStop(1, 'rgba(40,110,255,0)');
      ctx.fillStyle = fg; ctx.beginPath(); ctx.arc(fx, fy - 4 * u, 12 * u, 0, Math.PI * 2); ctx.fill();
      ctx.globalCompositeOperation = 'source-over';
      var fh = (11 + 3 * fl) * u; // 불꽃: 아래 넓고 위로 뾰족, 흔들림, 가운데 하얀 속불
      for (var y = 0; y < fh; y += u) {
        var q = y / fh, w = (1 - q * q) * 7 * u * (0.85 + 0.15 * fl), sway = Math.sin(now / 120 + i + q * 3) * q * 1.6 * u;
        ctx.fillStyle = q < 0.7 ? '#3a86d8' : '#2a5aa8';
        ctx.fillRect(Math.round(fx - w / 2 + sway), Math.round(fy - y), Math.max(1, Math.round(w)), Math.max(1, Math.round(u)));
        var w2 = w * 0.62; if (q < 0.85) { ctx.fillStyle = '#7ad8ff'; ctx.fillRect(Math.round(fx - w2 / 2 + sway), Math.round(fy - y), Math.max(1, Math.round(w2)), Math.max(1, Math.round(u))); }
        var w3 = w * 0.28; if (q < 0.55) { ctx.fillStyle = '#e8f8ff'; ctx.fillRect(Math.round(fx - w3 / 2 + sway * 0.6), Math.round(fy - y), Math.max(1, Math.round(w3)), Math.max(1, Math.round(u))); }
      }
      for (var e = 0; e < 2; e++) { var t = ((now / 700) + e * 0.5 + i * 0.13) % 1; ctx.fillStyle = 'rgba(160,225,255,' + (1 - t) + ')'; ctx.fillRect(Math.round(fx + Math.sin(now / 200 + e * 3 + i) * 3 * u), Math.round(fy - fh - t * 8 * u), Math.max(1, Math.round(u)), Math.max(1, Math.round(u))); }
    });
    ctx.restore();
  }
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
    // ---- [2026-11-15] 요새 도시 엘리시온 / 탑 입구 전용 그림(왕도와 다른 빛깔: 흰 대리석 + 금 상감, 푸른 모자이크 길, 푸른 지붕, 꽃나무) ----
    var E = tiles.ely = {};
    E.marble = [1, 2].map(function (s) {
      return tile('#f1ece1', [['#e6dfd0', 9], ['#fbf8f1', 7]], 400 + s, function (g) {
        px(g, '#d8d0bf', 0, 15, T, 1); px(g, '#d8d0bf', 15, 0, 1, T); px(g, '#fdfbf6', 0, 0, T, 1); px(g, '#fdfbf6', 0, 0, 1, T);
        px(g, '#e4dccb', 3, 5, 4, 1); px(g, '#e4dccb', 6, 6, 3, 1); px(g, '#e4dccb', 10, 10, 3, 1);
        if (s === 2) { px(g, '#d9b44a', 7, 5, 2, 6); px(g, '#d9b44a', 5, 7, 6, 2); px(g, '#f6e3a0', 7, 7, 2, 2); }
      });
    });
    E.mosaic = [1, 2].map(function (s) {
      return tile(s === 1 ? '#3557a8' : '#2c4a96', [['#27428a', 6]], 410 + s, function (g) {
        px(g, '#e8c860', 0, 0, T, 1); px(g, '#e8c860', 0, 0, 1, T);
        for (var i = 0; i < 4; i++) { px(g, '#7aa0e8', 8 - i, 4 + i, 1 + 2 * i, 1); px(g, '#7aa0e8', 8 - i, 12 - i, 1 + 2 * i, 1); }
        px(g, '#f6e3a0', 7, 7, 3, 3); px(g, '#ffffff', 8, 8);
      });
    });
    E.wall = [0, 1].map(function (s) { // [2026-11-16] "요새 도시로" -- 회색 돌 성벽: 0 = 벽 윗면(흉벽 돌), 1 = 바닥과 닿는 아랫단(톱니 흉벽 + 그늘)
      return tile('#868b98', [['#7a7f8c', 10], ['#939aa8', 5]], 420 + s, function (g) {
        for (var y = 0; y < T; y += 8) { px(g, '#5c6170', 0, y + 7, T, 1); px(g, '#5c6170', (y / 8) % 2 ? 4 : 12, y, 1, 7); px(g, '#a9afbc', 0, y, T, 1); }
        if (s === 1) {
          px(g, '#4c515e', 0, 8, T, 8); px(g, '#2a2e3a', 0, 15, T, 1);
          for (var x = 0; x < T; x += 4) { px(g, '#8f95a3', x, 6, 3, 4); px(g, '#b4bac6', x, 6, 3, 1); px(g, '#3a3e4a', x + 3, 6, 1, 4); } // 톱니 흉벽
          px(g, '#6a6f7c', 0, 12, T, 1);
        }
      });
    });
    E.towerC = {}; // 성탑(L 3x3 덩어리) -- 이웃(위/오른쪽/아래/왼쪽이 L인지)마다 한 장
    E.tower = function (x, y) {
      var u = cellAt(x, y - 1) === 'L', r2 = cellAt(x + 1, y) === 'L', d = cellAt(x, y + 1) === 'L', l = cellAt(x - 1, y) === 'L', k = (u ? 1 : 0) + (r2 ? 2 : 0) + (d ? 4 : 0) + (l ? 8 : 0);
      if (E.towerC[k]) return E.towerC[k];
      return E.towerC[k] = tile('#70758a', [['#666b80', 10]], 440 + k, function (g) {
        px(g, '#8a90a6', 0, 0, T, 1); px(g, '#565b6e', 0, 8, T, 1); px(g, '#565b6e', 8, 0, 1, 8); px(g, '#565b6e', 3, 9, 1, 7);
        if (!u) for (var x1 = 0; x1 < T; x1 += 4) { px(g, '#a9afc0', x1, 0, 3, 3); px(g, '#3a3e4e', x1 + 3, 0, 1, 3); }
        if (!l) for (var y1 = 0; y1 < T; y1 += 4) { px(g, '#a9afc0', 0, y1, 3, 3); px(g, '#3a3e4e', 0, y1 + 3, 3, 1); }
        if (!r2) for (var y2 = 0; y2 < T; y2 += 4) { px(g, '#a9afc0', 13, y2, 3, 3); px(g, '#3a3e4e', 13, y2 + 3, 3, 1); }
        if (!d) { px(g, '#454a5c', 0, 10, T, 6); px(g, '#2a2e3c', 0, 15, T, 1); for (var x3 = 0; x3 < T; x3 += 4) { px(g, '#8f95a8', x3, 8, 3, 4); px(g, '#3a3e4e', x3 + 3, 8, 1, 4); } px(g, '#1a1c26', 7, 11, 2, 3); } // 아랫면 + 화살 구멍
        if (u && r2 && d && l) { px(g, '#3a2a1a', 7, 3, 1, 10); px(g, '#c8202a', 8, 3, 5, 4); px(g, '#e84a52', 8, 3, 5, 1); px(g, '#8a1018', 11, 5, 2, 2); } // 가운데 -- 붉은 깃발
      });
    };
    E.roof = [1, 2].map(function (s) {
      return tile('#2f5fae', [['#274f96', 8]], 430 + s, function (g) {
        for (var y = 1; y < T; y += 4) { px(g, '#1f4080', 0, y + 2, T, 1); for (var x = (y % 8 === 1 ? 0 : 2); x < T; x += 4) px(g, '#5a8fe0', x, y, 2, 1); }
        if (s === 2) { px(g, '#e8c860', 7, 5, 2, 6); px(g, '#f6e3a0', 6, 7, 4, 2); }
      });
    });
    E.facade = [1, 2].map(function (s) {
      return tile('#f4f0e6', [['#e8e2d3', 6]], 440 + s, function (g) {
        px(g, '#d9b44a', 0, 0, T, 1); px(g, '#d6cdb8', 0, 15, T, 1);
        [2, 10].forEach(function (x0) { px(g, '#3b5a8a', x0, 4, 4, 8); px(g, '#3b5a8a', x0 + 1, 3, 2, 1); px(g, s === 2 ? '#ffd66b' : '#8fb8e8', x0 + 1, 5, 2, 6); px(g, '#d9b44a', x0 - 1, 12, 6, 1); });
      });
    });
    E.house = [1, 2].map(function (s) {
      return tile('#f4f0e6', [['#e8e2d3', 6]], 450 + s, function (g) {
        px(g, '#1f4080', 0, 0, T, 2); px(g, '#e8c860', 0, 2, T, 1); px(g, '#cfc6b0', 0, 15, T, 1);
        if (s === 1) { px(g, '#d9b44a', 4, 5, 8, 10); px(g, '#5a3a1c', 5, 7, 6, 8); px(g, '#5a3a1c', 6, 6, 4, 1); px(g, '#7a5230', 6, 8, 2, 7); px(g, '#7a5230', 8, 8, 2, 7); px(g, '#f6e3a0', 7, 11, 2, 1); }
        else { px(g, '#3b5a8a', 3, 5, 10, 6); px(g, '#8fb8e8', 4, 6, 3, 4); px(g, '#8fb8e8', 9, 6, 3, 4); px(g, '#3f8a3a', 2, 11, 12, 2); ['#e84a6a', '#ffd66b', '#e88ab0', '#ffffff'].forEach(function (c, i) { px(g, c, 3 + i * 3, 11); px(g, c, 4 + i * 3, 12); }); }
      });
    });
    E.dome = [1, 2].map(function (s) { // 대신전 지붕(금빛)
      return tile('#d9b44a', [['#c49a34', 8]], 460 + s, function (g) {
        for (var y = 1; y < T; y += 4) { px(g, '#a8842a', 0, y + 2, T, 1); for (var x = (y % 8 === 1 ? 0 : 2); x < T; x += 4) px(g, '#f6e3a0', x, y, 2, 1); }
        if (s === 2) { px(g, '#ffffff', 6, 5, 4, 6); px(g, '#ffffff', 5, 6, 6, 4); px(g, '#7ad8ff', 7, 7, 2, 2); }
      });
    });
    E.templeWall = [1, 2].map(function (s) {
      return tile('#f6f3ec', [['#eae5d8', 6]], 470 + s, function (g) {
        px(g, '#d9b44a', 0, 0, T, 2); px(g, '#f6e3a0', 0, 0, T, 1);
        px(g, '#d6cdb8', 1, 2, 2, 14); px(g, '#fdfbf6', 1, 2, 1, 14); px(g, '#d6cdb8', 13, 2, 2, 14); px(g, '#fdfbf6', 13, 2, 1, 14); // 기둥
        px(g, '#2f5fae', 5, 5, 6, 10); px(g, '#2f5fae', 6, 4, 4, 1); px(g, s === 2 ? '#ffd66b' : '#7ad8ff', 7, 6, 2, 8); px(g, '#e84a6a', 6, 9, 1, 2); px(g, '#e84a6a', 9, 9, 1, 2);
      });
    });
    E.lawn = [1, 2, 3].map(function (s) {
      return tile('#6fc060', [['#5cae50', 14], ['#8ad878', 10]], 480 + s, function (g) {
        if (s === 3) { px(g, '#f6a8c8', 3, 4); px(g, '#ffffff', 4, 4); px(g, '#ffe066', 11, 9); px(g, '#ffffff', 12, 10); px(g, '#f6a8c8', 7, 12); px(g, '#c8a8ff', 13, 3); }
      });
    });
    E.tree = [1, 2].map(function (s) { // 꽃나무(분홍 / 흰)
      return over(E.lawn[0], function (g) {
        var a = s === 1 ? ['#e88ab0', '#f6b8d0', '#fbe0ea'] : ['#c8d4f0', '#e6ecfa', '#ffffff'];
        px(g, 'rgba(0,0,0,.22)', 3, 13, 10, 2);
        px(g, '#7a5230', 7, 9, 2, 5); px(g, '#5a3a1c', 8, 9, 1, 5);
        px(g, a[0], 3, 4, 10, 6); px(g, a[0], 5, 2, 6, 9); px(g, a[1], 4, 3, 8, 5); px(g, a[1], 6, 2, 4, 1); px(g, a[2], 5, 4, 3, 2); px(g, a[2], 9, 6, 2, 1); px(g, a[2], 7, 3);
        px(g, '#ffffff', 4, 6); px(g, '#ffffff', 10, 4); px(g, a[0], 2, 11); px(g, a[1], 12, 12);
      });
    });
    E.bed = [1, 2].map(function (s) { // 꽃밭(낮은 울타리 + 꽃)
      return tile('#3f8a3a', [['#327a30', 10], ['#4fa048', 8]], 490 + s, function (g, r) {
        px(g, '#2a6628', 0, 0, T, 1); px(g, '#2a6628', 0, 15, T, 1);
        var cs = ['#e84a6a', '#ffd66b', '#e88ab0', '#ffffff', '#c8a8ff', '#ff9a4a'];
        for (var i = 0; i < 11; i++) { var x = 1 + Math.floor(r() * 13), y = 2 + Math.floor(r() * 11); px(g, cs[(i + s) % cs.length], x, y, 2, 2); px(g, '#fff6c0', x, y); }
      });
    });
    E.statue = over(E.marble[0], function (g) { // 금빛 날개 여신상
      px(g, 'rgba(0,0,0,.2)', 3, 14, 11, 2);
      px(g, '#b9b2a0', 4, 11, 8, 4); px(g, '#e9e4d8', 4, 11, 8, 1); px(g, '#8f8672', 4, 14, 8, 1); px(g, '#d9b44a', 5, 12, 6, 1);
      px(g, '#d9b44a', 7, 4, 2, 7); px(g, '#f6e3a0', 7, 2, 2, 2); px(g, '#a8842a', 8, 5, 1, 6);
      px(g, '#e8c860', 4, 4, 3, 1); px(g, '#e8c860', 9, 4, 3, 1); px(g, '#e8c860', 3, 5, 3, 2); px(g, '#e8c860', 10, 5, 3, 2); px(g, '#f6e3a0', 2, 6, 2, 2); px(g, '#f6e3a0', 12, 6, 2, 2);
      px(g, '#d9b44a', 6, 9, 4, 2);
    });
    E.lamp = [0, 1].map(function (f) { // 수정 가로등(빛이 깜빡)
      return over(E.marble[0], function (g) {
        px(g, 'rgba(0,0,0,.2)', 5, 14, 7, 2);
        px(g, '#3a4468', 7, 6, 2, 9); px(g, '#5a6890', 7, 6, 1, 9); px(g, '#2a3050', 6, 14, 4, 1); px(g, '#d9b44a', 6, 9, 4, 1); px(g, '#d9b44a', 6, 6, 4, 1);
        px(g, f ? 'rgba(160,236,255,.55)' : 'rgba(122,216,255,.35)', 4, 0, 8, 7);
        px(g, '#7ad8ff', 6, 1, 4, 5); px(g, '#7ad8ff', 7, 0, 2, 1); px(g, f ? '#ffffff' : '#e8fbff', 7, 2, 2, 3);
      });
    });
    E.bridge = tile('#e6e0d2', [['#d8d0bf', 8]], 495, function (g) {
      px(g, '#b9b2a0', 0, 0, T, 2); px(g, '#b9b2a0', 0, 14, T, 2); px(g, '#f8f5ee', 0, 2, T, 1); px(g, '#f8f5ee', 0, 13, T, 1);
      for (var x = 1; x < T; x += 4) { px(g, '#8f8672', x, 0, 1, 2); px(g, '#8f8672', x, 14, 1, 2); }
      px(g, '#d9b44a', 7, 7, 2, 2); px(g, '#d8d0bf', 0, 7, 5, 1); px(g, '#d8d0bf', 11, 8, 5, 1);
    });
    E.gate = tile('#e9e4d8', [['#ddd6c6', 6]], 496, function (g) { // 금빛 아치문
      px(g, '#a8842a', 2, 2, 12, 14); px(g, '#d9b44a', 3, 1, 10, 15); px(g, '#f6e3a0', 4, 1, 8, 1);
      px(g, '#1f2f5a', 4, 4, 8, 12); px(g, '#1f2f5a', 5, 3, 6, 1); px(g, '#3557a8', 5, 6, 6, 10); px(g, '#7aa0e8', 7, 8, 2, 8); px(g, '#f6e3a0', 8, 4, 1, 12);
    });
    // ---- [2026-11-16] 악마의 들판(테마 3) -- 그을린 땅, 불씨가 비치는 갈라진 땅, 무너진 길, 용암, 검은 바위, 죽은 나무, 요새 성문 ----
    var Dm = tiles.dem = {};
    Dm.ground = [1, 2, 3].map(function (s) { return tile('#3b2a2f', [['#33242a', 14], ['#46333a', 8], ['#2a1b20', 4]], 600 + s); });
    Dm.crack = [0, 1].map(function (f) {
      return over(Dm.ground[0], function (g) {
        px(g, '#170c0e', 2, 2, 1, 4); px(g, '#170c0e', 3, 6, 4, 1); px(g, '#170c0e', 7, 7, 1, 4); px(g, '#170c0e', 8, 11, 5, 1); px(g, '#170c0e', 12, 4, 1, 7);
        px(g, f ? '#ff7a2a' : '#c8381a', 4, 6, 2, 1); px(g, f ? '#c8381a' : '#ff7a2a', 9, 11, 3, 1); px(g, f ? '#ffb04a' : '#ff7a2a', 12, 7, 1, 2);
      });
    });
    Dm.road = [1, 2].map(function (s) {
      return tile('#4d4753', [['#413b47', 10], ['#5a5460', 5]], 610 + s, function (g) {
        px(g, '#2a2530', 0, 7, T, 1); px(g, '#2a2530', 7, 0, 1, 7); px(g, '#2a2530', 11, 8, 1, 8); px(g, '#625c68', 0, 0, T, 1); px(g, '#625c68', 0, 8, T, 1);
        if (s === 2) { px(g, '#33242a', 2, 10, 5, 4); px(g, '#2a1b20', 3, 11, 3, 2); }
      });
    });
    Dm.lava = [0, 1].map(function (f) {
      return tile('#d8421a', [['#ff7a2a', 16], ['#ffb04a', 6], ['#a82a10', 9]], 620 + f, function (g) {
        px(g, '#ffe08a', f ? 4 : 9, f ? 5 : 10, 3, 1); px(g, '#ffe08a', f ? 11 : 3, f ? 12 : 3, 2, 1); px(g, '#8a1c0a', f ? 1 : 12, f ? 9 : 6, 3, 1);
      });
    });
    Dm.rock = [1, 2].map(function (s) {
      return tile('#1f181d', [['#171116', 12], ['#2b2229', 6]], 630 + s, function (g) {
        px(g, '#3b3039', 2, 2, 5, 1); px(g, '#3b3039', 9, 8, 4, 1); px(g, '#0e090c', 0, 15, T, 1); px(g, '#0e090c', 15, 0, 1, T);
        if (s === 2) { px(g, '#c8381a', 6, 11, 1, 3); px(g, '#ff7a2a', 6, 11, 1, 1); }
      });
    });
    Dm.rockFace = tile('#2b2229', [['#1f181d', 10]], 640, function (g) { px(g, '#4a3e48', 0, 0, T, 2); px(g, '#3b3039', 0, 2, T, 1); px(g, '#120c10', 0, 11, T, 5); px(g, '#0a0608', 0, 15, T, 1); px(g, '#171116', 4, 3, 1, 8); px(g, '#171116', 11, 3, 1, 8); });
    Dm.tree = over(Dm.ground[0], function (g) { // 죽은 나무
      px(g, 'rgba(0,0,0,.3)', 4, 14, 9, 2);
      px(g, '#171012', 7, 5, 2, 10); px(g, '#2b2024', 7, 5, 1, 10); px(g, '#171012', 4, 3, 1, 4); px(g, '#171012', 5, 6, 2, 1); px(g, '#171012', 11, 2, 1, 5); px(g, '#171012', 9, 6, 2, 1);
      px(g, '#171012', 3, 1, 1, 2); px(g, '#171012', 12, 0, 1, 2); px(g, '#171012', 6, 14, 4, 1); px(g, '#ff7a2a', 8, 9, 1, 1);
    });
    Dm.gate = tile('#868b98', [['#7a7f8c', 8]], 650, function (g) { // 요새 성문(들판 쪽에서 본)
      px(g, '#5c6170', 1, 1, 14, 15); px(g, '#a9afbc', 2, 1, 12, 1); for (var x = 1; x < 15; x += 4) px(g, '#a9afbc', x, 0, 3, 2);
      px(g, '#1a1418', 4, 5, 8, 11); px(g, '#1a1418', 5, 4, 6, 1); px(g, '#6a5a2a', 5, 6, 6, 10); px(g, '#ffcf6a', 7, 8, 2, 8); px(g, '#3a3e4a', 4, 9, 8, 1);
    });
    // ---- 탑 안(방/계단방/수문장의 방/마을) ----
    var R = tiles.twr = {};
    R.floor = [1, 2, 3].map(function (s) {
      return tile('#4a4f66', [['#434860', 10], ['#555b76', 6]], 500 + s, function (g) {
        px(g, '#353a4e', 0, 15, T, 1); px(g, '#353a4e', 15, 0, 1, T); px(g, '#5f6684', 0, 0, T, 1); px(g, '#5f6684', 0, 0, 1, T);
        if (s === 2) { px(g, '#353a4e', 7, 0, 1, T); px(g, '#5f6684', 8, 0, 1, T); }
        if (s === 3) { px(g, '#7ad8ff', 7, 5, 2, 1); px(g, '#7ad8ff', 5, 7, 6, 2); px(g, '#7ad8ff', 7, 10, 2, 1); px(g, '#c8f2ff', 7, 7, 2, 2); }
      });
    });
    R.crack = over(R.floor[0], function (g) { px(g, '#2a2e40', 3, 3, 1, 3); px(g, '#2a2e40', 4, 6, 3, 1); px(g, '#2a2e40', 7, 7, 1, 4); px(g, '#2a2e40', 8, 11, 4, 1); px(g, '#2a2e40', 11, 4, 2, 1); });
    R.carpet = tile('#8a2038', [['#7a1a30', 8]], 510, function (g) {
      px(g, '#a52c48', 3, 3, 10, 10);
      for (var i = 1; i < T; i += 4) { px(g, '#e8c860', i, 1); px(g, '#e8c860', i, 14); px(g, '#e8c860', 1, i); px(g, '#e8c860', 14, i); }
      for (var k = 0; k < 3; k++) { px(g, '#e8c860', 8 - k, 5 + k, 1 + 2 * k, 1); px(g, '#e8c860', 8 - k, 11 - k, 1 + 2 * k, 1); }
      px(g, '#fff0b0', 8, 8);
    });
    R.wall = [1, 2].map(function (s) {
      return tile('#2a2e44', [['#242840', 8]], 520 + s, function (g) {
        for (var y = 0; y < T; y += 4) { px(g, '#3c4262', 0, y, T, 1); px(g, '#1c2034', 0, y + 3, T, 1); for (var x = ((y / 4) % 2) * 4; x < T; x += 8) px(g, '#1c2034', x, y, 1, 4); }
      });
    });
    R.wallFace = [0, 1, 2].map(function (f) { // 바닥과 닿는 벽면: 0 = 민벽, 1/2 = 횃불(두 프레임)
      return tile('#343a58', [['#2e3450', 8]], 530, function (g) {
        for (var y = 0; y < 12; y += 4) { px(g, '#454c70', 0, y, T, 1); px(g, '#242840', 0, y + 3, T, 1); for (var x = ((y / 4) % 2) * 4; x < T; x += 8) px(g, '#242840', x, y, 1, 4); }
        px(g, '#b8943a', 0, 0, T, 1); px(g, '#1c2034', 0, 12, T, 4); px(g, '#12162a', 0, 15, T, 1);
        if (f) {
          px(g, 'rgba(255,170,60,' + (f === 1 ? 0.22 : 0.32) + ')', 3, 0, 10, 10);
          px(g, '#5a4a2a', 7, 6, 2, 5); px(g, '#8a7240', 6, 6, 4, 1);
          if (f === 1) { px(g, '#ff9a2a', 6, 2, 4, 4); px(g, '#ffe066', 7, 3, 2, 3); px(g, '#ff7a1a', 7, 1, 1, 1); }
          else { px(g, '#ff7a1a', 6, 1, 4, 5); px(g, '#fff0a0', 7, 2, 2, 4); px(g, '#ff9a2a', 8, 0, 1, 1); }
        }
      });
    });
    R.pillar = over(R.floor[0], function (g) {
      px(g, 'rgba(0,0,0,.3)', 4, 13, 10, 3);
      px(g, '#8088a8', 4, 1, 8, 13); px(g, '#a8b0cc', 5, 1, 2, 13); px(g, '#5a6080', 10, 1, 2, 13);
      px(g, '#d9b44a', 3, 1, 10, 2); px(g, '#f6e3a0', 3, 1, 10, 1); px(g, '#d9b44a', 3, 12, 10, 2); px(g, '#a8842a', 3, 13, 10, 1);
    });
    R.door = over(R.wall[0], function (g) { // 방 사이의 문(금테 + 푸른 빛)
      px(g, '#a8842a', 1, 1, 14, 15); px(g, '#d9b44a', 2, 1, 12, 15); px(g, '#f6e3a0', 2, 1, 12, 1);
      px(g, '#141a38', 3, 3, 10, 13); px(g, '#2c4a96', 4, 5, 8, 11); px(g, '#7aa0e8', 6, 7, 4, 9); px(g, '#c8e4ff', 7, 9, 2, 7);
    });
    R.doorShut = over(R.wall[0], function (g) { // [2026-11-16] 닫힌 문 -- 쇠창살(전투방에서 방을 지키는 몬스터를 쓰러뜨릴 때까지)
      px(g, '#a8842a', 1, 1, 14, 15); px(g, '#d9b44a', 2, 1, 12, 15); px(g, '#f6e3a0', 2, 1, 12, 1);
      px(g, '#160a0e', 3, 3, 10, 13); px(g, '#3a1018', 4, 5, 8, 11);
      px(g, '#565b68', 3, 6, 10, 1); px(g, '#565b68', 3, 11, 10, 1);
      for (var bx = 4; bx <= 11; bx += 2) { px(g, '#8a8f9c', bx, 3, 1, 13); px(g, '#d0d4de', bx, 3, 1, 2); px(g, '#d0d4de', bx, 6, 1, 1); px(g, '#d0d4de', bx, 11, 1, 1); }
      px(g, '#ff5a4a', 7, 8, 2, 2); px(g, '#ffb0a0', 7, 8, 1, 1);
    });
    R.up = tile('#5b6180', null, 540, function (g) { // 올라가는 계단(위로 갈수록 밝게)
      ['#454a62', '#555b76', '#68708e', '#8088a8'].forEach(function (c, i) { px(g, c, 1, 12 - i * 4, 14, 4); px(g, '#2a2e40', 1, 15 - i * 4, 14, 1); });
      px(g, '#d9b44a', 0, 0, 1, T); px(g, '#d9b44a', 15, 0, 1, T); px(g, '#f6e3a0', 7, 1, 2, 1); px(g, '#f6e3a0', 6, 2, 4, 1);
    });
    R.down = tile('#2a2e40', null, 541, function (g) { // 내려가는 계단(아래로 갈수록 어둡게)
      ['#555b76', '#454a62', '#353a4e', '#1c2034'].forEach(function (c, i) { px(g, c, 1, i * 4, 14, 4); px(g, '#12162a', 1, 3 + i * 4, 14, 1); });
      px(g, '#b8943a', 0, 0, 1, T); px(g, '#b8943a', 15, 0, 1, T); px(g, '#7ad8ff', 6, 13, 4, 1); px(g, '#7ad8ff', 7, 14, 2, 1);
    });
    tiles.wp = wpSplit(wpTiles(tiles.stone[0], T, mk, px), T, mk); // [2026-11-18] 3x3 워프 포인트(단) -- 불꽃/빛은 wpDraw
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
    // ---- [2026-11-17] 최후의 방어선(테마 3에 더) -- 진지 바닥(다진 흙 + 널빤지), 무너진 돌 성벽, 해자 널다리, 화톳불 ----
    Dm.camp = [0, 1].map(function (s) { return tile('#5e4b3c', [['#544234', 12], ['#6a5646', 6]], 660 + s, function (g) { if (s) { px(g, '#7a5c3a', 1, 5, 14, 3); px(g, '#4a3624', 1, 8, 14, 1); px(g, '#8a6a46', 1, 5, 14, 1); px(g, '#4a3624', 6, 5, 1, 3); px(g, '#4a3624', 11, 5, 1, 3); } }); });
    Dm.ruin = [0, 1].map(function (s) {
      return tile('#6a6670', [['#5a5660', 10], ['#7a7680', 6], ['#2a1b20', 3]], 670 + s, function (g) {
        px(g, '#4a4650', 0, 5, T, 1); px(g, '#4a4650', 0, 10, T, 1); px(g, '#4a4650', 5, 0, 1, 5); px(g, '#4a4650', 11, 5, 1, 5); px(g, '#4a4650', 3, 10, 1, 6);
        px(g, '#8a8690', 0, 0, T, 1); px(g, '#1a1418', 9, 1, 3, 2); px(g, '#c8381a', 13, 12, 1, 2);
        if (s) { px(g, '#3a2e34', 0, 13, T, 3); px(g, '#241c20', 0, 15, T, 1); }
      });
    });
    Dm.plank = tile('#7a5a34', [['#6a4c2a', 10], ['#8a6a40', 5]], 680, function (g) { for (var i = 0; i < 4; i++) px(g, '#4a3418', 0, i * 4 + 3, T, 1); px(g, '#3a2a14', 1, 0, 1, T); px(g, '#3a2a14', 14, 0, 1, T); });
    Dm.brazier = [0, 1].map(function (f) {
      return over(Dm.camp[0], function (g) {
        px(g, 'rgba(0,0,0,.3)', 4, 14, 8, 2); px(g, '#2a2228', 7, 9, 2, 6); px(g, '#2a2228', 5, 14, 6, 1);
        px(g, '#3a3036', 3, 7, 10, 3); px(g, '#4a4046', 3, 7, 10, 1); px(g, '#1a1418', 4, 9, 8, 1);
        px(g, '#c8381a', 4, f ? 3 : 4, 8, 4); px(g, '#ff7a2a', 5, f ? 2 : 3, 6, 4); px(g, '#ffb04a', 6, f ? 3 : 2, 4, 3); px(g, '#ffe08a', 7, f ? 3 : 4, 2, 2);
        px(g, '#ff7a2a', f ? 4 : 11, f ? 1 : 2, 1, 1); px(g, '#ffb04a', f ? 10 : 6, 0, 1, 1);
      });
    });
    // ---- [2026-11-17] 혼돈스러운 마계(테마 4) ----
    var Ch = tiles.chaos = {};
    Ch.space = [];
    for (var cs = 0; cs < 5; cs++) for (var cf = 0; cf < 2; cf++) (function (s, f) {
      var c = mk(T, T), g = c.getContext('2d'), r = rng(900 + s);
      g.fillStyle = '#05030a'; g.fillRect(0, 0, T, T);
      for (var i = 0; i < 3; i++) { var sx = Math.floor(r() * T), sy = Math.floor(r() * T), b = r(); px(g, b < 0.3 ? '#c8b8ff' : b < 0.55 ? '#ffd8e8' : '#ffffff', sx, sy); if (f && i === 0) { px(g, 'rgba(255,255,255,.5)', sx - 1, sy); px(g, 'rgba(255,255,255,.5)', sx + 1, sy); px(g, 'rgba(255,255,255,.5)', sx, sy - 1); px(g, 'rgba(255,255,255,.5)', sx, sy + 1); } }
      Ch.space[s * 2 + f] = c;
    })(cs, cf);
    Ch.rock = [0, 1, 2].map(function (s) { return tile('#2a2230', [['#221a28', 12], ['#342a3c', 7], ['#3e2a4a', 3]], 910 + s, function (g) { if (s === 2) { px(g, '#7a3ac8', 4, 9, 3, 1); px(g, '#b06aff', 5, 9); } if (s === 1) { px(g, '#1a1220', 9, 3, 4, 1); px(g, '#1a1220', 12, 4, 1, 3); } }); });
    Ch.vein = [0, 1].map(function (f) { return over(Ch.rock[0], function (g) { px(g, '#3a0a3a', 2, 4, 1, 5); px(g, '#3a0a3a', 3, 9, 6, 1); px(g, '#3a0a3a', 9, 9, 1, 4); px(g, '#3a0a3a', 10, 3, 4, 1); px(g, f ? '#ff4ad8' : '#b02aa8', 3, 9, 6, 1); px(g, f ? '#b02aa8' : '#ff4ad8', 10, 3, 4, 1); px(g, '#ffc8f8', f ? 5 : 11, f ? 9 : 3); }); });
    Ch.under = [0, 1].map(function (s) { // 바위섬 밑면 -- 위는 바위, 아래로 갈수록 뾰족하게 우주로 사라진다
      var c = mk(T, T), g = c.getContext('2d'); g.fillStyle = '#05030a'; g.fillRect(0, 0, T, T);
      var cols = [7, 10, 5, 12, 8, 11, 6, 9, 13, 7, 10, 5, 12, 8, 11, 6];
      for (var x = 0; x < T; x++) { var d = cols[(x + s * 5) % T]; px(g, '#1a1220', x, 0, 1, d); px(g, '#120c18', x, d - 2, 1, 2); if (x % 5 === 2) px(g, '#5a2a8a', x, d - 1, 1, 1); }
      px(g, '#3e2a4a', 0, 0, T, 1);
      return c;
    });
    Ch.gate = [0, 1].map(function (f) { return over(Ch.rock[1], function (g) { for (var i = 0; i < 6; i++) { var a = i / 6 * Math.PI * 2 + (f ? 0.5 : 0); px(g, i % 2 ? '#7af0ff' : '#ffe9a8', 8 + Math.round(Math.cos(a) * 5) - 1, 8 + Math.round(Math.sin(a) * 5) - 1, 2, 2); } px(g, '#c8f8ff', 6, 6, 4, 4); px(g, '#ffffff', 7, 7, 2, 2); }); });
    Ch.gateShut = over(Ch.rock[1], function (g) { for (var i = 0; i < 6; i++) { var a = i / 6 * Math.PI * 2; px(g, '#3a3044', 8 + Math.round(Math.cos(a) * 5) - 1, 8 + Math.round(Math.sin(a) * 5) - 1, 2, 2); } px(g, '#1a1220', 6, 6, 4, 4); px(g, '#6a6070', 4, 7, 8, 1); px(g, '#6a6070', 7, 4, 1, 8); });
    // ---- [2026-11-17] 마계 마을(테마 5) -- 흑요석 판석(붉은 줄눈), 잿빛 땅, 뼈 다리, 뿔 달린 석상, 보랏빛 화톳불, 뾰족 지붕 집 ----
    var Vd = tiles.dv = {};
    Vd.pave = [0, 1].map(function (s) { return tile('#1e1724', [['#18121e', 10], ['#261d2e', 6]], 950 + s, function (g) { px(g, '#4a0e16', 0, 7, T, 1); px(g, '#4a0e16', 0, 15, T, 1); px(g, '#4a0e16', 7, 0, 1, 7); px(g, '#4a0e16', 15, 8, 1, 7); px(g, '#3a2e46', 0, 0, T, 1); px(g, '#3a2e46', 0, 8, T, 1); if (s) { px(g, '#c8381a', 3, 7, 3, 1); px(g, '#ff7a2a', 4, 7); px(g, '#8a2ac8', 10, 11, 2, 2); } }); });
    Vd.ground = [0, 1, 2].map(function (s) { return tile('#2a2026', [['#221a20', 12], ['#33282e', 6], ['#1a1418', 3]], 960 + s); });
    Vd.crack = [0, 1].map(function (f) { return over(Vd.ground[0], function (g) { px(g, '#120a0c', 3, 4, 1, 5); px(g, '#120a0c', 4, 9, 6, 1); px(g, '#120a0c', 10, 6, 1, 4); px(g, f ? '#ff7a2a' : '#c8381a', 4, 9, 6, 1); px(g, '#ffb04a', f ? 6 : 8, 9); }); });
    Vd.bridge = (function () { var c = mk(T, T), g = c.getContext('2d'); g.drawImage(tiles.dem.lava[0], 0, 0); for (var i = 0; i < 4; i++) { px(g, '#d8ccb0', 0, i * 4, T, 3); px(g, '#a89878', 0, i * 4 + 2, T, 1); px(g, '#3a2a20', 0, i * 4 + 3, T, 1); } px(g, '#efe4cc', 1, 0, 2, T); px(g, '#efe4cc', 13, 0, 2, T); px(g, '#8a7a60', 3, 0, 1, T); px(g, '#8a7a60', 12, 0, 1, T); return c; })();
    Vd.statue = over(Vd.pave[0], function (g) { // 뿔 달린 악마 석상(받침대 위)
      px(g, 'rgba(0,0,0,.35)', 2, 14, 12, 2); px(g, '#3a3044', 3, 12, 10, 3); px(g, '#4e4458', 3, 12, 10, 1);
      px(g, '#2a2232', 6, 5, 4, 7); px(g, '#2a2232', 5, 7, 6, 3); px(g, '#2a2232', 4, 6, 1, 4); px(g, '#2a2232', 11, 6, 1, 4); px(g, '#2a2232', 6, 2, 4, 3);
      px(g, '#2a2232', 5, 1, 1, 2); px(g, '#2a2232', 4, 0, 1, 2); px(g, '#2a2232', 10, 1, 1, 2); px(g, '#2a2232', 11, 0, 1, 2); px(g, '#433a4e', 6, 2, 1, 9);
      px(g, '#ff2a2a', 7, 3); px(g, '#ff2a2a', 9, 3);
      px(g, '#1a141e', 2, 4, 2, 4); px(g, '#1a141e', 12, 4, 2, 4); px(g, '#1a141e', 1, 3, 1, 2); px(g, '#1a141e', 14, 3, 1, 2); // 접힌 날개
    });
    Vd.brazier = [0, 1].map(function (f) {
      return over(Vd.pave[0], function (g) {
        px(g, 'rgba(0,0,0,.3)', 4, 14, 8, 2); px(g, '#2a2228', 7, 9, 2, 6); px(g, '#2a2228', 5, 14, 6, 1); px(g, '#3a3036', 3, 7, 10, 3); px(g, '#d8ccb0', 3, 7, 1, 1); px(g, '#d8ccb0', 12, 7, 1, 1);
        px(g, '#5a1a9a', 4, f ? 3 : 4, 8, 4); px(g, '#9a3aff', 5, f ? 2 : 3, 6, 4); px(g, '#d08aff', 6, f ? 3 : 2, 4, 3); px(g, '#ffffff', 7, f ? 3 : 4, 2, 1);
        px(g, '#c86aff', f ? 4 : 11, f ? 1 : 2, 1, 1); px(g, '#c86aff', f ? 10 : 6, 0, 1, 1);
      });
    });
    Vd.roof = [0, 1].map(function (s) { return tile('#2e1a2e', [['#261426', 10], ['#3a2238', 6]], 970 + s, function (g) { for (var i = 0; i < 4; i++) { px(g, '#1a0e1a', 0, i * 4 + 3, T, 1); px(g, '#4a2a46', (i * 5 + s * 3) % 13, i * 4, 3, 1); } }); });
    Vd.ridge = [0, 1].map(function (s) { return over(Vd.roof[s], function (g) { g.clearRect(0, 0, T, 6); g.drawImage(Vd.ground[0], 0, 0, T, 6, 0, 0, T, 6); px(g, '#1a0e1a', 0, 5, T, 2); px(g, '#2e1a2e', s ? 2 : 9, 0, 3, 6); px(g, '#2e1a2e', s ? 3 : 10, 0, 1, 1); px(g, '#c8381a', s ? 3 : 10, 1); }); });
    Vd.wall = [0, 1].map(function (s) { return tile('#2a2232', [['#221a2a', 10], ['#332a3c', 5]], 980 + s, function (g) { px(g, '#120c16', 0, 0, T, 2); if (s) { px(g, '#120c16', 5, 6, 6, 10); px(g, '#3a1a0e', 6, 7, 4, 9); px(g, '#ff7a2a', 9, 11); } else { px(g, '#120c16', 4, 5, 8, 6); px(g, '#ff5a2a', 5, 6, 2, 4); px(g, '#ff5a2a', 9, 6, 2, 4); px(g, '#ffb04a', 5, 7); px(g, '#ffb04a', 9, 7); } px(g, '#120c16', 0, 15, T, 1); }); });
    tiles.npc = { smith: npcSprite('smith'), captain: npcSprite('captain'), boutique: npcSprite('boutique'), barber: npcSprite('barber') /* [2026-11-20] 미용실 */, priest: npcSprite('priest'), guide: npcSprite('guide'),
      blackmarket: blackSprite(), furrier: furrierSprite(), stall: stallSprite(), daykeeper: keeperSprite(), // [2026-10-26], [2026-10-28] 요일 동굴 문지기
      ninjaquest: npcSprite('ninjaquest'), boatwright: npcSprite('boatwright'), ferry: npcSprite('ferry'), chest: chestSprite(), antchest: chestSprite(), smelter: npcSprite('smelter'), gemcutter: npcSprite('gemcutter'), artisan: npcSprite('artisan'), archishop: npcSprite('archishop'), elementalist: npcSprite('gemcutter'), refiner: npcSprite('refiner'), primalsmith: npcSprite('gemcutter'), alchemist: npcSprite('smelter'), /* [2026-11-20] 연금술사 */ /* [2026-11-20] 태초의 보석 세공사 */ demonfolk: demonfolkSprite(), wbboard: wbBoardSprite(), mailbox: npcSprite('ferry'), /* [2026-11-17] 우체부(파란 제복) */ wallguard: keeperSprite(), swordmaster: npcSprite('artisan'), basecamp: furrierSprite(), forestguard: keeperSprite(), citymerchant: npcSprite('archishop'), mercenary: keeperSprite(), enhancer: npcSprite('smith'), armorer: npcSprite('artisan'), /* [2026-11-18] 방어구 강화사 */ whirlmaster: npcSprite('ferry'), ultmaster: npcSprite('ninjaquest'), riverferry: npcSprite('ferry'), seaferry: npcSprite('ferry'), /* [2026-11-18] 바닷가 뱃사람/해상도시 부두지기 */ seabuoy: seaBuoySprite(), /* [2026-11-18] 신호 부표 */ wpkeeper: npcSprite('guide'), /* [2026-11-18] 워프 포인트 관리인 */ guard: npcSprite('guard'), knight: npcSprite('knight'), towergate: npcSprite('guide'), tokenshop: npcSprite('archishop') }; // [2026-11-15] 강 뱃사공, 탑 관리인 // [2026-10-28] 개미굴 보물 상자 // [2026-10-28] 피라미드 보상 상자 // [2026-10-28] 다도해 NPC(목록에 없으면 그림 없이 이름표만 나왔다)
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
  /** [2026-11-20] 떠돌이 갑옷장이 -- 은둔한 대장장이처럼 두건을 썼지만 붉게 달군 쇠빛 망토 + 등에 멘 방패. */
  function armorerSprite() {
    var c = npcSprite('priest'), g = c.getContext('2d');
    px(g, '#3a1a14', 4, 1, 8, 4); px(g, '#1a0c0a', 5, 3, 6, 2); px(g, '#6a6f7a', 4, 7, 8, 5); px(g, '#9aa3b6', 5, 7, 6, 1); px(g, '#ff5a2a', 6, 4); px(g, '#ff5a2a', 9, 4); px(g, '#c8402a', 7, 9, 2, 2);
    return c;
  }
  function hermitSprite() {
    var c = npcSprite('priest'), g = c.getContext('2d');
    px(g, '#1a1626', 4, 1, 8, 4); px(g, '#0c0a12', 5, 3, 6, 2); px(g, '#24212b', 4, 7, 8, 5); px(g, '#ffb03a', 6, 4); px(g, '#ffb03a', 9, 4);
    return c;
  }
  /** [2026-11-17] 월드보스 게시판 -- 나무 기둥 둘에 걸린 판, 붉은 거룡 문장 + 붙은 쪽지들. */
  function wbBoardSprite() {
    var c = mk(T, T), g = c.getContext('2d');
    px(g, 'rgba(0,0,0,.28)', 2, 14, 12, 2);
    px(g, '#5a3a1e', 2, 3, 2, 12); px(g, '#5a3a1e', 12, 3, 2, 12); px(g, '#7a5230', 2, 3, 1, 12); px(g, '#7a5230', 12, 3, 1, 12);
    px(g, '#8a5e34', 1, 2, 14, 9); px(g, '#a8763e', 1, 2, 14, 1); px(g, '#5a3a1e', 1, 10, 14, 1); px(g, '#6a4524', 2, 3, 12, 7);
    px(g, '#f0e2c0', 3, 4, 4, 5); px(g, '#e8d8b0', 9, 4, 4, 3); px(g, '#f0e2c0', 9, 8, 3, 2); // 쪽지
    px(g, '#c8281e', 4, 5, 2, 2); px(g, '#8a1414', 3, 5, 1, 1); px(g, '#8a1414', 6, 5, 1, 1); px(g, '#c8281e', 4, 7, 2, 1); // 거룡 문장
    px(g, '#7a6a50', 10, 5, 2, 1); px(g, '#7a6a50', 10, 6, 1, 1); px(g, '#7a6a50', 10, 9, 1, 1);
    px(g, '#c8a040', 5, 4); px(g, '#c8a040', 11, 4); px(g, '#c8a040', 10, 8); // 압정
    return outline(c, '#1a0e06');
  }
  /** [2026-11-17] 마계 주민 -- 검보라 두건 망토, 작은 뿔, 빛나는 붉은 눈, 손에 보랏빛 등불. */
  function demonfolkSprite() {
    var c = mk(T, T), g = c.getContext('2d');
    px(g, '#1a0e22', 4, 4, 8, 11); px(g, '#2a1838', 4, 4, 8, 1); px(g, '#120818', 4, 14, 8, 1); px(g, '#2a1838', 5, 6, 1, 8);
    px(g, '#1a0e22', 5, 1, 6, 4); px(g, '#0a0410', 6, 3, 4, 3); px(g, '#ff2a2a', 6, 4); px(g, '#ff2a2a', 9, 4);
    px(g, '#d8ccb0', 4, 0, 1, 2); px(g, '#d8ccb0', 11, 0, 1, 2); px(g, '#a89878', 5, 1); px(g, '#a89878', 10, 1);
    px(g, '#3a3036', 12, 9, 1, 4); px(g, '#9a3aff', 12, 8, 2, 2); px(g, '#d08aff', 12, 8);
    return outline(c, '#05020a');
  }
  function npcSprite(kind) {
    var c = mk(T, T), g = c.getContext('2d'), skin = '#f2c194';
    var smith = kind === 'smith', bq = kind === 'boutique', pr = kind === 'priest', gd = kind === 'guide'; // [2026-10-25] 이동 안내원(초록 여행복 + 깃발 지팡이)
     // [2026-10-24] 명품상점 주인(보라 연미복 + 실크햇 + 콧수염), 성당 신부(검은 사제복 + 흰 깃 + 금 십자가)
    var nq = kind === 'ninjaquest', bw = kind === 'boatwright', fr = kind === 'ferry';
    var so = kind === 'smelter' || kind === 'refiner', gc = kind === 'gemcutter', ar = kind === 'artisan', ah = kind === 'archishop', rf = kind === 'refiner'; // [2026-11-17] 악마 광석 정제사 // [2026-10-28] 다도해 상인(푸른 옷) // [2026-10-28] 광석 전문가(주황 앞치마), 보석 세공사(청록), 왕도 장인(검붉은) // [2026-10-28] 다도해 -- 신궁 수문장(붉은 갑옷), 배 목수(갈색 작업복), 뱃사공(파란 옷)
    var body = kind === 'barber' ? '#e86aa0' : rf ? '#4a1a1a' : ah ? '#2a7ac8' : so ? '#c0682a' : gc ? '#2a8a8a' : ar ? '#7a2a2a' : smith ? '#7a4a1f' : bq ? '#7b3fa0' : pr ? '#24212b' : gd ? '#2f8a5a' : nq ? '#a8202a' : bw ? '#8a6a3a' : fr ? '#2a5aa8' : '#f4f4f0', bodyD = ah ? '#1a5a98' : so ? '#8a4a1a' : gc ? '#1a5e5e' : ar ? '#4e1a1a' : smith ? '#5a3a1c' : bq ? '#5a2a78' : pr ? '#141218' : gd ? '#1f6342' : nq ? '#6a1018' : bw ? '#6a4a22' : fr ? '#1a3a78' : '#c9ccd6', pants = smith ? '#3a2b22' : bq ? '#2a1d3a' : pr ? '#24212b' : gd ? '#5a4630' : nq ? '#1a1a22' : bw ? '#3a2b22' : fr ? '#22305e' : '#22305e';
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
    if (rf) { px(g, '#2a1a12', 5, 8, 6, 4); px(g, '#3a3036', 4, 3, 8, 2); px(g, '#ff9a3a', 5, 3, 2, 2); px(g, '#ff9a3a', 9, 3, 2, 2); px(g, '#ffe08a', 5, 3); px(g, '#ffe08a', 9, 3); px(g, '#2a2228', 11, 10, 4, 3); px(g, '#ff5a1a', 12, 10, 2, 1); px(g, '#ffb04a', 12, 9, 1, 1); } // [2026-11-17] 고글 + 도가니
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
    if (kind === 'guard' || kind === 'knight') { // [2026-11-16] 중무장 경비병(강철 판금 + 푸른 깃) / 기사단장·수비대장(금빛 판금 + 붉은 깃·망토) -- 투구, 창, 방패
      var kn = kind === 'knight', a1 = kn ? '#d9b44a' : '#9aa3b6', a2 = kn ? '#a8842a' : '#666f82', a3 = kn ? '#f6e3a0' : '#dfe4ee', acc = kn ? '#c8202a' : '#3557a8';
      px(g, a2, 4, 7, 8, 8); px(g, a1, 5, 7, 6, 5); px(g, a3, 6, 8, 1, 3); px(g, a2, 5, 12, 2, 3); px(g, a2, 9, 12, 2, 3); px(g, a1, 7, 12, 2, 2); px(g, '#2a2e3c', 5, 15, 2, 1); px(g, '#2a2e3c', 9, 15, 2, 1);
      if (kn) { px(g, acc, 3, 7, 1, 8); px(g, acc, 12, 7, 1, 8); px(g, '#8a1018', 3, 13, 1, 2); px(g, '#8a1018', 12, 13, 1, 2); } // 망토
      px(g, a2, 3, 1, 10, 6); px(g, a1, 4, 1, 8, 5); px(g, a3, 5, 1, 3, 1); px(g, '#12141c', 5, 3, 6, 1); px(g, a2, 7, 4, 2, 3); // 투구(눈구멍 + 콧대)
      px(g, acc, 7, 0, 2, 1); px(g, acc, 8, 0, 3, 1); // 깃
      px(g, '#6b4220', 14, 2, 1, 13); px(g, '#dfe4ee', 14, 0, 1, 2); px(g, '#dfe4ee', 13, 1, 3, 1); // 창
      px(g, a2, 0, 8, 4, 6); px(g, acc, 1, 9, 2, 4); px(g, a3, 1, 10, 2, 1); // 방패
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
  // [2026-11-15] 요새 도시 엘리시온 / 탑 입구(테마 1), 탑 안(테마 2) -- 같은 바닥 글자를 전용 그림으로 바꿔 그린다(없으면 null = 보통 그림).
  //  탑 입구의 탑 정면(Q 덩어리)은 한 장의 큰 그림(faceBuild)을 칸마다 잘라 쓴다 -- 하늘까지 솟은 돌벽, 금빛 띠, 불 켜진 아치 창, 한가운데 빛나는 큰 문.
  function themeOf() { var id = map && map.id; return !map ? 0 : id === 'towercity' || id === 'towergate' ? 1 : /^tw\d/.test(id) ? 2 : /^demon(east|west)/.test(id) || id === 'lastline' || id === 'abyssgate' ? 3 : id === 'chaos' ? 4 : id === 'demonvillage' ? 5 : 0; } // [2026-11-17] 들판 2·3, 최후의 방어선, 마계 입구 = 3, 혼돈스러운 마계 = 4 // [2026-11-16] 3 = 악마의 들판
  var faceCache = null;
  function faceInfo() {
    var key = map.id + '|' + map.w + 'x' + map.h;
    if (faceCache && faceCache.key === key) return faceCache;
    var x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (var y = 0; y < map.h; y++) { var row = map.rows[y]; for (var x = 0; x < map.w; x++) if (row.charAt(x) === 'Q') { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; } }
    faceCache = { key: key, x0: x0, y0: y0, x1: x1, y1: y1, img: x1 < 0 ? null : (map.id === 'demonvillage' ? palaceBuild : faceBuild)((x1 - x0 + 1) * T, (y1 - y0 + 1) * T), t: {} }; // [2026-11-17] 마계 마을 = 마왕성
    return faceCache;
  }
  function faceTile(x, y) {
    var f = faceInfo();
    if (!f.img || x < f.x0 || x > f.x1 || y < f.y0 || y > f.y1) return null;
    var k = x + ',' + y, c = f.t[k];
    if (!c) { c = f.t[k] = mk(T, T); c.getContext('2d').drawImage(f.img, (x - f.x0) * T, (y - f.y0) * T, T, T, 0, 0, T, T); }
    return c;
  }
  /** [2026-11-17] 마계 마을 마왕성 정면 한 장(w x h 픽셀) -- 붉은 하늘과 핏빛 달, 뿔 달린 첨탑들, 흑요석 본성과 줄지은 불 켜진 창, 핏빛 깃발, 용암이 흐르는 큰 문, 문 위 뿔 문장. */
  function palaceBuild(w, h) {
    var c = mk(w, h), g = c.getContext('2d'), r = rng(1135), cx = w >> 1, i, y, x;
    var sky = g.createLinearGradient(0, 0, 0, h); sky.addColorStop(0, '#120208'); sky.addColorStop(0.55, '#3a0a14'); sky.addColorStop(1, '#5a1414');
    g.fillStyle = sky; g.fillRect(0, 0, w, h);
    for (i = 0; i < 70; i++) { g.fillStyle = 'rgba(255,' + (120 + Math.floor(r() * 100)) + ',120,' + (0.25 + r() * 0.5) + ')'; g.fillRect(Math.floor(r() * w), Math.floor(r() * h * 0.5), 1, 1); }
    var mx = Math.round(w * 0.16), my = Math.round(h * 0.2), mr = Math.round(h * 0.09); // 핏빛 달
    var mg = g.createRadialGradient(mx, my, mr * 0.5, mx, my, mr * 3); mg.addColorStop(0, 'rgba(255,60,40,.45)'); mg.addColorStop(1, 'rgba(255,60,40,0)'); g.fillStyle = mg; g.fillRect(mx - mr * 3, my - mr * 3, mr * 6, mr * 6);
    g.fillStyle = '#d83a2a'; g.beginPath(); g.arc(mx, my, mr, 0, Math.PI * 2); g.fill(); g.fillStyle = '#ff7a5a'; g.beginPath(); g.arc(mx - mr * 0.25, my - mr * 0.2, mr * 0.55, 0, Math.PI * 2); g.fill(); g.fillStyle = '#a82418'; g.fillRect(mx + mr * 0.2, my + mr * 0.1, mr * 0.3, mr * 0.25);
    function spire(sx, top, bw, col) { g.fillStyle = col; g.beginPath(); g.moveTo(sx - bw / 2, h); g.lineTo(sx - bw / 2, top + bw * 1.6); g.lineTo(sx, top); g.lineTo(sx + bw / 2, top + bw * 1.6); g.lineTo(sx + bw / 2, h); g.closePath(); g.fill(); }
    function horns(hx, hy, s, col) { g.strokeStyle = col; g.lineWidth = Math.max(2, s * 0.35); g.beginPath(); g.moveTo(hx - s * 0.3, hy); g.quadraticCurveTo(hx - s * 1.4, hy - s * 0.2, hx - s * 1.2, hy - s * 1.6); g.stroke(); g.beginPath(); g.moveTo(hx + s * 0.3, hy); g.quadraticCurveTo(hx + s * 1.4, hy - s * 0.2, hx + s * 1.2, hy - s * 1.6); g.stroke(); }
    for (i = 0; i < 11; i++) spire(w * (0.04 + i * 0.092) + (r() - 0.5) * 20, h * (0.12 + r() * 0.22), 18 + r() * 14, '#140c18'); // 뒤쪽 첨탑 숲
    // 양옆 큰 탑
    [[0.12, 0.08], [0.88, 0.08]].forEach(function (q) {
      var tx = w * q[0], tw = w * 0.11, top = h * q[1];
      g.fillStyle = '#1c1424'; g.fillRect(tx - tw / 2, top + tw * 1.2, tw, h); g.fillStyle = '#2e2238'; g.fillRect(tx - tw / 2, top + tw * 1.2, 3, h); g.fillStyle = '#100a14'; g.fillRect(tx + tw / 2 - 3, top + tw * 1.2, 3, h);
      spire(tx, top, tw * 1.05, '#241a2e'); horns(tx, top + tw * 1.25, tw * 0.35, '#d8ccb0');
      for (y = top + tw * 1.6; y < h - 30; y += 22) for (x = -1; x <= 1; x += 2) { g.fillStyle = r() < 0.75 ? '#ff6a2a' : '#3a1a1a'; g.fillRect(Math.round(tx + x * tw * 0.22 - 2), Math.round(y), 4, 9); }
      g.fillStyle = '#ff7a2a'; g.fillRect(Math.round(tx - 2), Math.round(h * 0.55), 4, Math.round(h * 0.45)); g.fillStyle = '#ffb04a'; g.fillRect(Math.round(tx - 1), Math.round(h * 0.55), 2, Math.round(h * 0.45)); // 용암 폭포
    });
    // 본성
    var kx0 = w * 0.2, kx1 = w * 0.8, ky = h * 0.34;
    g.fillStyle = '#1a1220'; g.fillRect(kx0, ky, kx1 - kx0, h - ky);
    for (x = kx0; x < kx1; x += 26) { g.fillStyle = '#2a1e32'; g.fillRect(Math.round(x), ky, 3, h - ky); g.fillStyle = '#0e0a12'; g.fillRect(Math.round(x + 23), ky, 2, h - ky); }
    for (x = kx0; x < kx1; x += 14) { g.fillStyle = '#1a1220'; g.beginPath(); g.moveTo(x, ky); g.lineTo(x + 7, ky - 12); g.lineTo(x + 14, ky); g.fill(); } // 톱니 흉벽
    for (y = ky + 14; y < h - 24; y += 26) for (x = kx0 + 12; x < kx1 - 12; x += 26) { if (Math.abs(x + 4 - cx) < w * 0.09) continue; var lit = r() < 0.7; g.fillStyle = lit ? '#ff5a2a' : '#2a1018'; g.fillRect(Math.round(x), Math.round(y), 8, 13); if (lit) { g.fillStyle = '#ffc06a'; g.fillRect(Math.round(x + 2), Math.round(y + 3), 4, 6); } g.fillStyle = '#0e0a12'; g.fillRect(Math.round(x), Math.round(y), 8, 2); }
    // 가운데 높은 탑 + 큰 뿔
    var ctw = w * 0.16; g.fillStyle = '#20162a'; g.fillRect(cx - ctw / 2, h * 0.12, ctw, h); spire(cx, -h * 0.05, ctw * 1.1, '#2a1e36'); horns(cx, h * 0.2, ctw * 0.42, '#efe4cc');
    // 문장(문 위) -- 뿔 달린 원에 붉은 눈
    var ey = h * 0.42, er = ctw * 0.22; g.fillStyle = '#5a0a14'; g.beginPath(); g.arc(cx, ey, er, 0, Math.PI * 2); g.fill(); g.strokeStyle = '#d8b04a'; g.lineWidth = 3; g.stroke();
    horns(cx, ey - er * 0.7, er * 0.6, '#d8b04a'); g.fillStyle = '#ff2a2a'; g.beginPath(); g.ellipse(cx, ey, er * 0.55, er * 0.25, 0, 0, Math.PI * 2); g.fill(); g.fillStyle = '#1a0004'; g.fillRect(cx - 2, ey - er * 0.22, 4, er * 0.44);
    // 큰 문 -- 용암빛
    var gw = w * 0.1, gh = h * 0.36, gx = cx - gw / 2, gy = h - gh;
    var gg = g.createLinearGradient(0, gy, 0, h); gg.addColorStop(0, '#3a0a08'); gg.addColorStop(0.5, '#c8381a'); gg.addColorStop(1, '#ffb04a');
    g.fillStyle = '#d8ccb0'; g.beginPath(); g.moveTo(gx - 6, h); g.lineTo(gx - 6, gy + gw * 0.5); g.quadraticCurveTo(cx, gy - gw * 0.55, gx + gw + 6, gy + gw * 0.5); g.lineTo(gx + gw + 6, h); g.fill();
    g.fillStyle = gg; g.beginPath(); g.moveTo(gx, h); g.lineTo(gx, gy + gw * 0.5); g.quadraticCurveTo(cx, gy - gw * 0.4, gx + gw, gy + gw * 0.5); g.lineTo(gx + gw, h); g.fill();
    for (i = 0; i < 5; i++) { g.fillStyle = '#2a0a06'; g.fillRect(Math.round(gx + gw * (0.15 + i * 0.18)), Math.round(gy + gw * 0.3), 3, Math.round(gh)); } // 창살
    // 핏빛 깃발
    [0.27, 0.36, 0.64, 0.73].forEach(function (q) { var bx = w * q, bl = h * 0.42; g.fillStyle = '#5a0a14'; g.fillRect(bx - 8, ky + 6, 16, bl); g.fillStyle = '#8a1424'; g.fillRect(bx - 6, ky + 6, 4, bl); for (var k = 0; k < 8; k++) { g.fillStyle = '#5a0a14'; g.fillRect(bx - 8 + k, ky + 6 + bl + k, 1, 8 - k); g.fillRect(bx + 7 - k, ky + 6 + bl + k, 1, 8 - k); } g.fillStyle = '#d8b04a'; g.fillRect(bx - 8, ky + 6, 16, 2); g.fillRect(bx - 1, ky + 20, 2, 18); g.fillRect(bx - 5, ky + 26, 10, 2); g.beginPath(); g.arc(bx, ky + 18, 4, 0, Math.PI * 2); g.fill(); });
    // 아래 계단
    for (i = 0; i < 4; i++) { g.fillStyle = i % 2 ? '#2a2232' : '#1e1726'; g.fillRect(0, h - 10 + i * 2.5, w, 3); }
    return c;
  }
  /** 탑 정면 한 장(w x h 픽셀, 아래 가운데가 큰 문). */
  function faceBuild(w, h) {
    var c = mk(w, h), g = c.getContext('2d'), r = rng(777), cx = w >> 1, y, x, i;
    function arch(ax, ay, aw, ah) { // 꼭대기가 둥근 모양(아래는 네모)
      var hw = aw / 2;
      for (var yy = 0; yy < ah; yy++) { var t2 = yy < hw ? Math.sqrt(Math.max(0, hw * hw - (hw - yy) * (hw - yy))) : hw; g.fillRect(Math.round(ax + hw - t2), Math.round(ay + yy), Math.max(1, Math.round(t2 * 2)), 1); }
    }
    function disc(dx, dy, rad) { for (var yy = -rad; yy <= rad; yy++) { var t2 = Math.sqrt(rad * rad - yy * yy); g.fillRect(Math.round(dx - t2), dy + yy, Math.max(1, Math.round(t2 * 2)), 1); } }
    // 돌벽 -- 아래는 짙은 남색, 위로 갈수록 안개 속으로 옅어진다
    for (y = 0; y < h; y++) { var k = y / h; g.fillStyle = 'rgb(' + Math.round(156 - 104 * k) + ',' + Math.round(170 - 108 * k) + ',' + Math.round(212 - 108 * k) + ')'; g.fillRect(0, y, w, 1); }
    for (y = 0; y < h; y += 8) { g.fillStyle = 'rgba(16,20,44,' + (0.08 + 0.24 * y / h) + ')'; g.fillRect(0, y + 7, w, 1); for (x = ((y / 8) % 2) * 8; x < w; x += 16) g.fillRect(x, y, 1, 7); }
    for (i = 0; i < 260; i++) { g.fillStyle = r() < 0.5 ? 'rgba(255,255,255,.07)' : 'rgba(10,14,36,.10)'; g.fillRect(Math.floor(r() * w), Math.floor(r() * h), 2 + Math.floor(r() * 5), 1); }
    // 버팀 기둥 여덟 -- 밝은 면과 그늘
    var cols = [0.035, 0.155, 0.275, 0.395, 0.605, 0.725, 0.845, 0.965];
    cols.forEach(function (q) { var p0 = Math.round(w * q) - 10; g.fillStyle = 'rgba(196,208,242,.26)'; g.fillRect(p0, 0, 20, h); g.fillStyle = 'rgba(255,255,255,.30)'; g.fillRect(p0, 0, 3, h); g.fillStyle = 'rgba(12,16,38,.38)'; g.fillRect(p0 + 17, 0, 3, h); });
    // 금빛 띠 셋
    var bands = [0.20, 0.46, 0.72];
    bands.forEach(function (q) {
      var yy = Math.round(h * q);
      g.fillStyle = '#b8943a'; g.fillRect(0, yy, w, 7); g.fillStyle = '#f6e3a0'; g.fillRect(0, yy, w, 1); g.fillStyle = '#7a5f20'; g.fillRect(0, yy + 6, w, 1);
      for (x = 4; x < w; x += 12) { g.fillStyle = '#f6e3a0'; g.fillRect(x, yy + 2, 3, 3); g.fillStyle = '#7ad8ff'; g.fillRect(x + 1, yy + 3, 1, 1); }
      cols.forEach(function (q2) { var p0 = Math.round(w * q2) - 12; g.fillStyle = '#d9b44a'; g.fillRect(p0, yy - 3, 24, 13); g.fillStyle = '#f6e3a0'; g.fillRect(p0, yy - 3, 24, 2); g.fillStyle = '#7a5f20'; g.fillRect(p0, yy + 9, 24, 1); });
    });
    // 아치 창 -- 띠 사이마다(위로 갈수록 작게), 가운데 큰 문 위쪽은 비워 둔다
    [[0.06, 9, 20], [0.26, 11, 26], [0.52, 13, 32], [0.78, 13, 30]].forEach(function (rw, ri) {
      var yy = Math.round(h * rw[0]);
      for (i = 0; i < cols.length - 1; i++) {
        var a0 = w * cols[i], a1 = w * cols[i + 1], mid = i === 3;
        if (mid && ri >= 1) continue;
        var n = mid ? 4 : 2;
        for (var j = 0; j < n; j++) {
          var wx = Math.round(a0 + (a1 - a0) * (j + 1) / (n + 1) - rw[1] / 2), lit = (i * 3 + j + ri) % 3 !== 0;
          g.fillStyle = '#b8943a'; arch(wx - 2, yy - 2, rw[1] + 4, rw[2] + 3);
          g.fillStyle = '#161c3c'; arch(wx, yy, rw[1], rw[2]);
          g.fillStyle = lit ? (ri % 2 ? '#ffd66b' : '#8fe0ff') : '#2c3868'; arch(wx + 2, yy + 3, rw[1] - 4, rw[2] - 5);
          if (lit) { g.fillStyle = 'rgba(255,255,255,.75)'; g.fillRect(wx + 3, yy + 6, 2, rw[2] - 12); }
          g.fillStyle = '#161c3c'; g.fillRect(wx + (rw[1] >> 1), yy + 2, 1, rw[2] - 2);
        }
      }
    });
    // 발치의 단
    g.fillStyle = '#cfc7b4'; g.fillRect(0, h - 10, w, 10); g.fillStyle = '#f1ece1'; g.fillRect(0, h - 10, w, 2); g.fillStyle = '#9a927e'; g.fillRect(0, h - 5, w, 1);
    // 한가운데 큰 문 -- 겹겹의 금빛 아치 틀 + 안쪽에서 새어 나오는 푸른 빛
    var gw = 7 * T, gh = 12 * T, gx = cx - gw / 2, gy = h - gh;
    g.fillStyle = '#5a4416'; arch(gx - 16, gy - 16, gw + 32, gh + 16);
    g.fillStyle = '#b8943a'; arch(gx - 13, gy - 13, gw + 26, gh + 13);
    g.fillStyle = '#f6e3a0'; arch(gx - 10, gy - 10, gw + 20, gh + 10);
    g.fillStyle = '#d9b44a'; arch(gx - 8, gy - 8, gw + 16, gh + 8);
    g.fillStyle = '#7a5f20'; arch(gx - 3, gy - 3, gw + 6, gh + 3);
    g.fillStyle = '#0e1430'; arch(gx, gy, gw, gh);
    for (i = 0; i < 7; i++) { g.fillStyle = 'rgba(' + (70 + i * 22) + ',' + (130 + i * 16) + ',255,' + (0.16 + 0.05 * i) + ')'; arch(gx + 6 + i * 7, gy + 10 + i * 13, gw - 12 - i * 14, gh - 10 - i * 13); }
    g.fillStyle = 'rgba(255,255,255,.55)'; arch(cx - 5, gy + 110, 10, gh - 110);
    for (i = 0; i < 26; i++) { g.fillStyle = 'rgba(200,240,255,' + (0.3 + r() * 0.5) + ')'; g.fillRect(Math.round(gx + 10 + r() * (gw - 20)), Math.round(gy + 30 + r() * (gh - 40)), 1, 1 + Math.floor(r() * 3)); } // 빛 티끌
    for (i = 0; i < 9; i++) { g.fillStyle = '#f6e3a0'; g.fillRect(Math.round(gx - 6), gy + 30 + i * 18, 3, 3); g.fillRect(Math.round(gx + gw + 3), gy + 30 + i * 18, 3, 3); } // 문틀 징
    // 문 위 둥근 창(금테 + 푸른 빛 + 바큇살)
    var ry = gy - 40, rr = 26;
    g.fillStyle = '#5a4416'; disc(cx, ry, rr + 5); g.fillStyle = '#d9b44a'; disc(cx, ry, rr + 3); g.fillStyle = '#f6e3a0'; disc(cx, ry, rr); g.fillStyle = '#16204a'; disc(cx, ry, rr - 3);
    g.fillStyle = '#4f8cf0'; disc(cx, ry, rr - 7); g.fillStyle = '#9fd8ff'; disc(cx, ry, rr - 14); g.fillStyle = '#ffffff'; disc(cx, ry, 4);
    g.fillStyle = '#d9b44a';
    for (i = 0; i < 8; i++) { var an = i * Math.PI / 4; for (var q3 = 5; q3 < rr - 2; q3++) g.fillRect(Math.round(cx + Math.cos(an) * q3), Math.round(ry + Math.sin(an) * q3), 1, 1); }
    // 진홍 깃발(금 문양) 양옆
    [-1, 1].forEach(function (sg) {
      var bx = cx + sg * (gw / 2 + 44) - 11, by = gy - 26, bl = 150;
      g.fillStyle = '#5a4416'; g.fillRect(bx - 3, by - 4, 28, 4);
      g.fillStyle = '#8a2038'; g.fillRect(bx, by, 22, bl); g.fillStyle = '#a52c48'; g.fillRect(bx + 3, by, 5, bl); g.fillStyle = '#5e1226'; g.fillRect(bx + 18, by, 4, bl);
      for (var q4 = 0; q4 < 11; q4++) { g.fillStyle = '#8a2038'; g.fillRect(bx + q4, by + bl + q4, 1, 11 - q4); g.fillRect(bx + 21 - q4, by + bl + q4, 1, 11 - q4); }
      g.fillStyle = '#e8c860'; g.fillRect(bx, by, 22, 3); g.fillRect(bx, by + bl - 3, 22, 2);
      for (var q5 = 0; q5 < 6; q5++) { g.fillRect(bx + 10 - q5, by + 40 + q5, 2 + 2 * q5, 1); g.fillRect(bx + 10 - q5, by + 52 - q5, 2 + 2 * q5, 1); }
      g.fillStyle = '#fff0b0'; g.fillRect(bx + 9, by + 44, 4, 5);
      g.fillStyle = '#e8c860'; g.fillRect(bx + 10, by + 70, 2, 44); g.fillRect(bx + 5, by + 82, 12, 2);
    });
    // 꼭대기 안개/구름
    for (i = 0; i < 34; i++) {
      var ox = r() * w, oy = r() * h * 0.17, ow = 40 + r() * 90, al = 0.10 + r() * 0.16;
      for (var q6 = 0; q6 < 5; q6++) { g.fillStyle = 'rgba(244,247,255,' + al + ')'; g.fillRect(Math.round(ox - ow / 2 + q6 * 6), Math.round(oy + q6 * 2 - 5), Math.max(4, Math.round(ow - q6 * 12)), 3); }
    }
    for (y = 0; y < Math.round(h * 0.07); y++) { g.fillStyle = 'rgba(236,241,255,' + (0.5 * (1 - y / (h * 0.07))) + ')'; g.fillRect(0, y, w, 1); }
    return c;
  }
  function themeTile(th, c, x, y, v, wf) {
    var E = tiles.ely, R = tiles.twr, below;
    if (th === 1) {
      switch (c) {
        case 's': return E.marble[v % 7 === 0 ? 1 : 0];
        case ',': return E.mosaic[(x + y) % 2];
        case 'W': below = cellAt(x, y + 1); return E.wall[below === 'W' || below === '^' ? 0 : 1];
        case '.': return E.lawn[v % 5 === 0 ? 2 : v % 2];
        case 'T': return E.tree[v % 2];
        case 'g': return E.bed[v % 2];
        case 'A': return E.statue;
        case 'L': return E.tower(x, y); // [2026-11-16] 성탑
        case 'i': return E.lamp[wf ? 1 : 0];
        case 'H': return E.bridge;
        case 'K': {
          if (cellAt(x, y + 1) !== 'K') return E.house[v % 2];
          var ku = 0; while (ku < 2 && cellAt(x, y - ku - 1) === 'K') ku++;
          return ku < 2 ? E.roof[v % 9 === 0 ? 1 : 0] : E.facade[v % 4 === 0 ? 1 : 0];
        }
        case 'Q': {
          if (map.id === 'towergate') return faceTile(x, y);
          var qu = 0; while (qu < 3 && cellAt(x, y - qu - 1) === 'Q') qu++;
          return qu < 3 && cellAt(x, y + 1) === 'Q' ? E.dome[qu === 1 && x % 4 === 0 ? 1 : 0] : E.templeWall[v % 3 === 0 ? 1 : 0];
        }
        case 'G': return (map.id === 'towergate' && faceTile(x, y)) || E.gate;
      }
      return null;
    }
    if (th === 3) { // [2026-11-16] 악마의 들판
      var Dm = tiles.dem;
      switch (c) {
        case '.': return Dm.ground[v % 3];
        case ':': return Dm.crack[(wf + x + y) % 2];
        case ',': return Dm.road[v % 2];
        case '~': return Dm.lava[(wf + (x + y) % 2) % 2];
        case 'W': below = cellAt(x, y + 1); return below === 'W' || below === '^' ? Dm.rock[v % 2] : Dm.rockFace;
        case 'T': return Dm.tree;
        case 'G': return map.id === 'abyssgate' ? Dm.ground[0] : Dm.gate; // [2026-11-17] 마계 입구 포털은 위에 소용돌이(abyssDraw)
        case 's': return Dm.camp[v % 13 === 0 ? 1 : 0]; // [2026-11-17] 최후의 방어선 진지 바닥
        case 'L': return Dm.ruin[cellAt(x, y + 1) === 'L' ? 0 : 1]; // 무너진 돌 성벽
        case 'H': return Dm.plank; // 해자 위 널다리
        case 'i': return Dm.brazier[wf ? 1 : 0]; // 화톳불
      }
      return null;
    }
    if (th === 5) { // [2026-11-17] 마계 마을
      var Vt = tiles.dv;
      switch (c) {
        case ',': return Vt.pave[v % 9 === 0 ? 1 : 0];
        case '.': return Vt.ground[v % 3];
        case ':': return Vt.crack[wf ? 1 : 0];
        case '~': return tiles.dem.lava[(wf + (x + y) % 2) % 2];
        case 'H': return Vt.bridge;
        case 'A': return Vt.statue;
        case 'i': return Vt.brazier[wf ? 1 : 0];
        case 'W': below = cellAt(x, y + 1); return below === 'W' || below === '^' ? tiles.dem.rock[v % 2] : tiles.dem.rockFace;
        case 'Q': return faceTile(x, y) || Vt.pave[0];
        case 'K': {
          if (cellAt(x, y + 1) !== 'K') return Vt.wall[(x * 3 + y) % 4 === 1 ? 1 : 0];
          return cellAt(x, y - 1) !== 'K' ? Vt.ridge[x % 2] : Vt.roof[v % 2];
        }
      }
      return null;
    }
    if (th === 4) { // [2026-11-17] 혼돈스러운 마계 -- 검은 우주, 떠 있는 바위섬, 귀환의 문
      var Ch = tiles.chaos;
      switch (c) {
        case '^': return cellAt(x, y - 1) === '.' || cellAt(x, y - 1) === ':' ? Ch.under[v % 2] : Ch.space[(v % 5) * 2 + (wf ? 1 : 0)];
        case '.': return Ch.rock[v % 3];
        case ':': return Ch.vein[wf ? 1 : 0];
        case 'G': return st && st.chaosLock ? Ch.gateShut : Ch.gate[wf ? 1 : 0];
      }
      return null;
    }
    switch (c) {
      case 's': return R.floor[v % 11 === 0 ? 2 : v % 2];
      case ':': return R.crack;
      case ',': return R.carpet;
      case 'W': below = cellAt(x, y + 1); return below === 'W' || below === '^' || below === 'G' || below === 'S' ? R.wall[v % 2] : R.wallFace[x % 4 === 2 ? 1 + (wf ? 1 : 0) : 0];
      case 'L': return R.pillar;
      case 'G': return x > map.w / 2 && twLocked() ? R.doorShut : R.door; // [2026-11-16] 전투방 -- 쓰러뜨릴 때까지 닫힌 문
      case 'U': return R.up;
      case 'S': return R.down;
      case '.': return E.lawn[v % 5 === 0 ? 2 : v % 2];
    }
    return null;
  }
  function tileFor(c, x, y, wf) {
    var v = hashXY(x, y), p;
    var th = themeOf(); // [2026-11-15] 요새 도시·탑 입구·탑 안은 전용 그림
    if (th) { p = themeTile(th, c, x, y, v, wf); if (p) return p; p = null; }
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
      case 'X': { var wqx = cellAt(x - 1, y) === 'X' ? (cellAt(x - 2, y) === 'X' ? 2 : 1) : 0, wqy = cellAt(x, y - 1) === 'X' ? (cellAt(x, y - 2) === 'X' ? 2 : 1) : 0; return tiles.wp[wf][wqy * 3 + wqx]; } // [2026-11-18] 3x3 워프 포인트
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
      case 'o': return tiles.water[(wf + (x + y) % 2) % 2]; // [2026-11-18] 바다로 나가는 물가(닿으면 배를 타고 다음 맵)
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
    try { if (window.MineChar && MineChar.setHD) MineChar.setHD(charHD); } catch (e) { } // [2026-11-20] 캐릭터 그림 옵션
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
    if (grade >= 78 && grade <= 81) { // [2026-11-18] 어둠의 심장 / 심판의 증표 / 정제된 마석 / 물의 심장(시안 승인)
      var R8 = function (x, y, w, h, cc) { px(g, cc, x, y, w || 1, h || 1); };
      var heart8 = function (a, b) { R8(4, 4, 3, 2, a); R8(9, 4, 3, 2, a); R8(3, 5, 10, 4, a); R8(4, 9, 8, 2, a); R8(5, 11, 6, 1, a); R8(6, 12, 4, 1, a); R8(7, 13, 2, 1, a); R8(5, 5, 2, 1, b); };
      if (grade === 81) { heart8('#2a7ad8', '#9ad8ff'); R8(7, 6, 2, 1, '#e8f8ff'); R8(6, 7, 4, 3, '#bfe8ff'); R8(7, 10, 2, 1, '#bfe8ff'); R8(7, 7, 1, 1, '#ffffff'); R8(1, 3, 1, 1, '#9ad8ff'); R8(14, 2, 1, 1, '#9ad8ff'); R8(13, 12, 1, 1, '#9ad8ff'); }
      else if (grade === 78) { heart8('#3a1a5a', '#7a4aa8'); R8(8, 5, 1, 2, '#c88aff'); R8(7, 7, 1, 2, '#c88aff'); R8(8, 9, 1, 2, '#c88aff'); R8(3, 1, 1, 2, '#6a3a9a'); R8(12, 0, 1, 2, '#6a3a9a'); R8(7, 1, 1, 1, '#9a6ad8'); }
      else if (grade === 79) {
        R8(5, 1, 6, 1, '#a8802a'); R8(3, 2, 10, 2, '#a8802a'); R8(2, 4, 12, 8, '#a8802a'); R8(3, 12, 10, 2, '#a8802a'); R8(5, 14, 6, 1, '#a8802a');
        R8(5, 2, 6, 1, '#e8c050'); R8(4, 3, 8, 1, '#e8c050'); R8(3, 4, 10, 8, '#e8c050'); R8(4, 12, 8, 1, '#e8c050'); R8(5, 13, 6, 1, '#e8c050');
        R8(7, 4, 2, 7, '#7a5a1a'); R8(4, 5, 8, 1, '#7a5a1a'); R8(6, 11, 4, 1, '#7a5a1a'); R8(3, 6, 1, 2, '#7a5a1a'); R8(5, 6, 1, 2, '#7a5a1a'); R8(3, 8, 3, 1, '#7a5a1a'); R8(10, 6, 1, 2, '#7a5a1a'); R8(12, 6, 1, 2, '#7a5a1a'); R8(10, 8, 3, 1, '#7a5a1a'); R8(4, 4, 1, 1, '#fff4c0');
      } else {
        R8(7, 1, 2, 1, '#ff8ae8'); R8(5, 2, 6, 2, '#c83ab8'); R8(4, 4, 8, 6, '#8a1a8a'); R8(5, 10, 6, 2, '#6a1270'); R8(7, 12, 2, 2, '#5a1060');
        R8(5, 4, 3, 6, '#c83ab8'); R8(6, 3, 1, 1, '#ffd0f8'); R8(7, 6, 2, 2, '#ffd0f8'); R8(8, 7, 1, 1, '#ffffff'); R8(2, 6, 1, 1, '#ff8ae8'); R8(13, 4, 1, 1, '#ff8ae8'); R8(12, 12, 1, 1, '#ff8ae8');
      }
      outline(c, '#0a0a14'); oreIcons[grade] = c.toDataURL(); return oreIcons[grade];
    }
    if (grade === 77) { // [2026-11-17] 빛의 심장 -- 금빛 심장에서 뻗는 빛살
      [[1, 7, 2, 1], [13, 7, 2, 1], [7, 0, 2, 2], [3, 2, 1, 1], [12, 2, 1, 1], [3, 13, 1, 1], [12, 13, 1, 1]].forEach(function (r) { px(g, '#fff6c0', r[0], r[1], r[2], r[3]); });
      px(g, '#e8b030', 4, 4, 3, 2); px(g, '#e8b030', 9, 4, 3, 2); px(g, '#e8b030', 3, 5, 10, 4); px(g, '#e8b030', 4, 9, 8, 2); px(g, '#e8b030', 5, 11, 6, 1); px(g, '#e8b030', 6, 12, 4, 1); px(g, '#e8b030', 7, 13, 2, 1);
      px(g, '#fff0a0', 5, 5, 3, 3); px(g, '#ffffff', 5, 5, 1, 1); px(g, '#ffe060', 8, 7, 3, 3); px(g, '#c08020', 10, 10, 2, 1);
      outline(c, '#5a3a08'); oreIcons[grade] = c.toDataURL(); return oreIcons[grade];
    }
    if (grade === 73 || grade === 74) { // [2026-11-17] 황혼의 인장(지는 해 -- 주황/보라) / 여명의 인장(뜨는 해 -- 금/하늘) -- 둥근 밀랍 인장에 반쪽 해
      var dusk = grade === 73, rim = dusk ? '#7a2a3a' : '#2a5a9a', wax = dusk ? '#b8402a' : '#d8a83a', sun = dusk ? '#ffb04a' : '#fff0a0', sky = dusk ? '#6a2a7a' : '#7ac8ff';
      [[5, 1, 6, 1], [3, 2, 10, 2], [2, 4, 12, 8], [3, 12, 10, 2], [5, 14, 6, 1]].forEach(function (r) { px(g, rim, r[0], r[1], r[2], r[3]); });
      [[5, 2, 6, 1], [4, 3, 8, 1], [3, 4, 10, 8], [4, 12, 8, 1], [5, 13, 6, 1]].forEach(function (r) { px(g, wax, r[0], r[1], r[2], r[3]); });
      px(g, sky, 4, 5, 8, 4); px(g, sun, 6, 7, 4, 2); px(g, sun, 7, 6, 2, 1); px(g, '#ffffff', 7, 7); px(g, rim, 4, 9, 8, 1);
      for (var i = 0; i < 3; i++) px(g, sun, dusk ? 5 + i * 3 : 4 + i * 3, dusk ? 10 + (i % 2) : 4 - (i % 2), 1, 1);
      outline(c, '#120a0a'); oreIcons[grade] = c.toDataURL(); return oreIcons[grade];
    }
    if (grade === 75 || grade === 76) { // [2026-11-17] 악마의 광석 조각(검붉은 결정에 검은 맥) / 종말의 파편(검은 조각에 보랏빛 금)
      var dm = grade === 75, b1 = dm ? '#8a1a2a' : '#1a0a24', b2 = dm ? '#c8382a' : '#2a1438', gl = dm ? '#ff7a5a' : '#c86aff', vn = dm ? '#2a0a10' : '#ff4ad8';
      [[7, 1, 2, 2], [6, 3, 4, 2], [5, 5, 6, 3], [4, 8, 8, 3], [5, 11, 6, 2], [6, 13, 4, 2]].forEach(function (r) { px(g, b1, r[0], r[1], r[2], r[3]); });
      px(g, b2, 7, 3, 2, 9); px(g, b2, 6, 6, 1, 4); px(g, gl, 7, 2, 1, 2); px(g, gl, 8, 5, 1, 1);
      px(g, vn, 5, 9, 3, 1); px(g, vn, 8, 10, 1, 3); px(g, vn, 9, 7, 2, 1); px(g, vn, 6, 12, 2, 1);
      if (!dm) { px(g, '#ffc8f8', 8, 10); px(g, '#ffc8f8', 10, 7); }
      outline(c, dm ? '#1a0408' : '#000000'); oreIcons[grade] = c.toDataURL(); return oreIcons[grade];
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
  var BOSS_ART = { '심판자 보로네오': 'boroneo', '심연의 거룡': 'dragon', '파라오 미라': 'pharaoh', '고대 광석 골렘': 'golem', '타락한 대주교': 'archbishop', '얼음 여왕': 'icequeen', '닌자 두목': 'ninjaboss', '닌자 두목의 분신': 'ninjaboss', '용암 거신': 'lavagiant', '여왕개미': 'antqueen', '천둥 황소': 'thunderbull', '하피 여왕': 'harpy',
    '하수구의 지배자 라텔': 'ratel', '폐광의 검은 갱도왕': 'minelord', '늪지 여왕 히드라': 'hydra', '화산의 심장 이프리트': 'ifrit', '빙하의 폭군 프로스트자이언트': 'frostgiant' }; // [2026-11-19] "탑 수문장들 이름에 맞게 이미지"(시안 승인)
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
      dragon: function (pt, pf, f, pose) { // [2026-11-15] 심연의 거룡 -- 어둠 속성 월드보스. 정면을 보고 앉은 용: 박쥐 날개(프레임마다 위/아래), 뒤로 휜 뿔 둘, 보랏빛 비늘과 밝은 배판, 빛나는 자홍 눈, 말린 꼬리, 심연의 불씨
        var b0 = '#3a2458', b1 = '#22123a', b2 = '#6a4a9a', m0 = '#5a2a7a', m1 = '#7a3aa8', bl = '#b88ae0', bl2 = '#8a5ab8', hn = '#e8dcc0', hn2 = '#a89870';
        var up = f ? 0 : 1; // 프레임 0 = 날개 위, 1 = 날개 아래
        var wing = function (dir) {
          var S = [24 + dir * 6, 22], T = up ? [[24 + dir * 11, 3], [24 + dir * 18, 5], [24 + dir * 23, 13]] : [[24 + dir * 13, 10], [24 + dir * 20, 15], [24 + dir * 23, 24]];
          for (var k = 0; k < 2; k++) for (var i = 0; i <= 14; i++) { // 날개막 -- 어깨에서 손가락 끝 사이(안으로 휜 가장자리)까지
            var q = i / 14, x = T[k][0] + (T[k + 1][0] - T[k][0]) * q, y = T[k][1] + (T[k + 1][1] - T[k][1]) * q + Math.sin(q * Math.PI) * 2.5;
            pt.L(S[0], S[1], x, y, i % 5 === 2 ? m1 : m0, 2);
          }
          for (var j = 0; j <= 8; j++) { var q2 = j / 8; pt.L(S[0], S[1] + 2, T[2][0] + (S[0] + dir * 3 - T[2][0]) * q2, T[2][1] + (S[1] + 9 - T[2][1]) * q2 - Math.sin(q2 * Math.PI) * 2, m0, 2); } // 몸통 쪽 아래 막
          T.forEach(function (c) { pt.L(S[0], S[1], c[0], c[1], b1, 2); pt.P(c[0], c[1] - 1, hn); pt.P(c[0] + dir, c[1], hn); }); // 손가락 뼈 + 끝 발톱
          pt.E(S[0] + dir, S[1], 2, 2, b0);
        };
        wing(-1); wing(1);
        // 말린 꼬리(끝은 마름모 가시)
        pt.L(27, 38, 35, 42, b0, 3); pt.L(35, 42, 42, 40, b0, 3); pt.L(42, 40, 44, 35 + (f ? 1 : 0), b0, 2); pt.D(44, 33 + (f ? 1 : 0), 2, b2, bl);
        // 몸 + 배판
        pt.E(24, 31, 8, 10, b0); pt.E(19, 27, 2, 4, b2);
        pt.E(24, 32, 4, 8, bl2); for (var by = 25; by < 40; by += 3) pt.R(21, by, 7, 1, bl);
        // 뒷발 + 발톱
        pt.E(16, 40, 4, 3, b0); pt.E(32, 40, 4, 3, b0); pt.E(15, 39, 2, 1, b2); pt.E(31, 39, 2, 1, b2);
        [13, 16, 19, 29, 32, 35].forEach(function (x) { pt.P(x, 43, hn); });
        // 가슴 앞에 모은 앞발
        pt.R(18, 33, 3, 5, b0); pt.R(27, 33, 3, 5, b0); pt.P(18, 38, hn); pt.P(20, 38, hn); pt.P(27, 38, hn); pt.P(29, 38, hn);
        // 목
        pt.R(21, 15, 7, 8, b0); pt.R(23, 17, 3, 6, bl2);
        // 뒤로 휜 뿔
        pt.L(20, 9, 16, 3, hn, 2); pt.L(16, 3, 14, 1, hn2); pt.L(28, 9, 32, 3, hn, 2); pt.L(32, 3, 34, 1, hn2);
        // 머리 + 주둥이
        pt.E(24, 11, 6, 5, b0); pt.E(24, 8, 4, 2, b2);
        pt.E(24, 15, 4, 3, b0); pt.R(21, 16, 7, 1, b1);
        pt.P(22, 14, b1); pt.P(26, 14, b1);
        pt.P(18, 12, b2); pt.P(30, 12, b2); pt.P(17, 13, b0); pt.P(31, 13, b0);
        pt.R(21, 17, 1, 2, hn); pt.R(27, 17, 1, 2, hn);
        // 빛나는 눈(어둠 속성) + 심연의 불씨
        pf.R(20, 10, 2, 2, '#ff5ad8'); pf.R(27, 10, 2, 2, '#ff5ad8'); pf.P(20, 10, '#ffffff'); pf.P(27, 10, '#ffffff');
        var ws = f ? [[8, 41], [40, 28], [11, 31], [37, 45]] : [[6, 37], [42, 31], [9, 28], [39, 44]];
        ws.forEach(function (c, i) { pf.P(c[0], c[1], '#c88aff'); pf.P(c[0], c[1] - 1, i % 2 ? '#8a4ad8' : '#e8c8ff'); });
        if (f) { pf.P(24, 19, '#f0c0ff'); pf.P(23, 20, '#b06aff'); pf.P(25, 20, '#b06aff'); pf.P(24, 21, '#8a4ad8'); pf.P(24, 22, '#6a2ab8'); } // 입가에 맺히는 어둠 숨결
      },
      boroneo: function (pt, pf, f, pose) { // [2026-11-17] 심판자 보로네오 -- 빛 속성 타락천사. 은빛 중갑옷 + 금 장식, 흰 날개(끝이 검게 물든), 왕관 투구와 금 간 후광, 검/망치를 번갈아(pose = 망치)
        var ar = '#c8ccd8', ar2 = '#8a90a4', ar3 = '#eef2f8', ad = '#5a6074', gd = '#e8c050', gd2 = '#a8802a', wg = '#f4f0e4', wg2 = '#c0b8a8', wgd = '#2e2240', k = '#140d0a';
        var up = f ? 1 : 0;
        [-1, 1].forEach(function (dir) { // 날개 -- 어깨에서 위·바깥으로 펼친 깃털 부채(끝이 검게 물든 타락천사), 위 가장자리는 금빛 뼈대
          var sx0 = 24 + dir * 6, sy0 = 19;
          for (var i = 0; i < 9; i++) {
            var ang = (-95 + i * 13) * Math.PI / 180 + (up ? -0.1 : 0.05), len = 20 - Math.abs(i - 2) * 1.3;
            var ex = sx0 + dir * Math.cos(ang) * len, ey = sy0 + Math.sin(ang) * len;
            pt.L(sx0, sy0, ex, ey, i % 2 ? wg : wg2, 2);
            pt.L(sx0 + (ex - sx0) * 0.75, sy0 + (ey - sy0) * 0.75, ex, ey, wgd, 2);
          }
          var tx = sx0 + dir * Math.cos(-95 * Math.PI / 180 + (up ? -0.1 : 0.05)) * 18, ty2 = sy0 + Math.sin(-95 * Math.PI / 180 + (up ? -0.1 : 0.05)) * 18;
          pt.L(sx0, sy0 - 1, tx, ty2, gd, 1); pf.P(tx, ty2 - 1, '#fff6c0');
        });
        pt.R(18, 36, 5, 8, ar2); pt.R(25, 36, 5, 8, ar2); pt.R(18, 36, 5, 2, ar); pt.R(25, 36, 5, 2, ar); pt.R(17, 43, 7, 2, ad); pt.R(24, 43, 7, 2, ad); // 다리 갑주
        pt.E(24, 28, 9, 10, ar2); pt.E(24, 27, 7, 8, ar); pt.R(18, 21, 12, 2, gd); pt.R(23, 23, 2, 11, gd2); pt.E(24, 27, 3, 3, ar3); pf.P(24, 27, '#fff6c0'); // 흉갑
        for (var sx = 16; sx < 32; sx += 3) { pt.R(sx, 34, 2, 4, ar2); pt.P(sx, 34, ar3); } // 허리 갑판
        pt.E(14, 22, 5, 4, ar); pt.E(34, 22, 5, 4, ar); pt.R(10, 20, 9, 1, gd); pt.R(30, 20, 9, 1, gd); pt.P(12, 21, ar3); pt.P(32, 21, ar3); // 어깨
        pt.R(11, 24, 4, 8, ar2); pt.R(33, 24, 4, 8, ar2); pt.R(11, 31, 4, 3, ar); // 팔
        pt.E(24, 13, 5, 6, ar); pt.R(19, 13, 11, 1, k); pt.R(23, 9, 2, 9, ar3); pf.R(20, 13, 2, 1, '#ffe060'); pf.R(27, 13, 2, 1, '#ffe060'); // 투구(눈은 금빛)
        pt.R(19, 6, 11, 2, gd); for (var c = 0; c < 4; c++) pt.R(19 + c * 3, 4, 2, 2, gd); pt.P(24, 3, '#ff5a5a'); // 왕관
        for (var a = 0; a < 22; a++) { if (a === 5 || a === 6) continue; var ang = a / 22 * Math.PI * 2; pf.P(24 + Math.cos(ang) * 8, 1.6 + Math.sin(ang) * 1.4, f ? '#fff6c0' : '#ffd860'); } // 금 간 후광
        if (!pose) { // 빛나는 대검(오른손을 들어)
          pt.R(34, 29, 4, 3, ar); pt.R(32, 28, 8, 2, gd);
          pt.L(37, 28, 45, 3, '#e8eeff', 3); pf.L(38, 27, 46, 2, '#ffffff'); pf.L(36, 29, 47, 0, 'rgba(255,248,200,.55)');
        } else { // 큰 망치
          pt.R(34, 29, 4, 3, ar);
          pt.L(36, 30, 41, 9, '#6a4a2a', 2); pt.L(37, 30, 42, 9, '#4a3018', 1);
          pt.R(36, 2, 12, 9, ar2); pt.R(36, 2, 12, 2, ar3); pt.R(36, 10, 12, 1, ad); pt.R(40, 4, 4, 5, gd); pf.P(42, 6, '#fff6c0');
        }
      },
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
      },
      ratel: function (pt, pf, f) { // [2026-11-19] 하수구의 지배자 라텔 -- 탑 9층 수문장. 검은 몸에 머리부터 어깨로 흐르는 은회색 망토 털(벌꿀오소리), 녹슨 쇠 왕관, 긴 갈고리 발톱, 발밑 하수구 오물(초록 거품)
        var k0 = '#2a2a30', k1 = '#16161a', k2 = '#3c3c46', s0 = '#d8dce4', s1 = '#a8aeb8', cl = '#ece2bc', cl2 = '#b0a47a', rs = '#b8743a', rs2 = '#7a4a22', rs3 = '#e0a060', u = f ? 1 : 0;
        pt.E(24, 44, 21, 3, '#2e4a22'); pt.E(24, 43, 17, 2, '#4a7a30'); pt.E(10, 44, 3, 1, '#6a9a3a'); pt.E(38, 45, 4, 1, '#6a9a3a'); // 오물 웅덩이
        pt.E(24, 32, 15, 11, k0); pt.E(24, 34, 9, 7, k2); // 몸 + 배
        pt.E(24, 23, 16, 5, s1); pt.E(24, 22, 14, 4, s0); // 어깨를 덮은 은회색 털
        pt.R(10, 22, 3, 12, s1); pt.R(36, 22, 3, 12, s1); pt.R(11, 22, 1, 10, s0); pt.R(37, 22, 1, 10, s0); // 옆구리로 흐르는 털
        pt.R(12, 35, 7, 9, k0); pt.R(13, 35, 2, 7, k2); // 왼 앞발(바닥)
        if (u) { pt.R(30, 27, 7, 8, k0); pt.R(31, 27, 2, 6, k2); [31, 33, 35].forEach(function (x) { pt.L(x, 27, x + 1, 22, cl, 1); pt.P(x + 1, 21, cl2); }); } // 오른 앞발을 쳐든다
        else { pt.R(29, 35, 7, 9, k0); pt.R(30, 35, 2, 7, k2); [30, 32, 34].forEach(function (x) { pt.L(x, 44, x - 1, 47, cl, 1); }); }
        [13, 15, 17].forEach(function (x) { pt.L(x, 44, x - 1, 47, cl, 1); }); // 갈고리 발톱
        pt.E(24, 15, 9, 7, k0); pt.E(24, 11, 8, 4, s0); pt.R(17, 11, 15, 2, s1); // 머리(정수리부터 은회색)
        pt.E(16, 10, 2, 2, k1); pt.E(32, 10, 2, 2, k1); // 귀
        pt.E(24, 19, 4, 3, k1); pt.R(22, 17, 5, 2, '#0a0a0c'); pt.P(23, 17, '#5a5a66'); // 주둥이 + 코
        pt.P(22, 21, '#f4f0e0'); pt.P(26, 21, '#f4f0e0'); pt.P(22, 22, '#d8d0b8'); pt.P(26, 22, '#d8d0b8'); // 송곳니
        pt.R(17, 6, 15, 3, rs); pt.R(17, 8, 15, 1, rs2); [17, 23, 29].forEach(function (x, i) { pt.R(x, i === 1 ? 2 : 3, i === 1 ? 3 : 3, i === 1 ? 4 : 3, rs); pt.P(x, i === 1 ? 2 : 3, rs3); }); pt.P(19, 7, rs3); pt.P(27, 7, rs2); // 녹슨 쇠 왕관
        pf.P(24, 7, '#7aff6a'); pf.P(24, 3, '#7aff6a'); // 왕관의 초록 보석(오물빛)
        pf.R(19, 14, 2, 2, '#ff4a3a'); pf.R(28, 14, 2, 2, '#ff4a3a'); pf.P(19, 14, '#ffd0c0'); pf.P(28, 14, '#ffd0c0'); // 붉은 눈
        var dr = u ? [[12, 30], [35, 26], [20, 39]] : [[12, 27], [36, 30], [27, 40]]; // 몸에서 떨어지는 오물
        dr.forEach(function (q) { pf.P(q[0], q[1], '#9aff5a'); pf.P(q[0], q[1] + 1, '#5ac83a'); });
        var bb = u ? [[6, 42], [30, 43], [42, 42]] : [[9, 43], [19, 42], [40, 43]]; bb.forEach(function (q) { pf.P(q[0], q[1], '#c8ff8a'); pf.P(q[0] + 1, q[1] - 1, '#8ae05a'); }); // 거품
      },
      minelord: function (pt, pf, f) { // [2026-11-19] 폐광의 검은 갱도왕 -- 탑 19층 수문장. 석탄빛 갑옷(틈마다 불씨), 헤드램프 달린 광부 투구 위 금관, 잿빛 수염, 큰 곡괭이와 등불
        var a0 = '#2a2a30', a1 = '#18181c', a2 = '#4a4a58', a3 = '#6a6a7a', gd = '#e8c050', gd2 = '#a8802a', u = f ? 1 : 0;
        pt.R(14, 21, 20, 22, '#1a1a20'); for (var cx = 14; cx < 34; cx += 3) pt.R(cx, 43, 2, 1 + (cx % 2), '#1a1a20'); // 해진 망토
        pt.R(18, 35, 5, 9, a0); pt.R(26, 35, 5, 9, a0); pt.R(18, 35, 5, 1, a2); pt.R(26, 35, 5, 1, a2); pt.R(17, 43, 7, 3, a1); pt.R(25, 43, 7, 3, a1); // 다리 + 장화
        pt.E(24, 28, 10, 9, a0); pt.E(24, 27, 7, 7, a2); pt.E(24, 26, 4, 4, a3); pt.R(16, 34, 17, 2, '#5a3a1e'); pt.R(23, 34, 3, 2, gd); // 흉갑 + 허리띠
        pt.D(13, 21, 5, a0, a3); pt.D(35, 21, 5, a0, a3); pt.P(11, 19, a2); pt.P(36, 18, a2); // 석탄 덩이 어깨
        pt.R(9, 23, 5, 10, a0); pt.R(34, 23, 5, 10, a0); pt.R(9, 23, 1, 9, a2); // 팔
        pt.R(8, 33, 7, 2, '#5a3a1e'); pt.R(9, 35, 5, 6, '#c8a040'); pt.R(10, 36, 3, 4, '#3a2a10'); pf.R(10, 36, 3, 4, '#ffd27a'); pf.P(11, 37, '#ffffff'); // 왼손 등불
        pt.E(24, 19, 5, 3, '#6a6a72'); pt.R(20, 19, 9, 5, '#6a6a72'); pt.P(21, 23, '#8a8a92'); pt.P(25, 24, '#8a8a92'); pt.P(27, 23, '#8a8a92'); // 잿빛 수염
        pt.E(24, 12, 7, 6, a2); pt.R(16, 15, 17, 2, a0); pt.R(19, 16, 11, 3, '#141418'); // 광부 투구 + 챙 + 그늘진 얼굴
        pf.R(20, 17, 2, 1, '#ffb03a'); pf.R(27, 17, 2, 1, '#ffb03a'); // 불씨 눈
        pt.R(21, 9, 7, 4, gd2); pt.R(22, 10, 5, 2, '#fff2b0'); pf.R(22, 10, 5, 2, '#fffbe0'); // 헤드램프
        pt.R(18, 5, 13, 2, gd); [18, 23, 28].forEach(function (x) { pt.R(x, 2, 2, 3, gd); pt.P(x, 2, '#fff2b0'); }); pt.P(24, 5, '#1a1a1e'); pf.P(24, 3, '#ff8a2a'); // 금관 + 석탄 보석
        [[20, 27, 23, 31], [27, 25, 25, 30], [17, 30, 19, 33], [30, 29, 32, 33]].forEach(function (q) { pf.L(q[0], q[1], q[2], q[3], '#ff7a1a'); pf.P(q[2], q[3], '#ffd060'); }); // 갑옷 틈의 불씨
        var py = u ? -2 : 0; // 곡괭이(오른손, 프레임마다 들썩)
        pt.L(36, 34, 43, 9 + py, '#6a4a2a', 2); pt.L(37, 34, 44, 9 + py, '#4a3018', 1);
        pt.L(36, 6 + py, 47, 13 + py, '#8a8a98', 3); pt.L(37, 5 + py, 41, 6 + py, '#c8c8d8', 1); pt.P(47, 14 + py, '#e8e8f0'); pt.P(36, 8 + py, '#e8e8f0');
        pt.R(34, 32, 5, 4, a0);
        var sp = u ? [[6, 12], [44, 30], [30, 42]] : [[4, 16], [42, 26], [16, 44]]; sp.forEach(function (q) { pf.P(q[0], q[1], '#ffb03a'); pf.P(q[0], q[1] - 1, '#ff5a1a'); }); // 날리는 불똥
      },
      hydra: function (pt, pf, f) { // [2026-11-19] 늪지 여왕 히드라 -- 탑 29층 수문장. 늪에서 솟은 다섯 머리(가운데 여왕 머리에 금관), 초록 비늘과 연두 배, 독침 침, 연잎
        var b0 = '#3a8a5a', b1 = '#22603a', b2 = '#5ab87a', bl = '#c8d890', bl2 = '#9ab060', gd = '#e8c050', u = f ? 1 : 0;
        pt.E(24, 43, 23, 4, '#24402e'); pt.E(24, 42, 19, 3, '#3a6a4a'); pt.E(5, 44, 3, 1, '#5aa04a'); pt.E(43, 45, 3, 1, '#5aa04a'); pt.P(5, 43, '#2e6a2a'); // 늪 + 연잎
        var neck = function (x0, y0, cx1, cy1, x1, y1, r) { for (var i = 0; i <= 18; i++) { var q = i / 18, a = (1 - q) * (1 - q), b = 2 * (1 - q) * q, c = q * q, x = a * x0 + b * cx1 + c * x1, y = a * y0 + b * cy1 + c * y1; pt.E(x, y, r, r, b0); pt.P(x - r + 1, y, b2); } };
        var head = function (x, y, dir, big) { var r = big ? 3 : 2; pt.E(x, y, r + 1, r + 1, b0); pt.E(x + dir * (r + 2), y + 1, r, r - 1, b0); pt.P(x - dir * 1, y - r, b2); pt.P(x, y - r - 1, b2); pt.R(x + dir * (r + 1) - (dir < 0 ? r + 1 : 0), y + r - 1, r + 2, 1, '#7a1a2a'); pt.P(x + dir * (r + 4), y, b1); pt.P(x + dir * (r + 1), y + r, '#f4f0e0'); pt.P(x + dir * (r + 3), y + r, '#f4f0e0'); pf.P(x, y - 1, '#ffe94a'); pf.P(x + dir, y - 1, '#ffe94a'); pt.L(x + dir * (r + 5), y + r - 1, x + dir * (r + 7), y + r - 2 + u, '#e85a6a'); pf.P(x + dir * (r + 2), y + r + 1, '#b8ff5a'); pf.P(x + dir * (r + 2), y + r + 2 + u, '#7ae03a'); };
        neck(16, 32, 6, 26, 8 + u, 12, 2); neck(32, 32, 42, 26, 40 - u, 12, 2); // 뒤쪽 두 머리
        head(8 + u, 10, -1, false); head(40 - u, 10, 1, false);
        pt.E(24, 35, 14, 8, b0); pt.E(24, 37, 7, 5, bl); for (var by = 33; by < 42; by += 2) pt.R(18, by, 13, 1, bl2); // 몸 + 배
        neck(19, 31, 11, 26, 13 - u, 19, 2); neck(29, 31, 37, 26, 35 + u, 19, 2); // 앞쪽 두 머리
        head(13 - u, 17, -1, false); head(35 + u, 17, 1, false);
        neck(24, 30, 24 + (u ? 2 : -2), 18, 24, 8, 3); // 가운데 여왕 머리
        pt.E(24, 7, 5, 4, b0); pt.E(24, 11, 3, 2, b0); pt.R(20, 6, 9, 1, b2); pt.P(22, 13, '#f4f0e0'); pt.P(26, 13, '#f4f0e0');
        pf.R(21, 6, 2, 1, '#ffe94a'); pf.R(26, 6, 2, 1, '#ffe94a'); pf.P(24, 14, '#b8ff5a'); pf.P(24, 15 + u, '#7ae03a');
        pt.R(20, 1, 9, 2, gd); [20, 23, 26].forEach(function (x) { pt.R(x, 0, 2, 1, gd); }); pt.P(24, 2, '#fff2b0'); pf.P(24, 1, '#7affb0'); // 금관
        var bp = u ? [[10, 41], [36, 42], [22, 44]] : [[13, 42], [33, 41], [27, 44]]; bp.forEach(function (q) { pf.P(q[0], q[1], '#9ad8a0'); }); // 늪 거품
      },
      ifrit: function (pt, pf, f) { // [2026-11-19] 화산의 심장 이프리트 -- 탑 39층 수문장. 붉은 근육질 상반신, 아래는 불꽃 꼬리, 가슴에 녹은 심장, 검은 뿔, 타오르는 머리칼, 용암 바위 어깨, 금 팔찌
        var s0 = '#d8401a', s1 = '#9a2a10', s2 = '#ff7a3a', rk = '#3a2a24', rk2 = '#5a4038', gd = '#e8c050', u = f ? 1 : 0;
        pt.E(24, 36, 9, 5, '#e8501a'); pt.E(25 + u, 40, 6, 3, '#ff8a2a'); pt.E(26 - u, 44, 3, 2, '#ffb03a'); // 불꽃 꼬리(아래로 가늘게)
        pf.E(24, 37, 5, 2, '#ffb03a'); pf.E(25 + u, 41, 3, 1, '#ffe07a'); pf.P(27 - u, 46, '#fff6c0');
        pt.E(24, 26, 11, 9, s0); pt.E(20, 23, 4, 3, s2); pt.E(28, 23, 4, 3, s2); pt.R(23, 22, 2, 10, s1); for (var ay = 28; ay < 34; ay += 2) pt.R(20, ay, 9, 1, s1); // 몸통(가슴/배 근육)
        pt.D(12, 19, 5, rk, rk2); pt.D(36, 19, 5, rk, rk2); pf.L(10, 18, 13, 21, '#ff7a1a'); pf.L(35, 17, 38, 21, '#ff7a1a'); // 용암 바위 어깨
        pt.R(8, 22, 5, 9, s0); pt.R(35, 22, 5, 9, s0); pt.R(8, 22, 1, 8, s2); pt.R(7, 29, 7, 2, gd); pt.R(34, 29, 7, 2, gd); // 팔 + 금 팔찌
        pt.E(10, 33, 3, 3, s0); pt.E(37, 33, 3, 3, s0); pf.E(10, 34 - u, 2, 2, '#ffb03a'); pf.E(37, 34 - u, 2, 2, '#ffb03a'); pf.P(10, 31 - u, '#ffe07a'); pf.P(37, 31 - u, '#ffe07a'); // 불타는 주먹
        pt.D(24, 25, 3, '#5a1a0a'); pf.D(24, 25, 2, '#ffb03a'); pf.P(24, 25, '#fff6c0'); pf.P(23, 24, '#ffe07a'); pf.L(21, 23, 19, 21, '#ff8a2a'); pf.L(27, 23, 29, 21, '#ff8a2a'); pf.L(24, 28, 24, 31, '#ff8a2a'); // 녹은 심장 + 갈라짐
        pt.E(24, 12, 6, 6, s0); pt.R(19, 15, 11, 2, s1); pt.E(24, 17, 3, 2, s1); // 머리
        pt.L(19, 9, 15, 4, '#2a1a14', 2); pt.L(15, 4, 15, 1, '#4a3a30'); pt.L(29, 9, 33, 4, '#2a1a14', 2); pt.L(33, 4, 33, 1, '#4a3a30'); // 검은 뿔
        pf.R(20, 11, 3, 1, '#fff6c0'); pf.R(26, 11, 3, 1, '#fff6c0'); pf.P(21, 12, '#ffb03a'); pf.P(27, 12, '#ffb03a'); // 타오르는 눈
        var fl = u ? [[19, 6, 4], [22, 3, 6], [25, 2, 7], [28, 4, 5]] : [[19, 5, 5], [22, 2, 7], [26, 3, 6], [29, 6, 3]]; // 불꽃 머리칼
        fl.forEach(function (q) { for (var i = 0; i < q[2]; i++) pf.P(q[0] + (i % 2 ? 1 : 0) * (u ? 1 : -1), q[1] - i + 3, i < 2 ? '#fff6c0' : i < 4 ? '#ffb03a' : '#ff5a1a'); });
        pf.L(22, 18, 21, 21 + u, '#ffb03a'); pf.L(24, 19, 24, 22 + u, '#ff8a2a'); pf.L(26, 18, 27, 21 + u, '#ffb03a'); // 불꽃 수염
        var em = u ? [[4, 10], [43, 14], [6, 40], [41, 38], [16, 2]] : [[3, 14], [44, 9], [8, 37], [43, 42], [34, 3]]; em.forEach(function (q, i) { pf.P(q[0], q[1], i % 2 ? '#ffb03a' : '#ff6a1a'); }); // 불똥
      },
      frostgiant: function (pt, pf, f) { // [2026-11-19] 빙하의 폭군 프로스트자이언트 -- 탑 49층 수문장. 얼음빛 푸른 거인, 고드름 맺힌 흰 수염, 얼음 가시 왕관, 흰 털 망토와 가죽 허리, 얼음 몽둥이
        var s0 = '#8ad8ff', s1 = '#4a98c8', s2 = '#c8f0ff', fr = '#f0f2f8', fr2 = '#b8c0d0', le = '#8a6a4a', le2 = '#5a4030', ic = '#bfefff', ic2 = '#7ac8e8', u = f ? 1 : 0;
        pt.R(15, 36, 7, 8, s0); pt.R(27, 36, 7, 8, s0); pt.R(15, 36, 2, 7, s2); pt.R(14, 42, 9, 4, fr); pt.R(26, 42, 9, 4, fr); pt.R(14, 45, 9, 1, fr2); pt.R(26, 45, 9, 1, fr2); // 다리 + 털 장화
        pt.R(14, 31, 21, 7, le); pt.R(14, 31, 21, 2, fr); for (var lx = 15; lx < 35; lx += 4) pt.R(lx, 37, 2, 2, le2); // 가죽 허리 + 털
        pt.E(24, 24, 13, 9, s0); pt.E(19, 22, 5, 4, s2); pt.E(29, 22, 5, 4, s2); pt.R(23, 21, 2, 9, s1); pt.L(12, 18, 34, 31, le2, 2); // 몸통 + 어깨띠
        pt.E(24, 16, 16, 4, fr); pt.E(24, 17, 14, 2, fr2); pt.E(10, 18, 4, 3, fr); pt.E(38, 18, 4, 3, fr); // 흰 털 망토
        pt.R(6, 19, 6, 13, s0); pt.R(36, 19, 6, 13, s0); pt.R(6, 19, 2, 12, s2); pt.E(9, 33, 3, 3, s0); pt.E(39, 32, 3, 3, s0); // 팔 + 주먹
        var cy = u ? -2 : 0; // 얼음 몽둥이(오른손, 프레임마다 들썩)
        pt.L(39, 32, 44, 12 + cy, ic2, 3); pt.L(44, 12 + cy, 46, 3 + cy, ic, 3); pt.D(45, 7 + cy, 3, ic, '#ffffff'); pt.P(42, 8 + cy, ic); pt.P(47, 10 + cy, ic);
        pf.P(44, 5 + cy, '#ffffff'); pf.P(46, 9 + cy, '#e8ffff'); pf.L(41, 22 + cy / 2, 43, 15 + cy, 'rgba(232,255,255,.7)');
        pt.E(24, 10, 6, 6, s0); pt.R(19, 8, 11, 1, s1); pt.E(22, 7, 2, 2, s2); // 머리
        pt.E(24, 16, 6, 4, '#f4f8ff'); pt.R(19, 15, 11, 2, '#f4f8ff'); pt.R(21, 19, 7, 2, '#dfe8f8'); [20, 23, 26, 28].forEach(function (x, i) { pt.L(x, 19, x, 21 + (i % 2) + u, ic, 1); }); pt.R(22, 14, 5, 1, s1); // 고드름 맺힌 수염 + 입
        pf.R(20, 9, 2, 1, '#e8ffff'); pf.R(27, 9, 2, 1, '#e8ffff'); pf.P(20, 9, '#ffffff'); // 창백하게 빛나는 눈
        pt.R(18, 3, 13, 2, ic2); [[18, 0], [21, 1], [24, -1], [27, 1], [30, 0]].forEach(function (q) { pt.L(q[0], 3, q[0], q[1] + 1, ic, 1); pf.P(q[0], q[1] + 1, '#ffffff'); }); // 얼음 가시 왕관
        pf.L(24, 22, 24 + (u ? 3 : 2), 25, 'rgba(232,255,255,.6)'); // 서리 숨결
        var sn = u ? [[3, 8], [44, 40], [8, 44], [33, 1], [2, 30]] : [[4, 5], [45, 36], [11, 42], [36, 3], [1, 26]]; // 눈송이
        sn.forEach(function (q) { pf.P(q[0], q[1], '#ffffff'); pf.P(q[0] - 1, q[1], '#cfefff'); pf.P(q[0] + 1, q[1], '#cfefff'); pf.P(q[0], q[1] - 1, '#cfefff'); pf.P(q[0], q[1] + 1, '#cfefff'); });
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
  // [2026-11-18] 바다 위 -- 잠수(서버 S5MineMobs.diving과 같은 식: 보통 14초마다 4초, 크라켄 45초마다 5초), 잠수 앞뒤 0.6초는 흐리게
  function seaDiving(ty, id, ms) {
    if (!ty || !ty.dive) return false;
    var p = ty.boss ? 45000 : 14000, u = ty.boss ? 5000 : 4000;
    return (ms + id * 7919) % p >= p - u;
  }
  function seaAlpha(ty, id, ms) {
    var p = ty.boss ? 45000 : 14000, u = ty.boss ? 5000 : 4000, v = (ms + id * 7919) % p, toDive = p - u - v;
    if (toDive >= 0 && toDive < 600) return Math.max(0.15, toDive / 600);
    if (v < 600) return Math.max(0.15, v / 600);
    return 1;
  }
  /** 물속 -- 물결 고리 + 거품(보스는 큰 그림자). (mx, my) = 몬스터 칸 왼쪽 위(화면). */
  function seaRipple(mx, my, ty, now) {
    var TS = T * scale, bf = ty.boss ? (ty.foot || 2) : 1, cx = mx + TS * bf / 2, cy = my + TS * bf / 2 + TS * 0.2, r0 = TS * 0.4 * bf;
    if (ty.boss) { ctx.fillStyle = 'rgba(20,20,50,.32)'; ctx.beginPath(); ctx.ellipse(cx, cy, r0 * 1.2, r0 * 0.7, 0, 0, Math.PI * 2); ctx.fill(); }
    for (var i = 0; i < 2; i++) { var q = ((now / 900) + i * 0.5) % 1; ctx.strokeStyle = 'rgba(220,240,255,' + (0.55 * (1 - q)).toFixed(2) + ')'; ctx.lineWidth = 1.5 * dpr; ctx.beginPath(); ctx.ellipse(cx, cy, r0 * (0.5 + q), r0 * 0.35 * (0.5 + q), 0, 0, Math.PI * 2); ctx.stroke(); }
    for (var b = 0; b < 3; b++) { var bq = ((now / 700) + b * 0.33) % 1; ctx.fillStyle = 'rgba(230,245,255,' + (0.7 * (1 - bq)).toFixed(2) + ')'; ctx.fillRect(cx + (b - 1) * 4 * scale, cy - bq * 8 * scale, 2 * scale, 2 * scale); }
  }
  function seaPainter(g, W, H) {
    var P = function (x, y, c) { x = Math.round(x); y = Math.round(y); if (x < 0 || y < 0 || x >= W || y >= H) return; g.fillStyle = c; g.fillRect(x, y, 1, 1); };
    return { P: P, R: function (x, y, w, h, c) { g.fillStyle = c; g.fillRect(x, y, w, h); },
      E: function (cx, cy, rx, ry, c) { for (var y = -ry; y <= ry; y++) for (var x = -rx; x <= rx; x++) if ((x * x) / (rx * rx + 0.5) + (y * y) / (ry * ry + 0.5) <= 1) P(cx + x, cy + y, c); },
      C: function (cx, cy, r, c) { for (var y = -r; y <= r; y++) for (var x = -r; x <= r; x++) if (x * x + y * y <= r * r + 0.3) P(cx + x, cy + y, c); } };
  }
  /** 바다 몬스터(시안 승인) -- 문어/가오리/상어 16x16, 고래 32x32. 선인식(first)이면 눈이 빨갛다. */
  var seaSprites = {};
  function seaSprite(k, f) {
    var ty = map.mobTypes[k] || {}, key = k + ':' + f;
    if (seaSprites[key]) return seaSprites[key];
    var red = !!ty.first, c, p, x, y;
    if (ty.kind === 'whale') {
      c = mk(32, 32); p = seaPainter(c.getContext('2d'), 32, 32);
      p.E(16, 23, 15, 5, 'rgba(20,50,100,.5)'); p.E(14, 20, 11, 6, '#2a3a5a'); p.E(14, 18, 10, 5, '#3a5070'); p.E(11, 16, 4, 2, '#5a7090');
      for (x = 6; x < 22; x += 3) p.P(x, 22, '#c8d8e8');
      p.R(25, 18, 3, 3, '#2a3a5a'); p.R(27, 15 - f, 2, 4, '#2a3a5a'); p.R(28, 13 - f, 4, 2, '#3a5070'); p.R(24, 14 - f, 4, 2, '#3a5070');
      p.P(7, 18, red ? '#ff3a2a' : '#ffffff'); p.P(7, 19, '#0a0a14');
      var sp = f ? 0 : 2; p.R(10, 6 + sp, 1, 7 - sp, '#d8f0ff'); p.R(8, 5 + sp, 2, 1, '#d8f0ff'); p.R(11, 5 + sp, 2, 1, '#d8f0ff'); p.P(7, 6 + sp, '#d8f0ff'); p.P(13, 6 + sp, '#d8f0ff');
      for (x = 2; x < 30; x += 4) p.P(x + f, 25, '#e8f4ff');
    } else {
      c = mk(16, 16); p = seaPainter(c.getContext('2d'), 16, 16);
      if (ty.kind === 'octo') {
        p.E(8, 13, 7, 2, 'rgba(20,50,100,.5)');
        [[2, 12], [5, 13], [10, 13], [13, 12]].forEach(function (q, i) { var sw = (i + f) % 2; p.R(q[0], q[1] - sw, 2, 2, '#c8502a'); p.P(q[0] + (i < 2 ? -1 : 2), q[1] - 1 - sw, '#c8502a'); p.P(q[0], q[1] + 1 - sw, '#ffc0a0'); });
        p.E(8, 7, 5, 5, '#a83a1e'); p.E(8, 6, 5, 5, '#d8603a'); p.E(6, 4, 2, 2, '#f09068'); p.P(10, 3, '#a83a1e'); p.P(11, 6, '#a83a1e');
        p.R(5, 8, 2, 2, red ? '#ff3a2a' : '#ffe86a'); p.R(10, 8, 2, 2, red ? '#ff3a2a' : '#ffe86a'); p.R(5, 9, 2, 1, '#1a0a0a'); p.R(10, 9, 2, 1, '#1a0a0a');
        for (x = 1; x < 15; x += 3) p.P(x + f, 14, '#e8f4ff');
      } else if (ty.kind === 'ray') {
        var b = '#1e2e4a', b2 = '#4a6a92', fw = f ? 1 : 0;
        for (y = 0; y < 7; y++) { var w = y < 3 ? 2 + y * 2 : 12 - (y - 3) * 2; p.R(8 - Math.round(w / 2) - (y === 3 ? 1 : 0), 4 + y + (y === 3 && fw ? -1 : 0), w + (y === 3 ? 2 : 0), 1, b); }
        p.R(1, 6 - fw, 2, 1, b); p.R(13, 6 - fw, 2, 1, b); p.R(6, 5, 4, 2, b2);
        p.P(6, 6, red ? '#ff3a2a' : '#e8f0ff'); p.P(9, 6, red ? '#ff3a2a' : '#e8f0ff');
        p.R(8, 11, 1, 4, b); p.P(8 + (f ? 1 : -1), 15, b);
        p.P(2, 6, '#7a9ac8'); p.P(13, 6, '#7a9ac8'); for (x = 3; x < 13; x += 4) p.P(x, 7, '#6a8ab8');
      } else { // 상어
        p.E(8, 11, 7, 2, 'rgba(20,40,80,.55)'); p.R(1 + f, 10, 2, 1, 'rgba(20,40,80,.55)');
        for (y = 0; y < 8; y++) { var w2 = Math.round(y * 0.7) + 1; p.R(9 - w2 + (y < 3 ? 1 : 0), 3 + y, w2 + 1, 1, '#5a6a7a'); p.P(9 - w2 + (y < 3 ? 1 : 0), 3 + y, '#8a9aaa'); }
        p.R(5, 10, 8, 1, '#3a4a5a'); if (red) { p.P(11, 11, '#ff3a2a'); p.P(12, 11, '#ff6a4a'); }
        for (x = 2; x < 15; x++) if ((x + f) % 3 === 0) p.P(x, 11, '#e8f4ff');
      }
    }
    seaSprites[key] = c;
    return c;
  }
  /** 심해의 크라켄(80x80 = 5x5칸, 시안 승인) -- f = 촉수 흔들림, stun = 기절(눈이 빙글, 촉수 축). */
  function krakenSprite(f, stun) {
    var key = 'krk' + f + (stun ? 's' : '');
    if (seaSprites[key]) return seaSprites[key];
    var c = mk(80, 80), p = seaPainter(c.getContext('2d'), 80, 80);
    var sk = '#7a1e3a', sk2 = '#a8405e', sk3 = '#5a1028', sk4 = '#c8607a', su = '#f4b8c4', su2 = '#d88898';
    p.E(40, 66, 36, 10, '#1d4a86'); p.E(40, 66, 30, 7, '#16396a');
    for (var a = 0; a < 40; a++) { var an = a / 40 * Math.PI * 2; if ((a + f) % 3) p.P(40 + Math.cos(an) * 35, 66 + Math.sin(an) * 9.5, '#cfe8ff'); }
    function tent(x0, y0, x1, y1, x2, y2, r0, r1) {
      var n = 34, pts = [];
      for (var i = 0; i <= n; i++) { var t = i / n, u = 1 - t; pts.push([u * u * x0 + 2 * u * t * x1 + t * t * x2, u * u * y0 + 2 * u * t * y1 + t * t * y2, r0 + (r1 - r0) * t]); }
      pts.forEach(function (q) { p.C(q[0], q[1], Math.round(q[2]) + 1, sk3); });
      pts.forEach(function (q) { p.C(q[0], q[1], Math.round(q[2]), sk); });
      pts.forEach(function (q, i) { if (i % 2 === 0) p.P(q[0] - q[2] * 0.4, q[1] - q[2] * 0.5, sk2); });
      pts.forEach(function (q, i) { if (i % 4 === 1 && q[2] >= 1.5) { p.P(q[0] + q[2] * 0.3, q[1] + q[2] * 0.4, su); p.P(q[0] + q[2] * 0.3 + 1, q[1] + q[2] * 0.4, su2); } });
    }
    var s = stun ? 0 : (f ? 2 : -2), d = stun ? 8 : 0;
    tent(30, 60, 8, 54 + d, 4, 40 - s + d * 2, 4.5, 1); tent(50, 60, 72, 54 + d, 76, 40 + s + d * 2, 4.5, 1);
    tent(26, 56, 10, 30 - s + d * 2, 18, 14 + s + d * 3, 4, 0.8); tent(54, 56, 70, 30 + s + d * 2, 62, 14 - s + d * 3, 4, 0.8);
    tent(34, 62, 22, 72, 8, 70 + s * 0.5, 3.5, 1); tent(46, 62, 58, 72, 72, 70 - s * 0.5, 3.5, 1);
    p.E(40, 30, 16, 20, sk3); p.E(40, 30, 15, 19, sk); p.E(40, 44, 13, 8, sk); p.E(34, 20, 6, 8, sk2); p.E(32, 16, 2, 3, sk4);
    [[46, 18], [49, 27], [44, 34], [31, 32], [37, 12], [50, 38], [29, 41]].forEach(function (q) { p.E(q[0], q[1], 1, 1, sk3); });
    p.R(26, 40, 10, 2, sk3); p.R(44, 40, 10, 2, sk3);
    [[31, 44], [49, 44]].forEach(function (e) {
      if (stun) { p.E(e[0], e[1], 4, 3, '#f0e8d0'); [[-2, -1], [-1, 0], [0, 1], [1, 0], [2, -1], [-2, 1], [2, 1]].forEach(function (o) { p.P(e[0] + o[0], e[1] + o[1], '#3a1a1a'); }); }
      else { p.E(e[0], e[1], 4, 3, '#ffd84a'); p.E(e[0], e[1], 3, 2, '#ffb820'); p.R(e[0] - 3, e[1], 7, 1, '#140a0a'); p.P(e[0] - 2, e[1] - 2, '#ffffff'); }
    });
    p.E(40, 52, 4, 3, sk3); p.P(39, 52, '#e8dcc0'); p.P(40, 53, '#e8dcc0'); p.P(41, 52, '#e8dcc0');
    if (stun) [[30, 4], [40, 2], [50, 4]].forEach(function (q, i) { var cc = i === 1 ? '#fff6a0' : '#ffd84a'; p.P(q[0], q[1], cc); p.P(q[0] - 1, q[1], cc); p.P(q[0] + 1, q[1], cc); p.P(q[0], q[1] - 1, cc); p.P(q[0], q[1] + 1, cc); });
    seaSprites[key] = c;
    return c;
  }
  // [2026-11-18] 크라켄 해역 -- 화약통 / 신호 부표 / 해상도시 지원 함선(시안 승인). 상태는 위치 알림 sea(seaSt).
  var seaSt = null, seaMsgSeen = 0, krkShownWas = false;
  function krkShown() { return !!(seaSt && seaSt.kf); } // [2026-11-19] 크라켄은 싸우는 중에만 보인다
  function krkAdminSee() { return !!(st && st.adminMaps); } // [2026-11-19] "관리자는 크라켄 위치가 보이도록" -- 숨어 있을 땐 반투명
  function lhLit() { return !!(seaSt && seaSt.lit && seaSt.lit > nowSrv()); } // [2026-11-19] 등대 불(종을 칠 수 있을 때)
  function seaBarrelSprite(f) {
    var key = 'brl' + f; if (seaSprites[key]) return seaSprites[key];
    var c = mk(16, 16), p = seaPainter(c.getContext('2d'), 16, 16);
    p.E(8, 13, 6, 2, 'rgba(20,50,100,.5)'); p.E(8, 9, 5, 5, '#7a4a22'); p.R(3, 6, 10, 1, '#3a2a1a'); p.R(3, 11, 10, 1, '#3a2a1a'); p.R(4, 5, 2, 8, '#9a6232');
    p.R(7, 7, 3, 3, '#1a1a1a'); p.P(7, 8, '#e8e0c8'); p.P(9, 8, '#e8e0c8'); p.P(8, 10, '#1a1a1a');
    p.R(8, 2, 1, 3, '#5a4a3a'); p.P(9, 1 + f, '#ffb83a'); p.P(8, 1, f ? '#ff6a2a' : '#ffd84a');
    for (var x = 2; x < 15; x += 3) p.P(x + f, 13, '#e8f4ff');
    return (seaSprites[key] = c);
  }
  function seaBuoySprite() {
    var c = mk(16, 16), p = seaPainter(c.getContext('2d'), 16, 16);
    p.E(8, 14, 6, 1, 'rgba(20,50,100,.5)'); p.R(4, 8, 8, 6, '#d83a2a'); p.R(4, 10, 8, 2, '#f0f0e8'); p.R(4, 8, 2, 6, '#f06a5a');
    p.R(7, 3, 2, 5, '#3a3a40'); p.R(6, 1, 4, 3, '#5a5a60'); p.P(7, 1, '#7a7a80');
    for (var x = 2; x < 15; x += 3) p.P(x, 14, '#e8f4ff');
    return c;
  }
  // [2026-11-18] "남쪽 바닷가 바다로 나가면 자동으로 배" -- 배가 있으면(지금은 관리자만) 바닷가 물 위를 배로. 배 맵(boat)은 등대 섬(r) 위만 배에서 내린다.
  function seaBoatOk() { return !!(st && st.archi && st.archi.boat && (st.archi.boatHp | 0) > 0); } // [2026-11-18] 같은 날 일반 유저 공개(예전: 관리자만)
  function boatAt(x, y) {
    if (!map || !(map.boat || map.sail)) return false;
    var row = map.rows && map.rows[Math.round(y)], c = row ? row.charAt(Math.round(x)) : '~';
    if (map.boat) return c !== 'r' && c !== 'N';
    return c === '~' || c === 'o';
  }
  var boatWas = null, moored = null;
  function boatLandCheck(pos) { // 물 <-> 섬/모래를 오갈 때 "배에서 내렸어요"(배는 그 자리에 매어 둔다) / "배에 올랐어요"
    if (!map || !(map.boat || map.sail)) { boatWas = null; moored = null; return; }
    var ob = boatAt(pos.x, pos.y), t = performance.now();
    if (boatWas && boatWas.m === map.id && boatWas.on !== ob) {
      if (!ob) { moored = { m: map.id, x: boatWas.x, y: boatWas.y, left: P.dir === 'left' }; floats.push({ x: P.x, y: P.y - 1, text: '⚓ 배에서 내렸어요 💦', color: '#e8f6ff', t0: t }); }
      else { moored = null; floats.push({ x: P.x, y: P.y - 1, text: '⛵ 배에 올랐어요', color: '#9fe0ff', t0: t }); }
    }
    if (!boatWas || boatWas.m !== map.id) moored = null;
    boatWas = { m: map.id, on: ob, x: Math.round(pos.x), y: Math.round(pos.y) };
  }
  // [2026-11-18] 등대(시안 승인) -- (x, y) = 섬 왼쪽 위(3칸 x 2칸, 탑 칸 = NPC 칸 가운데), 탑은 위로 4칸 반. ring = 종이 울리는 중.
  function lhDraw(x, y, now, ring, lit) { // [2026-11-19] lit = 불이 켜짐(그때만 등불과 빛줄기)
    var u = T * scale / 16, R = function (c, a, b, w, h) { ctx.fillStyle = c; ctx.fillRect(x + a * u, y + b * u, w * u, h * u); };
    R('#cfe8ff', -2, 24, 52, 2); R('#4a4640', 0, 8, 48, 18); R('#6a645a', 2, 6, 44, 16); R('#8a8478', 6, 6, 36, 6); R('#5a554c', 4, 18, 12, 4); R('#5a554c', 30, 16, 14, 5);
    R('#a09a8a', 10, 7, 6, 2); R('#a09a8a', 30, 8, 8, 2);
    R('#7a4a22', 40, 14, 14, 4); R('#b07a3c', 40, 14, 14, 1); R('#4a2c14', 42, 18, 2, 6); R('#4a2c14', 51, 18, 2, 6);
    R('#d8d4cc', 14, -40, 20, 48); R('#f4f2ee', 16, -40, 14, 48); R('#b8b4ac', 30, -40, 4, 48);
    R('#c8382a', 14, -30, 20, 6); R('#e85040', 16, -30, 12, 6); R('#c8382a', 14, -14, 20, 6); R('#e85040', 16, -14, 12, 6);
    R('#3a2a1a', 21, -2, 6, 10); R('#5a4028', 22, -1, 4, 9); R('#e0b050', 25, 3, 1, 1);
    R('#2a3a5a', 22, -24, 4, 4); R('#8ac8ff', 23, -23, 2, 2);
    R('#2a2a2a', 10, -44, 28, 4); R('#4a4a4a', 10, -44, 28, 1); for (var k = 11; k < 38; k += 4) R('#2a2a2a', k, -48, 1, 4); R('#2a2a2a', 10, -49, 28, 1);
    R('#2a2a2a', 15, -58, 18, 14);
    if (lit) { ctx.fillStyle = 'rgba(255,230,120,' + (0.75 + 0.25 * Math.sin(now / 200)).toFixed(2) + ')'; ctx.fillRect(x + 17 * u, y - 56 * u, 14 * u, 10 * u); R('#fffbe0', 21, -54, 6, 6); }
    else { R('#3a4250', 17, -56, 14, 10); R('#5a6474', 18, -55, 4, 3); } // 불 꺼진 등실(어두운 유리)
    R('#b8302a', 13, -64, 22, 6); R('#d84a3a', 16, -68, 16, 4); R('#e0b050', 23, -72, 2, 4);
    if (lit) {
      ctx.save(); ctx.translate(x + 24 * u, y - 51 * u); ctx.rotate(now / 900);
      var g = ctx.createLinearGradient(0, 0, 150 * u / 2, 0); g.addColorStop(0, 'rgba(255,240,160,.5)'); g.addColorStop(1, 'rgba(255,240,160,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(75 * u, -11 * u); ctx.lineTo(75 * u, 11 * u); ctx.closePath(); ctx.fill(); ctx.restore();
    }
    var sw = ring ? Math.sin(now / 60) * 3 : 0;
    ctx.save(); ctx.translate(x + 40 * u, y - 46 * u); ctx.rotate(sw * 0.12);
    ctx.fillStyle = '#4a3a1a'; ctx.fillRect(-u, -2 * u, 2 * u, 3 * u); ctx.fillStyle = '#e0b030'; ctx.beginPath(); ctx.moveTo(-4 * u, 7 * u); ctx.quadraticCurveTo(-4 * u, 0, 0, 0); ctx.quadraticCurveTo(4 * u, 0, 4 * u, 7 * u); ctx.closePath(); ctx.fill();
    ctx.fillStyle = '#ffe680'; ctx.fillRect(-3 * u, 2 * u, u, 3 * u); ctx.fillStyle = '#8a6010'; ctx.fillRect(-5 * u, 7 * u, 10 * u, u); ctx.restore();
    R('#2a2a2a', 34, -46, 6, 1);
    if (ring) { ctx.strokeStyle = 'rgba(255,230,120,.9)'; ctx.lineWidth = 2 * dpr; for (var r = 0; r < 3; r++) { var rr = (8 + r * 7) * u * 0.6; ctx.beginPath(); ctx.arc(x + 40 * u, y - 42 * u, rr, -0.9, 0.9); ctx.stroke(); ctx.beginPath(); ctx.arc(x + 40 * u, y - 42 * u, rr, Math.PI - 0.9, Math.PI + 0.9); ctx.stroke(); } }
  }
  function lhList() { return (map && map.npcs || []).filter(function (q) { return q.kind === 'lighthouse'; }); }
  function lhNear() { // 등대 섬 위(물 아님)에서 2칸 안 등대
    if (!map || map.id !== 'opensea' || boatAt(P.x, P.y)) return null;
    var L = lhList();
    for (var i = 0; i < L.length; i++) if (Math.max(Math.abs(L[i].x - P.x), Math.abs(L[i].y - P.y)) <= 2) return L[i];
    return null;
  }
  function lhBell(n) {
    var li = lhList().indexOf(n);
    api('/api/tower-mine-archi?action=bell:' + li).then(function (d) { if (d && d.sea) seaSt = d.sea; if (d && d.message) { if (d.ok) flashT0 = performance.now(); msg(d.message, 3000); } }).catch(function () { });
  }
  var allySailors = null;
  var ALLY_LOOK = [{ hat: '#8a909c|helmet', top: '#8a6a3a|armor', hair: '#3a2a1a' }, { hat: '#8a909c|helmet', top: '#6a7a4a|armor', hair: '#5a3a1a' }]; // [2026-11-19] "수병을 마을 자치병사 느낌으로"(시안 승인) -- 쇠 투구 + 가죽 갑옷
  function allyShipDraw(x, y, left, now) { // (x, y) = 배 왼쪽 위(화면), 오른쪽을 보고 그린 배를 left면 뒤집는다
    var u = T * scale / 16, bob = Math.floor(now / 500) % 2;
    var R = function (cc, a, b, w, h) { ctx.fillStyle = cc; ctx.fillRect(x + a * u, y + (b + bob) * u, w * u, h * u); };
    ctx.save();
    if (left) { ctx.translate(2 * x + 50 * u, 0); ctx.scale(-1, 1); }
    R('#4a2c14', 30, -22, 2, 36); R('#7a4a22', 30, -22, 1, 36);
    ctx.fillStyle = '#eef4fa'; ctx.beginPath(); ctx.moveTo(x + 33 * u, y + (-21 + bob) * u); ctx.quadraticCurveTo(x + 46 * u, y + (-8 + bob) * u, x + 50 * u, y + (8 + bob) * u); ctx.lineTo(x + 33 * u, y + (9 + bob) * u); ctx.closePath(); ctx.fill();
    R('#2a6ab8', 33, -9, 15, 3); R('#2a6ab8', 33, -1, 17, 3); R('#ffffff', 37, -5, 6, 1); R('#ffffff', 36, -4, 2, 1); R('#ffffff', 42, -4, 2, 1);
    R('#2a6ab8', 26, -25, 4, 3); R('#ffffff', 27, -24, 1, 1);
    R('#5a3418', -2, 9, 52, 2); R('#b07a3c', -2, 9, 52, 1);
    try { if (!allySailors) allySailors = [MineChar.sprite('right', 0, 0, ALLY_LOOK[0]), MineChar.sprite('right', 0, 3, ALLY_LOOK[1])]; ctx.drawImage(allySailors[0], x + 2 * u, y + (-4 + bob) * u, 16 * u, 16 * u); ctx.drawImage(allySailors[1], x + 15 * u, y + (-4 + bob) * u, 16 * u, 16 * u); } catch (e) { } // [2026-11-18] "수병 소환 때 렉" -- 프레임마다 캐릭터 그림을 새로 만들던 것을 한 번만
    R('#3a2210', -4, 10, 2, 6); R('#1f4f8f', -3, 12, 55, 2); R('#3a7ac8', -3, 12, 55, 1); R('#5a3418', -2, 14, 53, 1); R('#3a2210', 0, 15, 48, 1);
    R('#e0b050', -4, 11, 56, 1); R('#e0b050', 51, 9, 2, 2); R('#e0b050', 52, 7, 2, 2); R('#e0b050', 53, 6, 1, 1);
    for (var k = 2; k < 50; k += 8) R('#ffd76a', k, 12, 1, 2);
    ctx.fillStyle = 'rgba(255,255,255,.6)'; ctx.fillRect(x - 8 * u, y + 17 * u, 66 * u, u);
    ctx.restore();
  }
  function seaDrawBack(now, camX, camY) { // 등대 + 화약통(몬스터보다 먼저)
    if (!map) return;
    var TS = T * scale, t = nowSrv(), bs = (seaSt && seaSt.barrel) || [], bl = (seaSt && seaSt.bell) || [];
    lhList().forEach(function (n, i) { // [2026-11-18] 등대 -- 종을 치고 3.5초 동안 흔들리며 울린다
      var lx = (n.x - 1) * TS - camX, ly = n.y * TS - camY;
      if (lx < -4 * TS || ly < -2 * TS || lx > el.cv.width + 2 * TS || ly > el.cv.height + 6 * TS) return;
      lhDraw(lx, ly, now, bl[i] && t - bl[i] < 3500, lhLit() || (bl[i] && t - bl[i] < 3500));
    });
    if (moored && moored.m === map.id && !boatAt(P.x, P.y)) { var mx = Math.round(moored.x * TS - camX), my = Math.round(moored.y * TS - camY); drawBoat(mx, my - 3 * scale, now, moored.left); drawBoatFront(mx, my - 3 * scale, now, moored.left); } // 매어 둔 빈 배
    if (!map.barrels) return;
    map.barrels.forEach(function (b, i) {
      var ex = bs[i] || 0, x = b[0] * TS - camX, y = b[1] * TS - camY;
      if (ex && t - ex < 60000) {
        if (t - ex < 1200) { var q = (t - ex) / 1200; ctx.fillStyle = 'rgba(255,' + Math.round(200 - 120 * q) + ',60,' + (0.85 * (1 - q)).toFixed(2) + ')'; ctx.beginPath(); ctx.arc(x + TS / 2, y + TS / 2, TS * (0.5 + 2 * q), 0, Math.PI * 2); ctx.fill(); }
        return;
      }
      ctx.drawImage(seaBarrelSprite(Math.floor(now / 400 + i) % 2), x, y + Math.round(Math.sin(now / 500 + i) * scale), TS, TS);
    });
  }
  // [2026-11-19] "크라켄 공격 시 다리 휘두르는 모션, 모션이 나올 때만 공격받게" -- 크라켄 근접 공격(서버 hit.krk)마다 촉수 하나가
  //  들어 올렸다가(0 ~ 0.4) 내 자리로 내려치고(0.4 ~ 0.6) 누른 채 물보라(0.6 ~ 0.74) 거둬들인다(~ 1). 피해 숫자는 내려친 순간(KRK_SWING_HIT)에.
  var KRK_SWING_MS = 760, KRK_SWING_HIT = 0.6, krkSwings = [];
  function krkSwingAdd(kx, ky, tx, ty) { krkSwings.push({ kx: kx, ky: ky, tx: tx, ty: ty, t0: performance.now() }); if (krkSwings.length > 4) krkSwings.shift(); }
  /** (kx, ky) = 크라켄 가운데 칸, (tx, ty) = 맞는 칸(맵 좌표). 화면 좌표로 바꿔 그린다. */
  function krkSwingDraw(now, camX, camY) {
    if (!krkSwings.length) return;
    var TS = T * scale, u = scale;
    krkSwings = krkSwings.filter(function (s) { return now - s.t0 < KRK_SWING_MS; });
    krkSwings.forEach(function (s) {
      var q = (now - s.t0) / KRK_SWING_MS, kx = (s.kx + 0.5) * TS - camX, ky = (s.ky + 0.5) * TS - camY, px2 = (s.tx + 0.5) * TS - camX, py2 = (s.ty + 0.5) * TS - camY;
      var dir = px2 >= kx ? 1 : -1, rx = kx + dir * 1.2 * TS, ry = ky + 1.1 * TS; // 뿌리 -- 크라켄 몸 아래 맞는 쪽
      var hx = rx + dir * 0.6 * TS, hy = ky - 3.6 * TS, tx, ty, cx, cy, fade = 1;
      if (q < 0.4) { var a = q / 0.4, e = 1 - (1 - a) * (1 - a); tx = rx + dir * 0.4 * TS + (hx - rx - dir * 0.4 * TS) * e; ty = ry - 0.3 * TS + (hy - ry + 0.3 * TS) * e; cx = rx + dir * 1.6 * TS; cy = (ry + ty) / 2; } // 들어 올린다
      else if (q < KRK_SWING_HIT) { var b = (q - 0.4) / (KRK_SWING_HIT - 0.4), e2 = b * b; tx = hx + (px2 - hx) * e2; ty = hy + (py2 - hy) * e2; cx = (rx + tx) / 2 + dir * 0.4 * TS; cy = Math.min(ry, ty) - 0.6 * TS - 1.6 * TS * (1 - b); } // 내려친다(꼭대기에서 내 자리로)
      else if (q < 0.74) { tx = px2; ty = py2; cx = (rx + px2) / 2 + dir * 0.4 * TS; cy = Math.min(ry, py2) - 0.6 * TS; } // 내 자리를 누르고
      else { var c = (q - 0.74) / 0.26; tx = px2 + (rx - px2) * c * 0.6; ty = py2 + (ry - py2) * c * 0.6; cx = (rx + tx) / 2; cy = Math.min(ry, ty) - 0.4 * TS; fade = 1 - c; } // 거둬들인다
      ctx.save(); ctx.globalAlpha = Math.max(0, fade);
      var n = 30, pts = [];
      for (var i = 0; i <= n; i++) { var t = i / n, w = 1 - t; pts.push([w * w * rx + 2 * w * t * cx + t * t * tx, w * w * ry + 2 * w * t * cy + t * t * ty, 5 - 4 * t]); }
      var dot = function (x, y, r, col) { ctx.fillStyle = col; for (var yy = -r; yy <= r; yy++) for (var xx = -r; xx <= r; xx++) if (xx * xx + yy * yy <= r * r + 0.3) ctx.fillRect(Math.round(x / u + xx) * u, Math.round(y / u + yy) * u, u, u); };
      pts.forEach(function (p) { dot(p[0], p[1], Math.round(p[2]) + 1, '#5a1028'); });
      pts.forEach(function (p) { dot(p[0], p[1], Math.round(p[2]), '#7a1e3a'); });
      pts.forEach(function (p, i) { if (i % 2 === 0) { ctx.fillStyle = '#a8405e'; ctx.fillRect(Math.round(p[0] / u - p[2] * 0.4) * u, Math.round(p[1] / u - p[2] * 0.5) * u, u, u); } });
      pts.forEach(function (p, i) { if (i % 3 === 1 && p[2] >= 1.5) { ctx.fillStyle = '#f4b8c4'; ctx.fillRect(Math.round(p[0] / u + dir * p[2] * 0.4) * u, Math.round(p[1] / u + p[2] * 0.5) * u, 2 * u, u); } });
      if (q >= 0.4 && q < KRK_SWING_HIT) { ctx.strokeStyle = 'rgba(232,246,255,.7)'; ctx.lineWidth = 2 * u; ctx.beginPath(); ctx.moveTo(hx, hy); ctx.quadraticCurveTo(cx + dir * TS, cy, tx, ty); ctx.stroke(); } // 휘두르는 바람
      if (q >= KRK_SWING_HIT) { // 물보라
        var sq = Math.min(1, (q - KRK_SWING_HIT) / (1 - KRK_SWING_HIT));
        ctx.globalAlpha = 1 - sq; ctx.strokeStyle = '#e8f6ff'; ctx.lineWidth = 2 * u;
        ctx.beginPath(); ctx.ellipse(px2, py2 + 0.3 * TS, TS * (0.4 + 1.1 * sq), TS * (0.15 + 0.4 * sq), 0, 0, Math.PI * 2); ctx.stroke();
        for (var k = 0; k < 7; k++) { var an = Math.PI + k / 6 * Math.PI; ctx.fillStyle = k % 2 ? '#cfe8ff' : '#ffffff'; ctx.fillRect(px2 + Math.cos(an) * TS * (0.4 + sq), py2 + Math.sin(an) * TS * (0.3 + 1.2 * sq) - sq * 0.4 * TS, 2 * u, 2 * u); }
      }
      ctx.restore();
    });
  }
  // [2026-11-18] 등대 구조선(시안 승인) -- 서버가 준 res = {x, y, 체력, 가라앉은 시각, 부른 시각, 붙는 시각, 작살 수, 다음 작살, 다음 내려찍기, 마지막 작살 시각, 오는 쪽}.
  //  자리가 바뀌면 1초 동안 미끄러지듯, 작살은 크라켄 가운데(sea.kx/ky)로. 가라앉으면 두 동강 나며 잠기고 수병의 넋이 떠오른다.
  var resV = null, resSeen = { shots: 0, hp: 100 };
  function seaDrawFront(now, camX, camY) {
    if (!map || map.id !== 'opensea' || !seaSt) return;
    var r = seaSt.res, TS = T * scale, u = TS / 16, t = nowSrv();
    if (!r || !r[4]) { resV = null; return; }
    if (!resV || resV.k !== r[4]) { resV = { k: r[4], fx: r[0], fy: r[1], tx: r[0], ty: r[1], t0: now }; resSeen = { shots: 0, hp: 100 }; }
    if (resV.tx !== r[0] || resV.ty !== r[1]) { var q0 = Math.min(1, (now - resV.t0) / 1000); resV.fx += (resV.tx - resV.fx) * q0; resV.fy += (resV.ty - resV.fy) * q0; resV.tx = r[0]; resV.ty = r[1]; resV.t0 = now; }
    var q = Math.min(1, (now - resV.t0) / 1000), sx = resV.fx + (resV.tx - resV.fx) * q, sy = resV.fy + (resV.ty - resV.fy) * q;
    var side = r[10] || 1, left = side > 0, kx = seaSt.kx != null ? seaSt.kx + 0.5 : sx - side * 7, ky = seaSt.ky != null ? seaSt.ky + 0.5 : sy;
    var X = Math.round((sx + 0.5) * TS - camX - 25 * u), Y = Math.round(sy * TS - camY - 2 * u);
    if (r[3] > 0) { // 가라앉는다
      var sq = (t - r[3]) / 3200;
      if (sq >= 1 || sq < 0) return;
      ctx.save(); ctx.globalAlpha = 1 - sq * 0.6;
      ctx.save(); ctx.translate(X + 20 * u, Y + 10 * u + sq * TS); ctx.rotate(-0.45 * Math.min(1, sq * 3)); ctx.beginPath(); ctx.rect(-60 * u, -60 * u, 46 * u, 90 * u); ctx.clip(); allyShipDraw(-20 * u, -10 * u, left, 0); ctx.restore();
      ctx.save(); ctx.translate(X + 34 * u, Y + 12 * u + sq * TS * 1.2); ctx.rotate(0.5 * Math.min(1, sq * 3)); ctx.beginPath(); ctx.rect(-14 * u, -60 * u, 60 * u, 90 * u); ctx.clip(); allyShipDraw(-34 * u, -12 * u, left, 0); ctx.restore();
      ctx.restore();
      ctx.fillStyle = '#3a78d4'; ctx.globalAlpha = Math.min(1, sq * 2); ctx.fillRect(X - 10 * u, Y + 17 * u, 72 * u, TS * 1.6); ctx.globalAlpha = 1; // 물에 잠김
      for (var bb = 0; bb < 9; bb++) { var bq = ((now / 450) + bb * 0.13) % 1; fxDot(X + (bb * 7 - 4) * u, Y + 17 * u - bq * TS * 0.9, 2 * scale, '#e8f6ff', 0.75 * (1 - bq) * (1 - sq)); }
      ctx.fillStyle = '#7a4a22'; ctx.fillRect(X + 52 * u, Y + 14 * u + Math.sin(now / 300) * u, 10 * u, 2 * u); ctx.fillRect(X - 8 * u, Y + 20 * u, 7 * u, 2 * u); // 나무 조각
      try { // 쓰러진 수병 -- 넋이 떠오른다
        if (!allySailors) allySailors = [MineChar.sprite('right', 0, 0, ALLY_LOOK[0]), MineChar.sprite('right', 0, 3, ALLY_LOOK[1])];
        ctx.save(); ctx.globalAlpha = 0.4 * (1 - sq); ctx.filter = 'grayscale(1) brightness(1.6)';
        ctx.drawImage(allySailors[0], X + 6 * u, Y - (10 + sq * 40) * u, TS, TS); ctx.drawImage(allySailors[1], X + 26 * u, Y - (4 + sq * 44) * u, TS, TS); ctx.restore();
      } catch (e) { }
      ctx.filter = 'none'; ctx.globalAlpha = 1;
      return;
    }
    if (r[6] > resSeen.shots) { floats.push({ x: kx - 0.5, y: ky - 2.5, text: '🎯 작살 ' + r[6] + '/5', color: '#ffd24a', big: true, t0: performance.now() }); resSeen.shots = r[6]; }
    if (r[2] < resSeen.hp) { floats.push({ x: sx, y: sy - 2.2, text: r[2] > 0 ? '🛡️ 구조선이 대신 맞았다!' : '⚓ 구조선이 부서졌어요...', color: '#9fe0ff', big: true, t0: performance.now() }); resSeen.hp = r[2]; }
    allyShipDraw(X, Y, left, now);
    var hpw = 3 * TS, hpx = X + 1 * u, hpy = Y - 28 * u; // 구조선 체력
    ctx.fillStyle = 'rgba(0,0,0,.55)'; ctx.fillRect(hpx, hpy, hpw, 3 * scale); ctx.fillStyle = r[2] > 50 ? '#7ad8ff' : '#ff8a5a'; ctx.fillRect(hpx, hpy, hpw * r[2] / 100, 3 * scale);
    if (r[9] && t - r[9] < 450) { // 작살
      var aq = (t - r[9]) / 450, ax = X + (left ? 0 : 50 * u), ay = Y, tx = kx * TS - camX, ty2 = ky * TS - camY, hx = ax + (tx - ax) * aq, hy = ay + (ty2 - ay) * aq;
      ctx.strokeStyle = '#e8e0c8'; ctx.lineWidth = 2 * dpr; ctx.beginPath(); ctx.moveTo(hx - (tx - ax) * 0.12, hy - (ty2 - ay) * 0.12); ctx.lineTo(hx, hy); ctx.stroke();
      ctx.fillStyle = '#c0c8d0'; ctx.fillRect(hx - 3, hy - 3, 6, 6);
    }
  }
  function mobSprite(k, pose, f) {
    var tyS = map.mobTypes[k] || {}; // [2026-11-18] 바다 위
    if (tyS.name === '심해의 크라켄') return krakenSprite((f | 0) % 2, !!pose);
    if (tyS.sea) return seaSprite(k, (f | 0) % 2);
    var art = BOSS_ART[(map.mobTypes[k] || {}).src || (map.mobTypes[k] || {}).name]; // [2026-10-29] 보스 전용 그림(48x48, 2프레임) // [2026-11-17] 악마화된 몬스터는 원래 그림
    if (art) { var bk = 'b' + k + (pose ? 'p' : '') + ((f | 0) % 2); if (!mobSprites[bk]) mobSprites[bk] = bossArt(art, (f | 0) % 2, pose); return mobSprites[bk]; }
    var ck = pose ? k + 'p' : k; // [2026-10-28] pose = 닌자 두목 분신술 중 진짜(인을 맺은 자세)
    if (mobSprites[ck]) return mobSprites[ck];
    var ty = map.mobTypes[k] || {}, c = mk(T, T), g = c.getContext('2d'), col = ty.color || '#888888', dk = shade(col, -0.35), lt = shade(col, 0.3);
    var eye = ty.tier >= 4 ? '#ff3b30' : '#1a1a1a';
    switch (ty.kind) {
      case 'demon': // [2026-11-16] 악마 -- 뿔, 박쥐 날개, 불타는 눈, 꼬리
        px(g, dk, 0, 4, 4, 6); px(g, col, 1, 5, 3, 4); px(g, dk, 0, 10, 1, 2); px(g, dk, 2, 10, 1, 1); px(g, dk, 12, 4, 4, 6); px(g, col, 12, 5, 3, 4); px(g, dk, 15, 10, 1, 2); px(g, dk, 13, 10, 1, 1); // 날개
        px(g, dk, 5, 12, 2, 3); px(g, dk, 9, 12, 2, 3); px(g, col, 4, 7, 8, 5); px(g, lt, 5, 7, 6, 1); px(g, dk, 6, 9, 4, 2); px(g, '#ff7a2a', 7, 10, 2, 1); // 몸 + 가슴의 불씨
        px(g, col, 4, 2, 8, 5); px(g, lt, 5, 2, 6, 1); px(g, '#f4e8c8', 3, 0, 1, 3); px(g, '#f4e8c8', 12, 0, 1, 3); px(g, '#f4e8c8', 4, 2, 1, 1); px(g, '#f4e8c8', 11, 2, 1, 1); // 머리 + 뿔
        px(g, '#ffd24a', 5, 4, 2, 1); px(g, '#ffd24a', 9, 4, 2, 1); px(g, '#ff3b1a', 6, 4); px(g, '#ff3b1a', 9, 4); px(g, '#12080a', 6, 6, 4, 1); px(g, '#f4e8c8', 6, 6); px(g, '#f4e8c8', 9, 6); // 눈, 송곳니
        px(g, dk, 11, 13, 3, 1); px(g, dk, 14, 12, 1, 1); px(g, '#ff7a2a', 15, 11, 1, 1); break; // 꼬리
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
    // [2026-11-20] "검기를 배우면 기본 공격 사거리도 같이(무속성), 검기 사거리 중 가장 높은 것만큼" -- 바라보는 쪽으로 2 ~ 사거리 칸(서버 mineMeleeReach)
    var reach = (st && st.aura && st.aura.reach) | 0;
    for (var rk = 2; f && rk <= reach; rk++) { var rm = mobOcc[(P.x + f[0] * rk) + ',' + (P.y + f[1] * rk)]; if (rm) return { m: rm, dir: P.dir }; }
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
    if (n.ox != null && n.mt === t) { // [2026-11-16] 거룡(3x3)이 자리를 옮긴다 -- 걸어가지 않고 날아올랐다 내려앉는다(1.8초)
      var hmb = map && map.mobs && map.mobs[a[0]], hty = hmb && map.mobTypes[hmb.k];
      if (hty && hty.foot >= 3 && Math.abs(n.fx - n.ox) + Math.abs(n.fy - n.oy) > 2) { n.md = 1800; n.hop = 1; }
      else if (hty && hty.demon && Math.abs(n.fx - n.ox) + Math.abs(n.fy - n.oy) > 2.5) { // [2026-11-16] 악마의 순간이동 -- 걷지 않고 사라졌다 나타난다(양쪽에 보랏빛 번쩍)
        for (var bi = 0; bi < 6; bi++) { lootPops.push({ x: n.ox + (bi % 3 - 1) * 0.3, y: n.oy + 0.2, t0: performance.now() + bi * 25, g: 'blink' }); lootPops.push({ x: n.fx + (bi % 3 - 1) * 0.3, y: n.fy + 0.2, t0: performance.now() + 60 + bi * 25, g: 'blink' }); }
        n.ox = null; n.oy = null; n.mt = null;
      }
    } else if (old && old.hop && n.mt === old.mt) n.hop = 1;
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
    if (el.smith) el.smith.addEventListener('scroll', smithPopFit); // [2026-11-20] 확인 창이 떠 있는 채로 스크롤해도 보이는 자리에
    // [2026-11-19] "핸드폰에서 공격 누르고 있으면 자동 공격 -- PC 스페이스 누르고 있는 것과 똑같이" -- 누르는 동안 atkBtn을 되풀이(공격 간격 420ms는 atkBtn/attack이 지킨다).
    //  손가락/마우스는 pointerdown부터, 키보드(Enter -- click의 detail 0)는 한 번. 창이 열리면(워프/대장간 등) 멈춘다.
    if (el.atk) {
      var atkHoldT = null, atkHoldStop = function () { if (atkHoldT) { clearInterval(atkHoldT); atkHoldT = null; } };
      el.atk.onclick = function (e) { if (!e || e.detail === 0) atkBtn(); };
      el.atk.addEventListener('pointerdown', function (e) {
        if (e.button > 0) return;
        e.preventDefault(); atkHoldStop(); atkBtn();
        atkHoldT = setInterval(function () { if (!opened || smithOpen) { atkHoldStop(); return; } atkBtn(); }, 120);
      });
      ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (t) { el.atk.addEventListener(t, atkHoldStop); });
      window.addEventListener('blur', atkHoldStop);
      el.atk.addEventListener('contextmenu', function (e) { e.preventDefault(); }); // 길게 누르면 뜨는 메뉴 막기
      el.atk.style.touchAction = 'none'; el.atk.style.webkitUserSelect = 'none'; el.atk.style.userSelect = 'none'; el.atk.style.webkitTouchCallout = 'none';
    }
    // [2026-11-19] "광물 캐기 버튼도 연속으로 누르고 있을 수 있게(나무 캐기나 빙석)" -- 하피의 둥지 나무(chopTree)와 설산 동굴 얼음 광맥(veinDig)은 누르는 동안 되풀이.
    //  서버가 0.4초(나무)/0.3초(광맥)보다 빨리 찍으면 거절하니 0.45초마다. 그 밖의 채광(한 칸 한 번)과 바다 낚시는 한 번만.
    if (el.dig) {
      var digHoldT = null, digHoldStop = function () { if (digHoldT) { clearInterval(digHoldT); digHoldT = null; } };
      el.dig.onclick = function (e) { if (!e || e.detail === 0) digBtn(); };
      el.dig.addEventListener('pointerdown', function (e) {
        if (e.button > 0) return;
        e.preventDefault(); digHoldStop(); digBtn();
        if (map && (map.trees || map.vein)) digHoldT = setInterval(function () { if (!opened || smithOpen || !map || !(map.trees || map.vein)) { digHoldStop(); return; } digBtn(); }, 450);
      });
      ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (t) { el.dig.addEventListener(t, digHoldStop); });
      window.addEventListener('blur', digHoldStop);
      el.dig.addEventListener('contextmenu', function (e) { e.preventDefault(); });
      el.dig.style.touchAction = 'none'; el.dig.style.webkitUserSelect = 'none'; el.dig.style.userSelect = 'none'; el.dig.style.webkitTouchCallout = 'none';
    }
    window.addEventListener('resize', function () { if (opened) fit(); });
    zoomHud(); // [2026-11-19] 메뉴의 화면 크기 표시
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
    baseScale = Math.max(2, Math.round(Math.min(el.cv.width, el.cv.height) / (T * 9)));
    scale = zoomScale();
    ctx.imageSmoothingEnabled = false;
  }
  // [2026-11-19] "화면 크기는 옵션으로 확대/축소 1~2단계씩만" -- ☰ 메뉴의 🔍 -/+ (이 기기에 저장, -2 ~ +2). 한 단계 = 도트 배율 1(정수배라 뭉개지지 않는다), 최소 2배.
  var zoomLv = (function () { try { var v = parseInt(localStorage.getItem('tw-mine-zoom'), 10); return v >= -2 && v <= 2 ? v : 0; } catch (e) { return 0; } })();
  function zoomScale() { return Math.max(2, baseScale + zoomLv); }
  function zoomLabel() { return zoomLv === 0 ? '보통' : (zoomLv > 0 ? '+' : '') + zoomLv; }
  function zoomGo(d) {
    var v = Math.max(-2, Math.min(2, zoomLv + d));
    if (v === zoomLv) { msg(d > 0 ? '🔍 더 크게는 안 돼요. (최대 +2)' : '🔍 더 작게는 안 돼요. (최소 -2)', 1500); return; }
    zoomLv = v; try { localStorage.setItem('tw-mine-zoom', String(v)); } catch (e) { }
    if (Math.max(2, baseScale + v) === scale && d < 0) msg('🔍 이 화면에선 더 작게 할 수 없어요.', 1500);
    scale = zoomScale(); zoomHud();
  }
  function zoomHud() { var z = document.getElementById('mineZoomLv'); if (z) z.textContent = zoomLabel(); var ds = document.getElementById('mineDmgSum'); if (ds) ds.textContent = dmgSum ? '합산' : '속성별';  var cr = document.getElementById('mineCharRes'); if (cr) cr.textContent = charHD ? '64×64' : '16×16'; }
  // [2026-11-20] 데미지 숫자 -- 속성별(기본) / 합산 한 번. 이 기기에 저장(tw-mine-dmgsum).
  var dmgSum = (function () { try { return localStorage.getItem('tw-mine-dmgsum') === '1'; } catch (e) { return false; } })();
  function dmgSumGo(on) { dmgSum = !!on; try { localStorage.setItem('tw-mine-dmgsum', dmgSum ? '1' : '0'); } catch (e) { } zoomHud(); msg(dmgSum ? '💥 데미지를 합쳐 한 번에 보여 줘요.' : '💥 데미지를 속성별로 보여 줘요.', 1600); }

  // [2026-11-20] "캐릭터를 16x16 / 64x64 중 고르기(옵션), 64x64는 애니메이션풍 고해상도, 코스튬 전체·날개 포함" -- 기본 64x64, 이 기기에 저장(tw-mine-hd).
  //  바꾸면 그림 캐시(내 캐릭터/다른 유저/주민/외형 미리보기)를 비우고 다시 그린다. 그리는 곳은 같은 칸 크기라 배치는 그대로.
  var charHD = (function () { try { return localStorage.getItem('tw-mine-hd') !== '0'; } catch (e) { return true; } })();
  try { if (window.MineChar && MineChar.setHD) MineChar.setHD(charHD); } catch (e) { }
  function charResGo(hd) {
    hd = !!hd;
    if (hd === charHD) return;
    charHD = hd;
    try { localStorage.setItem('tw-mine-hd', hd ? '1' : '0'); } catch (e) { }
    try { MineChar.setHD(hd); } catch (e) { }
    otherSprites = {}; lookImgCache = {}; citCache = {}; allySailors = null; spriteKey = null;
    if (st) buildSprites(st.look);
    zoomHud();
    msg(hd ? '🧍 캐릭터를 64×64 고해상도로 그려요.' : '🧍 캐릭터를 16×16 도트로 그려요.', 1800);
  }
  // [2026-11-19] "크라켄이 커서 폰 화면에서 잘린다" -- 크라켄과 싸우는 동안(krkShown)은 카메라를 나와 크라켄(5x5칸 + 휘두르는 다리) 사이로 부드럽게 옮긴다
  //  (같은 날 "화면 크기는 옵션으로" -- 자동 축소는 뺐다). 나는 늘 화면 안(가장자리 1.5칸 안쪽), 아래쪽은 버튼이 덮으니 위 80%에 맞춘다.
  var baseScale = 3, krkCam = { ox: 0, oy: 0, t: 0 };
  function krkView(now, W, H, pos) {
    scale = zoomScale();
    var tx = 0, ty = 0, kx = null, ky = null;
    if (map && map.id === 'opensea' && krkShown()) {
      if (seaSt && seaSt.kx != null) { kx = seaSt.kx + 0.5; ky = seaSt.ky + 0.5; }
      else (map.mobs || []).forEach(function (m) { var t2 = map.mobTypes[m.k]; if (t2 && t2.name === '심해의 크라켄' && m._p) { kx = m._p.x + 2.5; ky = m._p.y + 2.5; } });
    }
    if (kx != null) {
      var px0 = pos.x + 0.5, py0 = pos.y + 0.5;
      var x0 = Math.min(px0 - 1.2, kx - 3.8), x1 = Math.max(px0 + 1.2, kx + 3.8), y0 = Math.min(py0 - 1.8, ky - 4.4), y1 = Math.max(py0 + 1.2, ky + 3.4);
      var vw = W / (T * scale), vh = H / (T * scale), lx = Math.max(0, vw / 2 - 1.5), ly = Math.max(0, vh * 0.4 - 1.5);
      tx = Math.max(-lx, Math.min(lx, (x0 + x1) / 2 - px0)); ty = Math.max(-ly, Math.min(ly, (y0 + y1) / 2 - py0 + vh * 0.1));
    }
    var k = krkCam.t ? 1 - Math.exp(-Math.min(500, now - krkCam.t) / 220) : 1;
    krkCam.t = now; krkCam.ox += (tx - krkCam.ox) * k; krkCam.oy += (ty - krkCam.oy) * k;
    return pos; // [2026-11-19] "크라켄 조우 시 화면이 이상했어 -- 카메라 초점 잡는 것만 롤백" -- 카메라는 다시 늘 나를 가운데로(위 계산은 쓰지 않는다)
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
    if (st && st.look && st.look.shoesCode === 'SHOE_GM') return !!map && x >= 0 && y >= 0 && x < map.w && y < map.h && (PORTALS.indexOf(c) < 0 || c === 'X') && c !== 'N'; /* [2026-11-18] "워프 포인트 근처를 밟는 순간 창이 뜬다" -- 바람의 신은 X(문 칸)를 못 지나가 부딪힐 때마다 창이 열렸다 -> X는 지나간다(멈춰 서서/공격 버튼으로 연다) */ // [2026-10-28] 바람의 신(운영자) -- 장애물/몬스터 통과, NPC/포탈 칸에선 멈춘다(말 걸기/들어가기)
    if (mobOcc[x + ',' + y]) return false; // [2026-10-24] 몬스터가 있는 칸은 못 지나간다
    var twd = twDecorCell(); // [2026-11-16] 탑 방 한가운데 상자/제단/샘은 돌아서 간다
    if (twd && twd[0] === x && twd[1] === y) return false;
    if (antGate(x)) return false; // [2026-11-04] 사막 서쪽 방벽 문 -- Lv.41 미만은 서쪽으로 못 지나간다(바람에도)
    return WALK.indexOf(c) >= 0 || (c === '~' && (!!(st && st.waterWalk) || !!(map && map.boat) || !!(map && map.sail && (seaBoatOk() || cellAt(P.x, P.y) === '~')))); /* [2026-11-21] 고침: 배가 없거나 부서진 채 물 위에 있으면 물로도 움직여 뭍으로 나올 수 있게(갇히지 않게) */ // [2026-10-24] 용왕의 반지 = 물 위 걷기, [2026-10-28] 다도해 뱃길 = 배, [2026-11-18] 남쪽 바닷가 = 배가 있으면
  }
  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  // [2026-10-23] 스태미너 -> 곡괭이 내구도(캘 때마다 1, 시간 회복 없음, 마을 대장장이에게 광석으로 강화 = 최대/현재 함께 증가)
  function resetText(ms) {
    if (!ms) return '';
    var d = new Date(ms);
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }
  function showStamina() {
    var fishOn = !!(map && fishHere()), fdur = st.fishDur != null ? st.fishDur : 100; // [2026-11-20] 낚시 자리면 낚시 내구도(따로 100)
    el.sta.textContent = fishOn ? fdur + ' / ' + (st.fishDurMax || 100) : st.durability + ' / ' + st.durabilityMax; // [2026-10-30] 채광 버튼 꼬리표
    el.sta.classList.toggle('zero', fishOn ? fdur < 1 : st.durability < 1);
    // [2026-10-24] 오른쪽 위 안내는 맵 리셋 시각만(대장장이 안내/보석 목록은 뺐다)
    regenText();
    el.dig.disabled = busy; // 내구도가 없어도 누를 수 있다(캐기는 dig()가 안내)
    if (el.dig.firstChild && el.dig.firstChild.nodeType === 3) el.dig.firstChild.nodeValue = map && (fishHere() || (map.noDig && !map.trees && FISH_MAPS[map.id])) ? '🎣' : '⛏️'; // [2026-11-18] 바다에선 낚시, [2026-11-19] 모두에게
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
    // [2026-11-17] "진환생은 선택 -- 강제하는 버튼이나 안내는 없게": 환생 가능 안내(알림/띠/체력바 꼬리표)를 없앴다. 환생은 성당 신부 창에서만.
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
        ;
    // [2026-10-28] 환생 꼬리표 -- 폭 112px 안에 들어가면 "Lv.1(환생1)", 넘치면 "Lv.1✦1", 그래도 넘치면(환생 뒤 체력이 7자리) 체력을 "1415k"로
    // [2026-11-05] "체력바 옆 숫자가 UI를 넘어온다" -- 환생 안 한 사람도 넘치면 줄이고(예전엔 환생한 사람만), 1.2만 -> "12.3k", 그래도 넘치면 "1.4M"
    for (var fit = 0; fit < 4; fit++) {
      var hs = fit === 3 && h >= 1000000 ? (h / 1000000).toFixed(h < 10000000 ? 1 : 0) + 'M' : fit >= 2 && h >= 10000 ? (h / 1000).toFixed(h < 100000 ? 1 : 0) + 'k' : String(h);
      el.hp.innerHTML = '<span class="hp-l"><span>' + lvLabel(c, fit > 0) + '</span><span class="hp-n">' + (lk ? '🔒' : '❤') + hs + '</span></span>' // [2026-10-28] 짧게
          + '<span class="mine-bar hp"><i style="width:' + pct + '%"></i>' + (lk ? '<b style="width:' + lk + '%"></b>' : '') + '</span><span class="mine-bar ex"><i style="width:' + ep + '%"></i></span>'
          ;
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
  // [2026-11-13] 대광역 곡괭이(5x5) -- "자동캐기 시 캐고 나서 5칸은 이동한 뒤" -- 마지막으로 캔 곳에서 체비셰프 5칸 이상 떨어진 칸을 먼저 고른다(겹치지 않게)
  var lastDig = null;
  function wide5On() { var on = false; (st && st.gear || []).forEach(function (g) { if (g.code === 'WIDE_PICK5' && g.on) on = true; }); return on; }
  function autoFar(x, y) { return !(wide5On() && lastDig && map && lastDig.m === map.id && Math.max(Math.abs(x - lastDig.x), Math.abs(y - lastDig.y)) < 5); }
  function minable(x, y) {
    var c = cellAt(x, y), k = x + ',' + y;
    return 'VTYPHe~'.indexOf(c) < 0 && WALK.indexOf(c) >= 0 && !dug[k] && !noOre[k] && !noOreCell(x, y);
  }
  /** 가장 가까운 캘 칸으로 가는 첫 걸음 방향(넓이 우선 탐색, 30칸 안). 없으면 null. */
  function autoStep(far) {
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
        if (minable(nx, ny) && (!far || autoFar(nx, ny))) return first[ni];
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
    refiner: '#ff8a5a', primalsmith: '#fff4b0', alchemist: '#b8f06a', demonfolk: '#ff6a8a', wbboard: '#ffd27a', mailbox: '#7ab8ff', daykeeper: '#ffe14d', wallguard: '#ffe14d', swordmaster: '#ff6a3a', basecamp: '#bfefff', forestguard: '#ff8a5a', citymerchant: '#7cf08a', mercenary: '#ffb04a', enhancer: '#ff8a3a', armorer: '#9fd0ff', whirlmaster: '#8ef08e', ultmaster: '#ffd27a', ninjaquest: '#ff4a4a', boatwright: '#c8964a', ferry: '#3a8aff', chest: '#ffd23f', smelter: '#ff8a3a', gemcutter: '#3ad8d8', artisan: '#c84a4a', archishop: '#3a9aff' };
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
    if (armorerOn()) { miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(armorer.x - ox - 3 * k - 1, armorer.y - oy - 3 * k - 1, 6 * k + 3, 6 * k + 3); miniCtx.fillStyle = '#ff6a2a'; miniCtx.fillRect(armorer.x - ox - 3 * k, armorer.y - oy - 3 * k, 6 * k + 1, 6 * k + 1); } // [2026-11-20] 떠돌이 갑옷장이 -- 주황 점
    if (hermitOn()) { // [2026-10-30] "그 NPC 지도에 위치 표시" -- 은둔한 대장장이(같은 맵에 나와 있을 때) 금색 점
      miniCtx.fillStyle = '#ffffff'; miniCtx.fillRect(hermit.x - ox - 3 * k - 1, hermit.y - oy - 3 * k - 1, 6 * k + 3, 6 * k + 3);
      miniCtx.fillStyle = '#ffc61a'; miniCtx.fillRect(hermit.x - ox - 3 * k, hermit.y - oy - 3 * k, 6 * k + 1, 6 * k + 1);
    }
    // [2026-10-28] "미니맵에서 보스 위치도" -- 맵 맨 끝 몬스터가 보스면(살아 있을 때) 붉은 점(흰 테두리, 크게)
    var bm = map.mobs && map.mobs[map.mobs.length - 1], bty = bm && map.mobTypes[bm.k];
    if (bty && bty.boss && bm._p && !(bty.name === '심해의 크라켄' && !krkShown() && !krkAdminSee())) { // [2026-11-19] 크라켄은 싸우는 중에만
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
  // [2026-11-18] 워프 포인트 -- "위에서 공격하거나 오래 있으면 창이 뜨게, 너무 자주 멈춘다" -- 3x3 위는 그냥 걷고, 멈춰 서서 WP_STAND_MS가 지나거나 공격 버튼을 누르면 연다.
  //  서버는 플레이어 바로 옆(1칸) 워프 포인트 칸을 보라 -- 같은 3x3의 이웃 칸을 넘긴다.
  var WP_STAND_MS = 1500, wpStand = null;
  function wpHere() { return !!(map && cellAt(P.x, P.y) === 'X'); }
  function wpOpenHere() {
    var dv = [[1, 0], [-1, 0], [0, 1], [0, -1]];
    for (var i = 0; i < dv.length; i++) if (cellAt(P.x + dv[i][0], P.y + dv[i][1]) === 'X') { setAuto(false); wpOpen(P.x + dv[i][0], P.y + dv[i][1]); return true; }
    return false;
  }
  function wpStandCheck(now) {
    if (!wpHere()) { wpStand = null; return; }
    if (P.moving || busy || smithOpen) { if (P.moving) wpStand = null; return; }
    var key = map.id + ':' + P.x + ',' + P.y;
    if (!wpStand || wpStand.k !== key) { wpStand = { k: key, t0: now, done: false }; return; }
    if (!wpStand.done && now - wpStand.t0 >= WP_STAND_MS) { wpStand.done = true; wpOpenHere(); }
  }
  function update(now) {
    try { wpStandCheck(now); } catch (e) { }
    if (now - lookWarmT > 4000) { lookWarmT = now; try { lookWarm(); } catch (e) { } } // [2026-11-19] 치장 그림 미리(들어온 뒤 / 모습이 바뀌면)
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
          mm._p = mp;
          if ((map.mobTypes[mm.k] || {}).name === '심해의 크라켄' && !krkShown() && !krkAdminSee()) continue; // [2026-11-19] 숨은 크라켄은 길을 막지 않는다(보이지도 않으니)
          mobOcc[mp.cx + ',' + mp.cy] = mm;
          if ((map.mobTypes[mm.k] || {}).boss) { var bfo = (map.mobTypes[mm.k] || {}).foot || 2; for (var boy = 0; boy < bfo; boy++) for (var box = 0; box < bfo; box++) mobOcc[(mp.cx + box) + ',' + (mp.cy + boy)] = mm; } // [2026-10-29] 보스 = 2x2칸, [2026-11-15] 거룡 3x3(foot)
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
    var digShow = map.noDig && !map.trees && !FISH_MAPS[map.id] ? 'none' : ''; // [2026-11-11] 하피의 둥지 -- 캐기 버튼 = 곡괭이로 나무 찍기 // [2026-11-20] "나루터는 낚시가 안 돼" -- 낚시 지역이면 버튼(낚시)
    if (el.dig.style.display !== digShow) el.dig.style.display = digShow;
    if (el.auto) { var autoShow = map.noDig || map.vein ? 'none' : ''; if (el.auto.style.display !== autoShow) { el.auto.style.display = autoShow; if ((map.noDig || map.vein) && auto) setAuto(false); } } // [2026-10-28] 설산 동굴은 자동채광 없음
    if (now - hpShownT > 500) { hpShownT = now; renderHp(); }
    var twLk = twLocked(); // [2026-11-16] 탑 전투방 문 -- 막 열렸으면 문 앞에 알림
    if (twDoorWas && !twLk && map.tower && st && st.twRun && st.twRun.type === 'C') { flashT0 = now; floats.push({ x: map.w - 3, y: Math.floor(map.h / 2) - 0.8, text: '🚪 문이 열렸어요!', color: '#9fe0ff', big: true, t0: now }); }
    twDoorWas = twLk;
    bossTick(now); // [2026-10-24] 동굴 3층 보스 선공 + [2026-10-25] 몬스터 인식 중 전투 턴
    coldTick(now); coldWarn(); // [2026-10-26] 한기/눈바람
    scanTick(); // [2026-10-24] 탐지 곡괭이
    var ng = nearOther(); if (el.greet && (el.greet.style.display === "block") !== ng) el.greet.style.display = ng ? "block" : "none"; // [2026-10-24] 근처에 다른 유저가 있으면 👋
    if (P.moving && now - P.t0 >= stepMs()) {
      P.moving = false; P.x = P.tx; P.y = P.ty;
      showCoord(); drawMini();
      antFallCheck(); // [2026-10-28] 사막 서쪽 -- 개미굴
      if (twSpikeCell(P.x, P.y)) twSpikeStep(now); // [2026-11-16] 탑 함정방 가시
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
        else if (minable(P.x, P.y) && autoFar(P.x, P.y)) { dig(true); return; }
        else {
          d = autoStep(true);
          if (!d && minable(P.x, P.y)) { dig(true); return; } // 5칸 밖에 캘 곳이 없으면 가까운 곳이라도
          if (!d) d = autoStep(false);
          if (!d) setAuto(false, '🤖 근처(30칸)에 캘 곳이 없어 자동채광을 멈췄어요. 다른 곳으로 옮겨서 다시 켜 주세요.');
        }
      }
      if (d && map.board) { // [2026-11-15] 탑 보드 -- 주사위로만
        if (now - lastBumpT > 2500 || lastBump !== 'twboard') msg('🎲 이 층에서는 주사위로만 움직여요. 위쪽 [🎲 굴리기]를 눌러 주세요.', 2200);
        lastBump = 'twboard'; lastBumpT = now;
        d = null;
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
        } else if (armorerOn() && nx === armorer.x && ny === armorer.y) {
          talk(armorerNpc(), now); // [2026-11-20] 떠돌이 갑옷장이
        } else if (bossNpcOn() && nx === bossNpc.x && ny === bossNpc.y) {
          talk(bossNpcObj(), now); // [2026-11-13] 보스 소환사
        } else if (walk(nx, ny)) {
          P.fx = P.x; P.fy = P.y; P.tx = nx; P.ty = ny; P.t0 = now; P.moving = true; P.step = P.step === 1 ? 2 : 1;
        } else if (PORTALS.indexOf(cellAt(nx, ny)) >= 0) {
          if (cellAt(nx, ny) === 'G' && nx > map.w / 2 && twLocked()) { // [2026-11-16] 탑 전투방 -- 닫힌 문
            if (now - lastBumpT > 2200 || lastBump !== 'twdoor') msg('⚔️ 문이 쇠창살로 닫혀 있어요. 방을 지키는 몬스터를 쓰러뜨려야 열려요.', 2200);
            lastBump = 'twdoor'; lastBumpT = now;
          } else enterPortal(nx, ny, now);
        } else if (map.sail && cellAt(nx, ny) === '~' && !seaBoatOk()) { // [2026-11-21] 배가 없거나 부서져 물로 못 나갈 때 -- 아무 말 없이 막히던 것
          if (now - lastBumpT > 2500 || lastBump !== 'noboat') msg(st && st.archi && st.archi.boat ? '⛵ 배가 부서져 있어 물로 나갈 수 없어요. 다도해 본섬 배 목수에게 고쳐 달라고 하세요.' : '⛵ 1인용 배가 있어야 물 위로 나갈 수 있어요.', 2400);
          lastBump = 'noboat'; lastBumpT = now;
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
  var armorer = null, armorerAdminSaid = '', hermitArmor = false; // [2026-11-20] 떠돌이 갑옷장이(창은 은둔한 대장장이 것을 함께 -- hermitArmor면 갑옷)
  function armorerOn() { return !!(armorer && map && armorer.m === map.id && armorer.until > nowSrv()); }
  // [2026-11-13] 보스 소환사 -- 보스룸에 보스가 없을 때 보스 집 칸에 선다(위치 알림 bossCall). 소환하면 3초 카운트(at) 뒤 사라지고 보스가 나온다.
  var bossNpc = null, bossCallInfo = null, bossCallBusy = false, worldBossOpen = false;
  function bossNpcOn() { return !!(bossNpc && map && bossNpc.m === map.id && !(bossNpc.at && bossNpc.at <= nowSrv())); }
  function bossNpcObj() { return { x: bossNpc.x, y: bossNpc.y, kind: 'bosscall', name: '보스 소환사', line: '이곳의 주인을 불러내 드리지요. 한 번 부르면 두 시간 뒤에 다시 부를 수 있소.' }; }
  function callLeftTxt(ms) { var m = Math.ceil(ms / 60000); return m >= 60 ? Math.floor(m / 60) + '시간' + (m % 60 ? ' ' + m % 60 + '분' : '') : m + '분'; }
  function callHhmm(ms) { var d = new Date(ms), /* [2026-11-18] "월드보스 59분에 나온다는 거 정각으로" -- 예전 ms - srvOff(내 시계 기준)는 1초만 늦어도 :59로 잘렸다. 서버 시각 그대로 */ p = function (v) { return (v < 10 ? '0' : '') + v; }; return (d.toDateString() === new Date().toDateString() ? '' : (d.getMonth() + 1) + '/' + d.getDate() + ' ') + p(d.getHours()) + ':' + p(d.getMinutes()); }
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
  // [2026-11-15] 탑(시즌5식) -- 보드 층(map.board)은 걸어서 못 움직이고 🎲로만 칸을 옮긴다. 위쪽 띠: 층/칸 + [🎲] [🪜 층 이동] [나가기].
  var twBar = null, twBusy = false, twInfo = null, TW_ICON = { E: '🏁', C: '⚔️', T: '📦', P: '🗼', X: '⚠️', L: '🍀', U: '🔼', D: '🔽' };
  function twIndex() { var b = map && map.board; if (!b) return -1; for (var i = 0; i < b.length; i++) if (b[i][0] === P.x && b[i][1] === P.y) return i; return -1; }
  // ---- [2026-11-16] 전리품 연출 + 탑 방 구분 ----
  //  "보물방, 몬스터방, 함정방 구분이 잘 안 된다 -- 함정방엔 바닥에 가시가 있어서 체력을 잃으면서 간다든가, 보물방은 상자와 아이템이 우수수 떨어지는 이펙트.
  //   보스 처치 시에도 우수수 떨어지게(탑 보스 포함 모든 보스, 여러 사람이 같이 처치하면 그만큼 더 많이 떨어져서 해당되는 플레이어에게 흡수되는 이펙트)".
  //  전리품(loot): 한 자리에서 솟구쳐 둘레에 떨어지고(LOOT_FLY) 잠깐 바닥에 놓였다가(rest) 받는 사람에게 빨려 들어간다(LOOT_IN). 받는 사람 = 나({me}) 또는 다른 사람({id} -- 위치 알림 번호).
  //  탑 방: 보물방 = 한가운데 상자(들어서면 열리며 받은 것들이 쏟아진다), 증표방 = 증표 제단, 행운의 샘 = 샘, 함정방 = 가시 띠 셋(밟을 때마다 체력 -- 서버 mineTwSpikeHit).
  var loot = [], lootPops = [], lootImgs = {}, LOOT_FLY = 620, LOOT_IN = 420, LOOT_GRADES = [21, 22, 23, 24, 25, 26, 27, 28, 29, 6, 20];
  function lootImg(g) { var im = lootImgs[g]; if (!im) { im = lootImgs[g] = new Image(); im.src = oreIcon(g); } return im; }
  function lootBurst(sx, sy, grades, n, tgs, spread, delay) {
    var t0 = performance.now() + (delay || 0), gap = n > 40 ? 10 : 26;
    for (var i = 0; i < n; i++) {
      var a = Math.random() * Math.PI * 2, r = (0.55 + Math.random() * 0.95) * (spread || 2);
      loot.push({ sx: sx, sy: sy, tx: sx + Math.cos(a) * r, ty: sy + Math.sin(a) * r * 0.72, g: grades[i % grades.length], tg: tgs[i % tgs.length], t0: t0 + i * gap, up: 1.5 + Math.random() * 1.7, rest: 480 + Math.random() * 360 + (n - i) * gap });
    }
    if (loot.length > 420) loot.splice(0, loot.length - 420);
  }
  /** [2026-11-17] 마계 입구 -- 돌기둥 사이 거대한 포털(검보라 소용돌이 + 붉은 테 + 빨려 드는 불티). 문 칸(map.abyss) 위 2.6칸 반지름. */
  function abyssDraw(now, TS, camX, camY) {
    var a = map.abyss, cx = (a[0] + 0.5) * TS - camX, cy = (a[1] - 0.6) * TS - camY, R = TS * 2.6, t = now / 1000;
    var gr = ctx.createRadialGradient(cx, cy, R * 0.1, cx, cy, R * 1.35);
    gr.addColorStop(0, 'rgba(255,60,160,.35)'); gr.addColorStop(0.5, 'rgba(120,20,160,.22)'); gr.addColorStop(1, 'rgba(60,0,60,0)');
    ctx.fillStyle = gr; ctx.beginPath(); ctx.arc(cx, cy, R * 1.35, 0, Math.PI * 2); ctx.fill();
    ctx.save(); ctx.translate(cx, cy); ctx.scale(1, 1.15);
    ctx.fillStyle = '#0a0410'; ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.fill();
    for (var k = 0; k < 4; k++) { // 소용돌이 팔
      ctx.strokeStyle = ['#6a1aa8', '#c82a9a', '#3a0a6a', '#ff5ad8'][k]; ctx.lineWidth = Math.max(1, TS * (k === 3 ? 0.06 : 0.16)); ctx.beginPath();
      for (var q = 0; q <= 40; q++) { var p = q / 40, ang = k * Math.PI / 2 - t * 1.6 + p * 5.2, rr = R * (0.95 - p * 0.85); if (q) ctx.lineTo(Math.cos(ang) * rr, Math.sin(ang) * rr); else ctx.moveTo(Math.cos(ang) * rr, Math.sin(ang) * rr); }
      ctx.stroke();
    }
    var core = ctx.createRadialGradient(0, 0, 0, 0, 0, R * 0.35); core.addColorStop(0, 'rgba(0,0,0,1)'); core.addColorStop(0.7, 'rgba(30,0,40,.9)'); core.addColorStop(1, 'rgba(30,0,40,0)');
    ctx.fillStyle = core; ctx.beginPath(); ctx.arc(0, 0, R * 0.35, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = 'rgba(255,90,40,' + (0.65 + 0.3 * Math.sin(t * 3)) + ')'; ctx.lineWidth = Math.max(1, TS * 0.12); ctx.beginPath(); ctx.arc(0, 0, R, 0, Math.PI * 2); ctx.stroke(); // 붉은 테
    ctx.strokeStyle = 'rgba(255,200,120,.5)'; ctx.lineWidth = Math.max(1, TS * 0.04); ctx.beginPath(); ctx.arc(0, 0, R * 1.04, 0, Math.PI * 2); ctx.stroke();
    ctx.restore();
    for (var i = 0; i < 14; i++) { // 빨려 드는 불티
      var ph = ((t * 0.45 + i / 14) % 1), ang2 = i * 2.4 + t * 0.8, rr2 = R * (2.1 - ph * 1.9);
      fxDot(cx + Math.cos(ang2) * rr2, cy + Math.sin(ang2) * rr2 * 1.1, Math.max(1, TS * 0.07 * (1 - ph * 0.5)), i % 3 ? '#ff7ad8' : '#ffb04a', Math.min(1, ph * 2) * (1 - ph * 0.3));
    }
  }
  /** [2026-11-17] 보로네오 장판 위 그림 -- 9 심판의 빛: 하늘에서 내리꽂히는 금빛 기둥(예고 땐 가는 줄, 터질 때 굵게), 10 천사의 일격: 천사가 내려와 창을 꽂고 위로 사라진다. */
  var angelImgs = null;
  function angelImg(f) {
    if (!angelImgs) angelImgs = [0, 1].map(function (q) {
      var c = mk(T, T), g = c.getContext('2d');
      px(g, '#ffe680', 4, 0, 8, 1); px(g, '#fff6c0', 5, 0, 6, 1); // 후광
      px(g, '#f4f0e4', 0, q ? 3 : 5, 4, 5); px(g, '#f4f0e4', 12, q ? 3 : 5, 4, 5); px(g, '#c0b8a8', 1, q ? 7 : 9, 3, 2); px(g, '#c0b8a8', 12, q ? 7 : 9, 3, 2); // 날개
      px(g, '#f0d0b0', 6, 2, 4, 3); px(g, '#e8c050', 6, 2, 4, 1); px(g, '#3a6ad8', 7, 3, 1, 1); px(g, '#3a6ad8', 9, 3, 1, 1); // 얼굴
      px(g, '#ffffff', 5, 5, 6, 7); px(g, '#d8dce8', 5, 10, 6, 3); px(g, '#e8c050', 5, 7, 6, 1); // 흰 옷 + 금띠
      px(g, '#a8802a', 12, 1, 1, 13); px(g, '#eef2ff', 11, 12, 3, 3); px(g, '#ffffff', 12, 14, 1, 2); // 창
      return outline(c, '#5a5040');
    });
    return angelImgs[f];
  }
  function zoneHolyDraw(k, zx, zy, zs, left, warn, TS, now) {
    var cx = zx + zs / 2, base = zy + zs * 0.75;
    if (k === 9) {
      var a = left > 0 ? 0.25 + 0.35 * (1 - left / warn) : Math.max(0, 1 + left / 600), wdt = left > 0 ? zs * 0.12 : zs * 0.7;
      var gr = ctx.createLinearGradient(0, base - TS * 6, 0, base); gr.addColorStop(0, 'rgba(255,250,210,0)'); gr.addColorStop(1, 'rgba(255,240,160,' + a + ')');
      ctx.fillStyle = gr; ctx.fillRect(cx - wdt / 2, base - TS * 6, wdt, TS * 6);
      if (left <= 0) { ctx.fillStyle = 'rgba(255,255,240,' + a * 0.8 + ')'; ctx.beginPath(); ctx.ellipse(cx, base, zs * 0.55, zs * 0.22, 0, 0, Math.PI * 2); ctx.fill(); }
      return;
    }
    var sz = TS * 1.3, im = angelImg(Math.floor(now / 160) % 2), y, al;
    if (left > 0) { var q = 1 - left / warn; y = base - sz - (1 - q) * TS * 3; al = 0.35 + 0.65 * q; }
    else if (left > -600) { var q2 = -left / 600; y = base - sz - q2 * TS * 2.5; al = 1 - q2; if (q2 < 0.3) { ctx.fillStyle = 'rgba(220,235,255,' + (0.7 - q2 * 2) + ')'; ctx.beginPath(); ctx.arc(cx, base, zs * 0.5, 0, Math.PI * 2); ctx.fill(); } }
    else return;
    ctx.globalAlpha = Math.max(0, al); ctx.drawImage(im, Math.round(cx - sz / 2), Math.round(y), sz, sz); ctx.globalAlpha = 1;
  }
  function lootTarget(tg, now) {
    if (!tg || tg.none) return null;
    if (tg.me) { var c = curPos(now); return { x: c.x, y: c.y }; }
    var o = others[tg.id];
    if (!o) return null;
    var op = otherPos(o, now);
    return { x: op.x, y: op.y };
  }
  function lootDraw(now, TS, camX, camY) {
    if (!loot.length && !lootPops.length) return;
    var keep = [];
    for (var i = 0; i < loot.length; i++) {
      var p = loot[i], e = now - p.t0;
      if (e < 0) { keep.push(p); continue; }
      var x = p.tx, y = p.ty, z = 0, sc = 1, al = 1, resting = false;
      if (e < LOOT_FLY) { var q = e / LOOT_FLY; x = p.sx + (p.tx - p.sx) * q; y = p.sy + (p.ty - p.sy) * q; z = Math.sin(q * Math.PI) * p.up; sc = 0.6 + 0.4 * q; }
      else if (e < LOOT_FLY + p.rest) { var b = e - LOOT_FLY; z = b < 220 ? Math.sin(b / 220 * Math.PI) * 0.3 : 0; resting = true; } // 바닥에 닿아 한 번 튄다
      else {
        var q2 = (e - LOOT_FLY - p.rest) / LOOT_IN, tp = lootTarget(p.tg, now);
        if (q2 >= 1) { if (tp) lootPops.push({ x: tp.x, y: tp.y, t0: now, g: p.g }); continue; }
        if (!tp) al = 1 - q2;
        else { var k = q2 * q2; x = p.tx + (tp.x - p.tx) * k; y = p.ty + (tp.y - 0.35 - p.ty) * k; z = Math.sin(q2 * Math.PI) * 0.45; sc = 1 - 0.4 * q2; }
      }
      var cx = x * TS - camX + TS / 2, cy = y * TS - camY + TS / 2, s = TS * 0.62 * sc;
      if (cx > -TS && cy > -TS * 3 && cx < el.cv.width + TS && cy < el.cv.height + TS * 3) {
        ctx.fillStyle = 'rgba(0,0,0,' + (0.28 * al) + ')'; ctx.beginPath(); ctx.ellipse(cx, cy + TS * 0.26, s * 0.34, s * 0.13, 0, 0, Math.PI * 2); ctx.fill();
        ctx.globalAlpha = al;
        if (p.g === 'heal') { fxDot(cx, cy - z * TS, s * 0.3, '#7dffb0', al); fxDot(cx, cy - z * TS, s * 0.14, '#ffffff', al); }
        else ctx.drawImage(lootImg(p.g), Math.round(cx - s / 2), Math.round(cy - s / 2 - z * TS), s, s);
        if (resting && (i * 7 + Math.floor(now / 140)) % 11 === 0) fxDot(cx + s * 0.25, cy - s * 0.3, 1.4 * scale, '#ffffff', 0.9); // 놓여 있는 동안 반짝
        ctx.globalAlpha = 1;
      }
      keep.push(p);
    }
    loot = keep;
    lootPops = lootPops.filter(function (o) { return now - o.t0 < 260; });
    lootPops.forEach(function (o) { // 빨려 들어간 자리 -- 작은 빛 고리
      if (now < o.t0) return;
      var q = (now - o.t0) / 260, cx = o.x * TS - camX + TS / 2, cy = o.y * TS - camY + TS * 0.3;
      for (var i = 0; i < 5; i++) { var a = i * 1.2566 + o.t0 % 6; fxDot(cx + Math.cos(a) * TS * 0.45 * q, cy + Math.sin(a) * TS * 0.3 * q, (1 - q) * 1.8 * scale, o.g === 'heal' ? '#7dffb0' : o.g === 'hurt' ? '#ff6a5a' : o.g === 'blink' ? '#c98aff' : '#ffe9a8', 1 - q); }
      ctx.globalAlpha = 1;
    });
  }
  /** 보스가 막 쓰러졌다(위치 알림 bossFx: {at, x, y, f 차지하는 칸, n 함께 싸운 사람 수, me 내가 그중 하나, us 화면에 있는 다른 사람들}) -- 사람이 많을수록 더 많이. */
  var bossFxSeen = 0;
  function bossLoot(f) {
    var tgs = [];
    if (f.me) tgs.push({ me: 1 });
    (f.us || []).forEach(function (id) { tgs.push({ id: id }); });
    if (!tgs.length) tgs.push({ none: 1 });
    var fo = f.f || 2, gs = f.g && f.g.length ? f.g : null, per = gs ? gs.length : 6; // [2026-11-17] 실제로 떨어진 개수(한 사람 몫) x 받는 사람 수
    if (!per) return;
    var all = []; tgs.forEach(function (tg) { for (var i = 0; i < per; i++) all.push({ g: gs ? gs[i] : LOOT_GRADES[i % LOOT_GRADES.length], tg: tg }); });
    if (all.length > 130) all.length = 130;
    lootBurst(f.x + (fo - 1) / 2, f.y + (fo - 1) / 2, all.map(function (o) { return o.g; }), all.length, all.map(function (o) { return o.tg; }), 1.5 + fo * 0.75, 0);
    flashT0 = performance.now();
  }
  // 탑 방 -- 한가운데 놓이는 것(보물방 T 상자 / 증표방 P 제단 / 행운의 샘 L)과 함정방(X) 가시
  var twFx = null, twArt = null, spikePend = 0, spikeTotal = 0, spikeTimer = 0, spikeHitAt = {}, TW_SPIKE_MAX = 14;
  function twRoomType() { var r = st && st.twRun; return map && map.tower && r && r.total && map.w === 23 ? r.type : ''; }
  function twDecorCell() { return 'TPL'.indexOf(twRoomType() || '-') >= 0 ? [Math.floor(map.w / 2), Math.floor(map.h / 2)] : null; }
  function twSpikeCell(x, y) { return twRoomType() === 'X' && y >= 2 && y <= map.h - 3 && (x === 6 || x === 7 || x === 10 || x === 11 || x === 14 || x === 15) && WALK.indexOf(cellAt(x, y)) >= 0; }
  function twArtBuild() {
    var A = twArt = {}, art = function (fn) { var c = mk(T, T); fn(c.getContext('2d')); return c; };
    var chest = function (open) {
      return outline(art(function (g) {
        px(g, '#6a3f1e', 2, 7, 12, 7); px(g, '#8a5a32', 3, 8, 10, 5); px(g, '#5a3418', 3, 12, 10, 1);
        px(g, '#d9b44a', 2, 7, 12, 1); px(g, '#d9b44a', 2, 13, 12, 1); px(g, '#d9b44a', 7, 7, 2, 7);
        if (open) { px(g, '#a8743c', 2, 1, 12, 3); px(g, '#c89250', 3, 1, 10, 1); px(g, '#d9b44a', 7, 1, 2, 3); px(g, '#3a2210', 3, 4, 10, 3); px(g, '#ffd24a', 4, 5, 8, 2); px(g, '#fff6c0', 6, 4, 4, 2); }
        else { px(g, '#a8743c', 2, 3, 12, 4); px(g, '#c89250', 3, 3, 10, 1); px(g, '#d9b44a', 7, 3, 2, 4); px(g, '#f6e3a0', 7, 8, 2, 2); px(g, '#7a5a1a', 7, 9, 2, 1); }
      }), '#140d0a');
    };
    A.chest = [chest(false), chest(true)];
    A.altar = outline(art(function (g) { // 증표 제단 -- 돌 받침 위에 떠 있는 푸른 증표
      px(g, '#5a6080', 3, 12, 10, 2); px(g, '#8088a8', 4, 10, 8, 2); px(g, '#a8b0cc', 4, 10, 8, 1); px(g, '#d9b44a', 3, 12, 10, 1);
      px(g, '#2c6fd0', 6, 3, 4, 6); px(g, '#7ad8ff', 7, 2, 2, 7); px(g, '#e8fbff', 7, 3, 1, 4); px(g, '#d9b44a', 6, 8, 4, 1);
    }), '#140d0a');
    A.spring = [0, 1].map(function (f) { // 행운의 샘 -- 돌 수반 + 솟는 물
      return outline(art(function (g) {
        px(g, '#5a6080', 2, 8, 12, 6); px(g, '#8088a8', 2, 7, 12, 2); px(g, '#a8b0cc', 3, 7, 10, 1);
        px(g, '#2a8ad8', 3, 8, 10, 4); px(g, '#7ad8ff', 4, 9, 8, 2); px(g, '#e8fbff', f ? 5 : 9, 9, 2, 1);
        px(g, '#9fe8ff', 7, f ? 2 : 3, 2, 6); px(g, '#ffffff', 7, f ? 1 : 2, 2, 2); px(g, '#c8f2ff', f ? 5 : 10, 5, 1, 2); px(g, '#c8f2ff', f ? 10 : 5, 4, 1, 2);
      }), '#140d0a');
    });
    A.spike = [0, 1].map(function (hit) { // 가시 -- 0 = 솟아 있는 가시 넷, 1 = 밟은 순간(더 길고 끝이 붉다)
      return art(function (g) {
        px(g, 'rgba(120,16,28,.22)', 0, 0, T, T);
        [[3, 4], [11, 4], [3, 12], [11, 12]].forEach(function (s) {
          var h = hit ? 6 : 4;
          px(g, '#12162a', s[0] - 2, s[1], 5, 2); // 구멍
          px(g, '#5a5f6c', s[0] - 1, s[1] - 2, 3, 3); px(g, '#8a8f9c', s[0] - 1, s[1] - 2, 2, 3);
          px(g, '#c8ccd6', s[0], s[1] - h + 1, 1, h - 2); px(g, '#8a8f9c', s[0] + 1, s[1] - h + 2, 1, h - 3);
          px(g, hit ? '#ff5a4a' : '#f4f6fa', s[0], s[1] - h, 1, 1 + hit);
        });
      });
    });
  }
  /** 방에 막 들어섰다(d = 문을 지난 응답) -- 받은 것(twDrops)이 있으면 조금 뒤 한가운데에서 쏟아져 나온다. */
  function twRoomEnter(d) {
    twFx = null; spikePend = 0; spikeTotal = 0; spikeHitAt = {};
    var ty = twRoomType();
    if (!ty || !twDecorCell()) return;
    if (d && d.twDrops && d.twDrops.length) twFx = { t0: performance.now() + 650, drops: d.twDrops, type: ty };
    else if (d && ty === 'L') twFx = { t0: performance.now() + 650, drops: [], type: ty };
  }
  /** 바닥 층(몬스터/캐릭터보다 먼저): 가시 띠, 한가운데 상자/제단/샘. */
  function twRoomDraw(now, TS, camX, camY) {
    var ty = twRoomType();
    if (!ty) return;
    if (!twArt) twArtBuild();
    if (ty === 'X') {
      for (var y = 2; y <= map.h - 3; y++) for (var x = 6; x <= 15; x++) {
        if (!twSpikeCell(x, y)) continue;
        var ht = spikeHitAt[x + ',' + y], hit = ht && now - ht < 320;
        ctx.drawImage(twArt.spike[hit ? 1 : 0], Math.round(x * TS - camX), Math.round(y * TS - camY - (hit ? 0 : Math.max(0, Math.sin(now / 520 + x * 1.7)) * scale)), TS, TS);
        if (!hit && (x * 5 + y * 3 + Math.floor(now / 170)) % 23 === 0) fxDot(x * TS - camX + (x % 2 ? 3.5 : 11.5) * scale, y * TS - camY + 1.5 * scale, 1.1 * scale, '#ffffff', 0.9); // 가시 끝 반짝
      }
      ctx.globalAlpha = 1;
      return;
    }
    var dc = twDecorCell();
    if (!dc) return;
    var dx = Math.round(dc[0] * TS - camX), dy = Math.round(dc[1] * TS - camY), isOpen = !twFx || now >= twFx.t0, gl = 0.5 + 0.5 * Math.sin(now / 300);
    ctx.fillStyle = 'rgba(0,0,0,.28)'; ctx.beginPath(); ctx.ellipse(dx + TS / 2, dy + TS * 0.9, TS * 0.46, TS * 0.14, 0, 0, Math.PI * 2); ctx.fill();
    if (ty === 'T') {
      if (isOpen) { ctx.fillStyle = 'rgba(255,214,90,' + (0.16 + 0.1 * gl) + ')'; ctx.beginPath(); ctx.arc(dx + TS / 2, dy + TS * 0.35, TS * (0.75 + 0.1 * gl), 0, Math.PI * 2); ctx.fill(); }
      ctx.drawImage(twArt.chest[isOpen ? 1 : 0], dx - Math.round(TS * 0.15), dy - Math.round(TS * 0.3), Math.round(TS * 1.3), Math.round(TS * 1.3));
    } else if (ty === 'P') {
      ctx.fillStyle = 'rgba(122,216,255,' + (0.14 + 0.12 * gl) + ')'; ctx.beginPath(); ctx.arc(dx + TS / 2, dy + TS * 0.3, TS * (0.7 + 0.12 * gl), 0, Math.PI * 2); ctx.fill();
      ctx.drawImage(twArt.altar, dx - Math.round(TS * 0.15), dy - Math.round(TS * 0.3 + gl * 1.5 * scale), Math.round(TS * 1.3), Math.round(TS * 1.3));
    } else {
      ctx.fillStyle = 'rgba(125,255,176,' + (0.12 + 0.1 * gl) + ')'; ctx.beginPath(); ctx.arc(dx + TS / 2, dy + TS * 0.45, TS * (0.85 + 0.1 * gl), 0, Math.PI * 2); ctx.fill();
      ctx.drawImage(twArt.spring[Math.floor(now / 260) % 2], dx - Math.round(TS * 0.15), dy - Math.round(TS * 0.3), Math.round(TS * 1.3), Math.round(TS * 1.3));
      for (var i = 0; i < 4; i++) { var sq = ((now / 1300 + i * 0.25) % 1); fxDot(dx + TS / 2 + Math.sin(i * 2.3 + now / 700) * TS * 0.5, dy + TS * 0.5 - sq * TS * 1.1, 1.3 * scale, '#b8ffd8', 1 - sq); }
      ctx.globalAlpha = 1;
    }
    if (twFx && !twFx.done && now >= twFx.t0) { // 열리는 순간 -- 받은 것들이 쏟아져 나와 나에게
      twFx.done = true;
      var gs = [];
      twFx.drops.forEach(function (w) { for (var k = 0; k < Math.max(1, Math.min(40, w.qty | 0)); k++) gs.push(w.grade); }); // [2026-11-17] 받은 개수 그대로
      if (ty === 'L') { lootBurst(dc[0], dc[1], ['heal'].concat(gs), 10, [{ me: 1 }], 1.5, 0); floats.push({ x: dc[0], y: dc[1] - 1, text: '🍀 체력이 가득 찼어요', color: '#7dffb0', big: true, t0: now }); }
      else if (gs.length) {
        flashT0 = now;
        lootBurst(dc[0], dc[1], gs, Math.min(60, gs.length), [{ me: 1 }], ty === 'T' ? 2.2 : 1.5, 0);
        twFx.drops.forEach(function (w, i) { var gi = lootImg(w.grade); floats.push({ x: dc[0], y: dc[1] - 1 - i * 0.55, text: '+' + w.qty + ' ' + w.name, color: (ORE_COL[w.grade] || ORE_COL[1])[1], icon: gi, t0: now + LOOT_FLY + 1100 + i * 160 }); });
      }
    }
  }
  /** 함정방 가시를 밟았다 -- 그 칸 가시가 솟고 체력이 깎인다(밟은 수를 모아 서버에 알린다). */
  function twSpikeStep(now) {
    if (spikeTotal >= TW_SPIKE_MAX) return;
    spikeTotal++; spikePend++;
    spikeHitAt[P.x + ',' + P.y] = now;
    var c = st && st.combat, dmg = c && c.hpMax ? Math.max(1, Math.round(c.hpMax * 0.025)) : 0;
    floats.push({ x: P.x, y: P.y - 0.5, text: dmg ? '-' + dmg.toLocaleString() : '가시!', color: '#ff6a5a', t0: now });
    for (var i = 0; i < 4; i++) lootPops.push({ x: P.x + (i % 2 ? 0.2 : -0.2), y: P.y + 0.3, t0: now + i * 30, g: 'hurt' });
    if (!spikeTimer) spikeTimer = setTimeout(twSpikeFlush, 420);
  }
  function twSpikeFlush() {
    spikeTimer = 0;
    var n = spikePend;
    spikePend = 0;
    if (!n || !opened) return;
    api('/api/tower-mine-tw?action=spike&n=' + n).then(function (d) { if (d && d.combat) { setCombat(d.combat); renderHp(); } if (spikePend && !spikeTimer) spikeTimer = setTimeout(twSpikeFlush, 420); }).catch(function () { });
  }
  // [2026-11-16] "몬스터를 잡지 않으면 문이 아예 막히게(지금은 화면이 어두워졌다가 다시 됨)" -- 전투방(st.twRun.type C)에서 방을 지키는 몬스터(st.twRun.mob)가
  //  살아 있는 동안 오른쪽 문은 쇠창살로 닫혀 있고(R.doorShut) 밟아도 서버에 묻지 않는다(화면이 어두워지지 않는다). 쓰러뜨리면 그 자리에서 열린다.
  var twDoorWas = false;
  function twLocked() {
    var r = st && st.twRun;
    if (!map || !map.tower || !r || r.type !== 'C' || r.mob == null) return false;
    if (!mobDeathReady) return true; // 아직 몬스터 목록을 못 받았다
    var s = mobSt[r.mob];
    return !!s && !(s.du > nowSrv()) && s.hp > 0;
  }
  var TW_ROOM = { C: '⚔️ 전투방', T: '📦 보물방', P: '🗼 증표방', X: '⚠️ 함정방', L: '🍀 행운의 샘', U: '🪜 계단방' };
  /** [2026-11-15] 탑 띠 -- 층 + 지금 방(st.twRun: floor/step/total/type) + [층 이동]. 주사위는 없앴다: 오른쪽 문을 지나는 순간 다음 방이 정해진다. */
  function twRender() {
    if (!el.ov) return;
    if (!twBar) { twBar = document.createElement('div'); twBar.className = 'mine-tw-bar'; el.ov.appendChild(twBar); }
    if (!map || !map.tower) { twBar.style.display = 'none'; return; }
    var r = (st && st.twRun) || null, h = '🗼 ' + map.tower + '층';
    if (r && r.total) h += ' · 방 ' + Math.min(r.step | 0, r.total) + '/' + r.total + (TW_ROOM[r.type] ? ' · ' + TW_ROOM[r.type] : '') + (r.type === 'U' ? ' · 🔼 위 / 🔽 아래' : ' · 오른쪽 문 ▶');
    var twl = st && st.twLeft != null ? st.twLeft : r && r.bossLeft != null ? r.bossLeft : null; // [2026-11-19] "수문장 반복 처치 보상 3번 중 몇 번 남았는지 항상"
    if (twl != null) h += ' · 👑 수문장 반복 보상 <b style="color:' + (twl > 0 ? '#ffe14d' : '#ff8a8a') + '">' + twl + '/' + ((r && r.bossMax) || 3) + '</b> 남음';
    h += ' <button type="button" class="mine-mini-btn" onclick="TowerMine.twFloors()">🪜 층 이동</button>';
    if (twBar._h !== h) { twBar.innerHTML = h; twBar._h = h; }
    twBar.style.display = '';
  }
  function twRoll() {
    if (!map || !map.board || twBusy || busy || P.moving) return;
    twBusy = true; twRender();
    api('/api/tower-mine-tw?action=roll').then(function (d) {
      if (!d || d.tooFast) { twBusy = false; twRender(); return; }
      if (!d.ok) { twBusy = false; twRender(); msg(d.message || '굴리지 못했어요.', 2200); return; }
      floats.push({ x: P.x, y: P.y - 1.2, text: '🎲 ' + d.roll, color: '#ffe14d', big: true, t0: performance.now() });
      var path = (d.path || []).slice(), step = function () {
        if (!path.length) {
          twBusy = false;
          if (d.combat) { st.combat = d.combat; renderHp(); }
          if (d.ores) st.ores = d.ores;
          if (d.max && twInfo) twInfo.max = d.max;
          var tl = d.tile;
          if (tl === 'U' || tl === 'T' || tl === 'L') flashT0 = performance.now();
          msg(d.message || '', tl === 'C' ? 2600 : 3600);
          lastSaved = ''; saveBeacon(false);
          twRender();
          return;
        }
        var c = path.shift();
        P.dir = c[0] > P.x ? 'right' : c[0] < P.x ? 'left' : c[1] > P.y ? 'down' : 'up';
        P.fx = P.x; P.fy = P.y; P.tx = c[0]; P.ty = c[1]; P.t0 = performance.now(); P.moving = true; P.step = P.step === 1 ? 2 : 1;
        setTimeout(step, stepMs() + 60);
      };
      step();
    }).catch(function () { twBusy = false; twRender(); msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function twFloors() {
    setAuto(false);
    twInfo = null;
    openSmith({ name: '탑 관리인', line: '', kind: 'towergate' }, 'tower');
    api('/api/tower-mine-tw?action=info').then(function (d) { twInfo = d; if (smithOpen && smithView === 'tower') renderSmith(smithNpc); }).catch(function () {});
  }
  function towerHtml(n) {
    var d = twInfo, h = npcHead('🗼 ' + (n && n.name ? n.name : '탑'), false) + (n && n.line ? talkBlock(n, n.line) : '');
    if (!d) return h + '<div class="mine-smith-say">...</div>';
    var lo = 1, max = d.max | 0, top = d.top || max, open = d.open || max, bn = d.bossNames || [], daily = d.bossDaily || 3, done = Math.min(daily, d.bossDone | 0);
    var bossNm = function (f) { return bn[(f - 9) / 10] || '수문장'; };
    h += '<div class="mine-smith-say">갈 수 있는 층: <b>' + lo + ' ~ ' + max + '층</b> <small>(지난 층도 다시 갈 수 있어요)</small>'
        + ' · 👑 수문장 반복 보상(오늘) <b>' + done + '/' + daily + '</b>'
        + '<br><small>⚔️ 탑은 혼자 오르는 곳이에요(다른 사람과 따로, 층에 들어갈 때마다 처음부터). 몬스터가 강하니 체력을 봐 가며 굴리세요.</small>'
        + '<br><small>방을 지나 오른쪽 문으로 나아가면 계단방이 나와요(위 계단 = 다음 층, 왼쪽 문 = 탑 밖). 9층마다 수문장(하루 ' + daily + '번까지), 10층마다 마을. 몬스터를 쓰러뜨리면 탑의 증표를 받아요(증표 상인에게서 동료 장비 뽑기·보석 사기).</small></div>';
    var btns = '';
    for (var f = lo; f <= max; f++) {
      var lb = f % 10 === 0 ? '🏘️ ' + f + '층 마을' : f % 10 === 9 ? '👑 ' + f + '층 ' + escAttr(bossNm(f)) : '🗼 ' + f + '층';
      btns += '<button type="button" class="mine-mini-btn"' + (map && map.tower === f ? ' style="outline:2px solid #ffd27a"' : '') + ' onclick="TowerMine.twGo(' + f + ')">' + lb + '</button>';
    }
    h += '<div style="display:flex;flex-wrap:wrap;gap:4px;margin:6px 0">' + btns + '</div>';
    if (map && map.tower) h += '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.twExit()">🚪 탑에서 나가기 (탑 입구)</button></div>';
    // [2026-11-15] 탑 전체(100층) -- 구역마다 수문장(시즌5 보스 이름). 열린 층(open) 위는 준비 중.
    var plan = '';
    for (var bf = 9; bf <= top; bf += 10) {
      var stt = bf > open ? '🚧 준비 중' : (d.block | 0) >= bf ? '✅ 쓰러뜨림' : max >= bf ? '⚔️ 도전할 수 있어요' : '🔒 ' + (bf - 8) + '~' + (bf - 1) + '층을 올라야 해요';
      plan += '<div class="mine-smith-row"><span class="nm"' + (bf > open ? ' style="opacity:.55"' : '') + '>👑 ' + bf + '층 ' + escAttr(bossNm(bf)) + '<small>' + stt + '</small></span></div>';
    }
    h += '<div class="mine-smith-say" style="border:1px solid #7ab8ff">🗼 <b>탑 ' + top + '층</b> <small>지금은 ' + open + '층까지 열려 있어요.</small>' + plan + '</div>';
    return h;
  }
  // [2026-11-15] 증표 상인 = 동료(용병) 장비 뽑기(시즌5처럼 장비/악세, 초급~최상급) + 용병 관리 장비 탭에서 끼우기
  var tokenTab = 'gear'; // [2026-11-15] gear = 동료 장비 뽑기 / gem = 보석 사기
  var tokenShop = null, tokenBusy = false, mgInv = null, mgLoading = false, MG_SLOTS = [['W', '⚔️ 무기'], ['H', '⛑️ 투구'], ['A', '🛡️ 갑옷'], ['N', '📿 목걸이'], ['R', '💍 반지'], ['B', '📿 팔찌']];
  /** [2026-11-17] 월드보스 게시판 -- 지금 상태/다음 출현, 이번 맵 주기(현재 보스)와 지난 주기(이전 보스): 남은 체력, 처치 시각, 기여도(몫 %·대략 피해), 받은 보상. */
  /** [2026-11-17] 우편함(우체부) -- 받을 우편 목록과 받기. */
  var mailData = null, mailBusy = false, mailNoticeShown = false;
  function mailGo(act, id) {
    if (mailBusy) return;
    mailBusy = true;
    api('/api/tower-mine-token?action=' + act + (id ? '&code=' + encodeURIComponent(id) + '&g=0' : '')).then(function (d) {
      mailBusy = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      if (d.mails) { mailData = d.mails; if (st) st.mailN = d.mails.filter(function (m) { return !m.got; }).length; }
      if (act === 'mailGet' && d.message) { msg(d.message, 3600); if (d.ok) { flashT0 = performance.now(); var gs = []; (mailData || []).forEach(function (m) { if (m.id === id) m.items.forEach(function (it) { for (var k = 0; k < Math.min(10, it.qty); k++) gs.push(it.grade); }); }); if (gs.length && smithNpc) lootBurst(smithNpc.x, smithNpc.y, gs, gs.length, [{ me: 1 }], 1.6, 0); } }
      if (smithOpen && smithView === 'mail') renderSmith(smithNpc);
    }).catch(function () { mailBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function mailHtml(n) {
    var h = npcHead('📮 ' + n.name, false) + talkBlock(n, n.line);
    if (!mailData) return h + '<div class="mine-smith-say">우편을 찾는 중...</div>';
    if (!mailData.length) return h + '<div class="mine-smith-say">받을 우편이 없어요.</div>';
    mailData.forEach(function (m) {
      h += '<div class="mine-smith-say" style="border:1px solid ' + (m.got ? '#5a5468' : '#ffd27a') + '"><b>' + escAttr(m.title) + '</b><br><small>' + escAttr(m.body) + '</small><div style="margin:4px 0">'
        + m.items.map(function (it) { return '<img src="' + oreIcon(it.grade) + '" alt="" style="width:16px;vertical-align:middle"> ' + escAttr(it.name) + ' ' + it.qty; }).join(' · ') + '</div>'
        + (m.got ? '<button type="button" disabled>받았어요</button>' : '<button type="button" class="mine-mini-btn" onclick="TowerMine.mailGet(\'' + m.id + '\')">📮 받기</button>') + '</div>';
    });
    return h;
  }
  var wbBoard = null;
  function wbWhen(ms) { var d = new Date(ms), p = function (v) { return (v < 10 ? '0' : '') + v; }, t = new Date(); return (d.toDateString() === t.toDateString() ? '오늘' : (d.getMonth() + 1) + '/' + d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()); }
  function wbBoardHtml(n) {
    var d = wbBoard, h = npcHead('📜 ' + n.name, false);
    if (!d) return h + '<div class="mine-smith-say">게시판을 읽는 중...</div>';
    var lv = d.live || {}, now = nowSrv();
    h += '<div class="mine-smith-say" style="border:1px solid #ff8a5a"><b>🐉 심연의 거룡</b> · ' + (lv.closed ? '아직 나타나지 않아요' : lv.on ? '<b style="color:#ff6a5a">지금 동쪽 성곽에 출현 중!</b> (' + Math.max(1, Math.ceil((lv.end - now) / 60000)) + '분 남음)' : '다음 출현 <b>' + wbWhen(lv.next) + '</b>' + (lv.nextCycle ? ' <small>(이번 맵 주기엔 이미 쓰러짐 -- 맵 초기화 뒤)</small>' : '')) + '</div>';
    function rwList(r) { // [2026-11-17] "보상 설명 말고 사람별로" -- 그 사람이 실제로 받은 것
      if (!r) return '';
      var a = ['⭐ 별빛석 ' + d.star, '🔮 칠요석 ' + d.seven];
      if (r < 0) return a.join(' · ') + ' (은하수 조각·용의 반지는 기록 전)';
      var g = Math.floor(r / 10) % 10; if (g) a.push('🌌 은하수 조각 ' + g); if (r >= 100) a.push('💍 용의 반지');
      return a.join(' · ');
    }
    function rwTxt(r) { if (!r) return ''; if (r < 0) return '보상 받음'; var s = '⭐' + d.star + ' 🔮' + d.seven, g = Math.floor(r / 10) % 10; if (g) s += ' 🌌' + g; if (r >= 100) s += ' 💍용의 반지'; return s; }
    [['cur', '현재 보스'], ['prev', '이전 보스']].forEach(function (k) {
      var b = d[k[0]];
      if (!b) return;
      var hasHp = b.hp >= 0, pct = hasHp && b.max ? Math.round(b.hp * 1000 / b.max) / 10 : null;
      h += '<div class="mine-smith-say"><b>' + (k[0] === 'cur' ? '🔥 ' : '🕰️ ') + k[1] + '</b> <small>맵 주기 ' + b.cycle + ' (' + wbWhen(b.from) + ' ~)</small><br>'
        + (b.killed ? '✅ 처치 -- <b>' + (b.killedAt ? wbWhen(b.killedAt) : '시각 기록 없음') + '</b>' : hasHp ? (b.hp < b.max ? '⚔️ 남은 체력 ' + pct + '%' : '💤 아직 아무도 상처 내지 못함') + (k[0] === 'prev' ? ' -- 쓰러뜨리지 못함' : '') : '기록 없음');
      if (hasHp && b.max) h += '<div class="mine-bar hp" style="display:block;width:100%;margin:4px 0"><i style="width:' + (b.killed ? 0 : Math.min(100, pct)) + '%;background:#c8381a"></i></div>';
      var ps = b.players || [];
      if (!ps.length) h += '<small>함께 싸운 사람이 아직 없어요.</small>';
      ps.forEach(function (p, i) {
        var rwl = p.rw ? '🎁 ' + rwList(p.rw) : !b.killed ? '' : p.ok ? '🎁 ⭐ 별빛석 ' + d.star + ' · 🔮 칠요석 ' + d.seven : '보상 없음 (' + b.minShare + ' 몫 미달)';
        h += '<div class="mine-smith-row"><span class="nm">' + (i < 3 ? ['🥇', '🥈', '🥉'][i] : (i + 1) + '.') + ' ' + escAttr(p.nick) + ' <b>' + p.pct + '%</b><small>피해 약 ' + fmtN(p.dmg) + (!b.killed && !p.ok ? ' · 아직 보상 기준(' + b.minShare + ' 몫) 전' : '') + '</small>' + (rwl ? '<small style="color:#ffe9a8">' + rwl + '</small>' : '') + '</span></div>';
      });
      h += '</div>';
    });
    return h;
  }
  /** [2026-11-17] 최후의 방어선 -- 악마 광석 정제사: 조각 하나마다 10%로 미스릴 원석. */
  // [2026-11-20] 최후의 방어선 태초의 보석 세공사 -- 재료(마계의 정수·거룡의 심장 조각·악마의 광석 조각·광석·세공 보석)로 태초의 보석(한 사람에 하나), 쓰면 검기 폭 3칸(한 번만)
  // [2026-11-20] 연금술사(왕도·요새 도시) -- 구리 -> 철 -> 은 -> 금, 10:1. 1회 / 100회 / 전부
  var alchemyInfo = null, alchemyBusy = false;
  function alchemyGo(act, from, n) {
    if (alchemyBusy) return;
    alchemyBusy = true;
    api('/api/tower-mine-token?action=' + act + '&code=' + (from | 0) + '&g=' + (n | 0)).then(function (d) {
      alchemyBusy = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      alchemyInfo = d;
      if ((act === 'alchemy' || act === 'alchemyBack') && d.message) { msg(d.message, 3200); if (d.ok) { flashT0 = performance.now(); var ac = smithNpc ? [smithNpc.x, smithNpc.y] : [P.x, P.y]; try { lootBurst(ac[0], ac[1] - 1, [(from | 0) + 2], Math.min(20, d.got || 1), [{ me: 1 }], 1.2, 0); } catch (e) { } } }
      if (smithOpen && smithView === 'alchemy') renderSmith(smithNpc);
    }).catch(function () { alchemyBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function alchemyHtml(n) {
    var d = alchemyInfo || {}, q = d.have || [0, 0, 0, 0, 0], r = d.rate || 10, NM = ['구리 광석', '철광석', '은광석', '금광석', '악마의 광석 조각'], GR = [1, 2, 3, 4, 75]; // [2026-11-20] 금광석 -> 악마의 광석 조각
    var h = npcHead('⚗️ ' + n.name, false) + talkBlock(n, n.line);
    if (!alchemyInfo) return h + '<div class="mine-smith-say">⚗️ 도가니를 데우는 중...</div>';
    h += '<div class="mine-smith-say" style="border:1px solid #b8f06a"><b>⚗️ 광석 연금</b> <small>아래 광석 ' + r + '개를 녹여 한 단계 위 광석 1개로. 금광석 ' + r + '개는 악마의 광석 조각 1개로.</small>';
    var ic = function (g) { return '<img src="' + oreIcon(g) + '" alt="" style="width:16px;vertical-align:middle">'; };
    for (var i = 0; i < 4; i++) {
      var can = Math.floor(q[i] / r);
      h += '<div class="mine-smith-row"><span class="nm">' + ic(i + 1) + ' ' + NM[i] + ' ' + r + ' → ' + ic(GR[i + 1]) + ' ' + NM[i + 1] + ' 1<small>보유 ' + fmtN(q[i]) + '개 → 최대 ' + fmtN(can) + '개 · ' + NM[i + 1] + ' 보유 ' + fmtN(q[i + 1]) + '개</small></span></div>';
      [[1, '1개'], [100, '100개'], [0, '전부']].forEach(function (b) { h += '<button type="button" class="mine-mini-btn"' + (can < Math.max(1, b[0]) ? ' data-lack="1"' : '') + ' onclick="TowerMine.alchemy(' + i + ',' + b[0] + ')">' + b[1] + '</button> '; });
    }
    // [2026-11-20] 복원 -- 위 광석 1개를 풀어 한 단계 아래 광석 3~8개로(악마의 광석 조각 -> 금 -> 은 -> 철)
    var bm = d.backMin || 3, bx = d.backMax || 8;
    h += '</div><div class="mine-smith-say" style="border:1px solid #8ad8ff"><b>⚗️ 광석 복원</b> <small>위 광석 1개를 풀어 한 단계 아래 광석 ' + bm + '~' + bx + '개로 되돌려요. (철광석까지)</small>';
    for (var j = 4; j >= 2; j--) {
      var hv = q[j] | 0;
      h += '<div class="mine-smith-row"><span class="nm">' + ic(GR[j]) + ' ' + NM[j] + ' 1 → ' + ic(GR[j - 1]) + ' ' + NM[j - 1] + ' ' + bm + '~' + bx + '<small>보유 ' + fmtN(hv) + '개</small></span>';
      [[1, '1개'], [100, '100개'], [0, '전부']].forEach(function (b) { h += '<button type="button" class="mine-mini-btn"' + (hv < Math.max(1, b[0]) ? ' data-lack="1"' : '') + ' onclick="TowerMine.alchemyBack(' + j + ',' + b[0] + ')">' + b[1] + '</button> '; });
      h += '</div>';
    }
    return h + '</div>';
  }
  var primalInfo = null, primalBusy = false;
  function primalGo(act) {
    if (primalBusy) return;
    primalBusy = true;
    api('/api/tower-mine-token?action=' + act + '&code=x&g=0').then(function (d) {
      primalBusy = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      if (d.aura) st.aura = d.aura;
      primalInfo = d;
      if (act !== 'primalInfo' && d.message) { msg(d.message, 3600); if (d.ok) { flashT0 = performance.now(); if (act === 'primalUse') banner = { text: '✨ 검기 폭 3칸!', t0: performance.now(), rainbow: true }; } }
      if (smithOpen && smithView === 'primal') renderSmith(smithNpc);
    }).catch(function () { primalBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function primalHtml(n) {
    var d = primalInfo || {}, ic = '<img src="' + oreIcon(84) + '" alt="" style="width:16px;vertical-align:middle"> ';
    var h = npcHead('💎 ' + n.name, false) + talkBlock(n, n.line);
    if (!primalInfo) return h + '<div class="mine-smith-say">💎 재료를 살펴보는 중...</div>';
    if (d.wide) return h + '<div class="mine-smith-say" style="border:1px solid #fff4b0">✨ 태초의 빛이 이미 그대의 검에 깃들어 있네. 검기가 3칸 폭으로 뻗어 나가지.<br><small>태초의 보석은 한 사람에게 한 번뿐이에요.</small></div>';
    if ((d.gem | 0) > 0) return h + '<div class="mine-smith-say" style="border:1px solid #fff4b0">' + ic + '<b>태초의 보석</b>을 가지고 있군. 검에 깃들이면 검기가 <b>3칸 폭</b>으로 뻗어 나가네. <small>(한 번만 -- 보석은 사라져요)</small>'
      + '<div style="margin-top:6px"><button type="button" class="mine-mini-btn" onclick="TowerMine.primal(\'primalUse\')">✨ 검에 깃들이기</button></div></div>';
    var ok = (d.cost || []).every(function (c) { var hv = c.have; (st && st.ores || []).forEach(function (o) { if (o.grade === c.grade && o.qty != null) hv = o.qty; }); return hv >= c.need; });
    return h + '<div class="mine-smith-say" style="border:1px solid #fff4b0">' + ic + '<b>태초의 보석</b> <small>마계의 정수(정제된 마석) · 거룡의 심장 조각 · 악마의 광석 조각 · 구리~미스릴 · 세공된 보석(땅·바다)을 모두 녹여 하나로. 검에 깃들이면 검기 폭 3칸(한 사람에 한 번).</small>'
      + '<div style="margin-top:6px">' + costHtml(d.cost) + '</div>'
      + '<div style="margin-top:6px"><button type="button" class="mine-mini-btn"' + (ok ? '' : ' data-lack="1"') + ' onclick="TowerMine.primal(\'primalMake\')">💎 만들기</button></div></div>';
  }
  var refineInfo = null, refineBusy = false;
  function refineGo(act, n) {
    if (refineBusy) return;
    refineBusy = true;
    api('/api/tower-mine-token?action=' + act + '&code=x&g=' + (n | 0)).then(function (d) {
      refineBusy = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      refineInfo = d;
      if (act === 'mana' && d.message) { msg(d.message, 3600); if (d.got > 0) { flashT0 = performance.now(); var mc = smithNpc ? [smithNpc.x, smithNpc.y] : [P.x, P.y]; lootBurst(mc[0], mc[1] - 1, [5], Math.min(30, d.got), [{ me: 1 }], 1.4, 0); } } // [2026-11-18] 정제된 마석 교환
      if (act === 'refine' && d.message) { msg(d.message, 3600); if (d.got > 0) { flashT0 = performance.now(); var dc = smithNpc ? [smithNpc.x, smithNpc.y] : [P.x, P.y]; lootBurst(dc[0], dc[1] - 1, [5], Math.min(30, d.got), [{ me: 1 }], 1.4, 0); } }
      if (smithOpen && smithView === 'refine') renderSmith(smithNpc);
    }).catch(function () { refineBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function refineHtml(n) {
    var d = refineInfo || {}, sh = d.shard | 0, ic = '<img src="' + oreIcon(75) + '" alt="" style="width:16px;vertical-align:middle"> ';
    var h = npcHead('🔥 ' + n.name, false) + talkBlock(n, n.line);
    h += '<div class="mine-smith-say" style="border:1px solid #ff8a5a"><b>⚒️ 정제</b> <small>' + ic + '악마의 광석 조각 하나마다 ' + (d.pct || 10) + '% 확률로 미스릴 원석이 남아요. 실패하면 재가 돼요.</small>';
    h += '<div class="mine-smith-row"><span class="nm">' + ic + '악마의 광석 조각<small>보유 ' + sh + '개 · 악마의 들판과 최후의 방어선에서 곡괭이로 캐면 나와요</small></span></div>';
    [[1, '1개'], [10, '10개'], [0, '전부']].forEach(function (b) { h += '<button type="button" class="mine-mini-btn"' + (sh < Math.max(1, b[0]) ? ' data-lack="1"' : '') + ' onclick="TowerMine.demonRefine(' + b[0] + ')">' + b[1] + ' 정제</button> '; });
    h += '</div>';
    var mn = d.mana | 0, mic = '<img src="' + oreIcon(80) + '" alt="" style="width:16px;vertical-align:middle"> '; // [2026-11-18] 정제된 마석 -> 미스릴 원석 1:1
    h += '<div class="mine-smith-say" style="border:1px solid #ff8ae8"><b>💠 마석 교환</b> <small>' + mic + '정제된 마석 하나를 미스릴 원석 하나로 바꿔 줘요.</small>';
    h += '<div class="mine-smith-row"><span class="nm">' + mic + '정제된 마석<small>보유 ' + mn + '개 · 마계 마을에 가면 하나씩 받아요</small></span></div>';
    [[1, '1개'], [10, '10개'], [0, '전부']].forEach(function (b) { h += '<button type="button" class="mine-mini-btn"' + (mn < Math.max(1, b[0]) ? ' data-lack="1"' : '') + ' onclick="TowerMine.manaSwap(' + b[0] + ')">' + b[1] + ' 교환</button> '; });
    return h + '</div>';
  }
  function tokenGo(act, code, g) {
    if (tokenBusy) return;
    tokenBusy = true;
    api('/api/tower-mine-token?action=' + act + (code != null ? '&code=' + encodeURIComponent(code) + '&g=' + g : '')).then(function (d) {
      tokenBusy = false; mgLoading = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      if (d.tiers) tokenShop = d;
      if (d.inv) mgInv = d.inv;
      if (d.mercs) st.mercs = d.mercs;
      if (act !== 'list') { if (d.ok && d.best >= 5) flashT0 = performance.now(); if (d.message) msg(d.message, act.indexOf('pull') === 0 ? 4200 : 2600); }
      if (smithOpen && (smithView === 'token' || smithView === 'mercmgr')) renderSmith(smithNpc);
    }).catch(function () { tokenBusy = false; mgLoading = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function tokenHtml(n) {
    var d = tokenShop, h = npcHead('🗼 ' + n.name, false) + talkBlock(n, n.line);
    if (!d) return h + '<div class="mine-smith-say">...</div>';
    h += '<div class="mine-smith-say"><img src="' + oreIcon(72) + '" alt="" style="width:16px;vertical-align:middle"> 탑의 증표 <b>' + d.have + '</b>개 -- 탑 몬스터 1개 · 수문장 20개 · 보물 칸 2개 · 증표 칸 3개'
        + '<br><small>뽑은 장비는 🛡️ 용병 관리 → ⚔️ 장비 탭에서 끼워요. 무기·투구·갑옷은 직업 전용, 악세는 공용.</small></div>';
    h += '<div style="display:flex;gap:4px;margin:6px 0">' + [['gear', '🎁 동료 장비 뽑기'], ['gem', '💎 보석 사기'], ['cut', '✨ 세공 보석 사기'], ['heart', '❤️ 보스 심장']].map(function (t) {
      return '<button type="button" class="mine-mini-btn"' + (tokenTab === t[0] ? ' style="outline:2px solid #ffd27a"' : '') + ' onclick="TowerMine.tkTab(\'' + t[0] + '\')">' + t[1] + '</button>';
    }).join('') + '</div>';
    if (tokenTab === 'gem' || tokenTab === 'cut' || tokenTab === 'heart') { // [2026-11-15] "탑 증표로 원하는 보석을 구매", [2026-11-16] 보석 사기 / 세공 보석 사기로 나눔(바다 보석도)
      var isCut = tokenTab === 'cut', isHeart = tokenTab === 'heart', heartCode = function (c) { return /_HEART$/.test(c); }; // [2026-11-17] 보스 심장 탭
      h += '<div class="mine-smith-say" style="border:1px solid #7ad8a0"><b>' + (isHeart ? '❤️ 보스 심장' : isCut ? '✨ 세공 보석 사기' : '💎 보석 사기') + '</b> <small>원하는 ' + (isHeart ? '보스의 심장' : isCut ? '세공된 보석' : '보석') + '을 골라 증표로 바꿔요.</small>';
      (d.gems || []).filter(function (g) { return isHeart ? heartCode(g.code) : !heartCode(g.code) && (g.code.indexOf('CUT_') === 0) === isCut; }).forEach(function (g) {
        var o = (st.ores || []).filter(function (q) { return q.code === g.code; })[0];
        var left = g.lim ? Math.max(0, g.lim - (g.bought | 0)) : 99, n10 = Math.min(10, left); // [2026-11-17] 전체 구매 한도
        h += '<div class="mine-smith-row"><span class="nm">' + (o ? '<img src="' + oreIcon(o.grade) + '" alt="" style="width:16px;vertical-align:middle"> ' : '') + escAttr(g.name) + ' · 증표 ' + g.price + '<small>보유 ' + (o ? o.qty : 0) + '개' + (g.lim ? ' · 구매 ' + (g.bought | 0) + '/' + g.lim : '') + '</small></span>'
            + '<button type="button"' + (left < 1 ? ' disabled' : d.have < g.price ? ' data-lack="1"' : '') + ' onclick="TowerMine.tkGem(\'' + g.code + '\',1)">1개</button>'
            + (n10 > 1 ? '<button type="button"' + (d.have < g.price * n10 ? ' data-lack="1"' : '') + ' onclick="TowerMine.tkGem(\'' + g.code + '\',' + n10 + ')">' + n10 + '개</button>' : '') + '</div>';
      });
      return h + '</div>';
    }
    [['equip', '⚔️ 장비 뽑기', '무기(공격력) · 투구(치명타) · 갑옷(상성 피해)'], ['acc', '💍 악세 뽑기', '목걸이 · 반지 · 팔찌 (두 효과를 절반씩)']].forEach(function (k) {
      h += '<div class="mine-smith-say" style="border:1px solid #7ab8ff"><b>' + k[1] + '</b> <small>' + k[2] + '</small>';
      d.tiers.forEach(function (t, i) {
        h += '<div class="mine-smith-row"><span class="nm">' + t.name + ' · 증표 ' + t.price + '<small>' + t.rate + (t.open ? '' : ' · 🔒 탑 ' + t.unlock + '층 수문장') + '</small></span>'
            + (t.open ? '<button type="button"' + (d.have < t.price ? ' data-lack="1"' : '') + ' onclick="TowerMine.mgPull(\'' + k[0] + '\',' + i + ',1)">1번</button><button type="button"' + (d.have < t.price * 10 ? ' data-lack="1"' : '') + ' onclick="TowerMine.mgPull(\'' + k[0] + '\',' + i + ',10)">10번</button>'
                : '<button type="button" disabled>잠김</button>') + '</div>';
      });
      h += '</div>';
    });
    return h;
  }
  /** [2026-11-16] "용병 탭 장비 장착은 플레이어 캐릭터 장비 장착과 동일하게" -- 용병 그림 둘레의 부위 칸 여섯(mgSlotBtn), 칸을 누르면 그 부위에 끼울 수 있는 장비 창(mgPopHtml -- 장착/해제). */
  var mgPop = null;
  function mgPopGo(id) { mgPop = id; if (smithOpen) renderSmith(smithNpc); }
  function mgSlotBtn(m, id) {
    var s = MG_SLOTS.filter(function (q) { return q[0] === id; })[0], g = (m.mg || {})[id] | 0, inf = (m.mgi || {})[id];
    return '<button type="button" class="doll-slot' + (g ? ' on' : '') + '" onclick="TowerMine.mgPop(\'' + id + '\')"><b>' + s[1] + '</b><span>' + (g ? escAttr(inf ? inf[0] : '★' + g) : '없음') + '</span></button>';
  }
  function mgPopHtml(m) {
    var s = MG_SLOTS.filter(function (q) { return q[0] === mgPop; })[0];
    if (!s) return '';
    if (mgInv == null && !mgLoading) { mgLoading = true; tokenGo('list'); }
    var g = (m.mg || {})[s[0]] | 0, inf = (m.mgi || {})[s[0]], acc = 'NRB'.indexOf(s[0]) >= 0;
    var rows = (mgInv || []).filter(function (it) { return it.part === s[0]; }); // [2026-11-17] 직업 구분 없이(서버가 지금 용병 직업 이름으로 합쳐 준다)
    var h = '<div class="eq-pop" onclick="if (event.target === this) TowerMine.mgPop(null)"><div class="eq-pop-box"><div class="eq-pop-h">' + s[1] + ' <small>' + (acc ? '용병 공용' : (m.name || '용병') + ' 전용') + '</small>'
        + '<button type="button" onclick="TowerMine.mgPop(null)">✖</button></div>';
    if (g) h += '<div class="mine-smith-row worn"><span class="nm">✔ ' + escAttr(inf ? inf[0] : '★' + g) + '<small>' + escAttr(inf ? inf[1] : '') + '</small></span><button type="button" onclick="TowerMine.mgOff(\'' + s[0] + '\',' + m.k + ')">해제</button></div>';
    if (mgInv == null) h += '<div class="mine-eq-empty">...</div>';
    else if (!rows.length && !g) h += '<div class="mine-eq-empty">가진 장비가 없어요. 요새 도시·탑 마을의 증표 상인에게서 뽑을 수 있어요.</div>';
    rows.forEach(function (it) {
      h += '<div class="mine-smith-row"><span class="nm">' + escAttr(it.name) + (it.q > 1 ? ' x' + it.q : '') + '<small>' + escAttr(it.eff) + '</small></span><button type="button" onclick="TowerMine.mgOn(\'' + it.code + '\',' + m.k + ')">장착</button></div>';
    });
    return h + '</div></div>';
  }
  function twGo(f) { closeSmith(); travel('/api/tower-mine-tw?action=go&n=' + f, '그 층으로 가지 못했어요.'); }
  function twExit() { closeSmith(); travel('/api/tower-mine-tw?action=exit', '탑에서 나가지 못했어요.'); }
  function riverHtml(n) {
    var city = map && map.id === 'towercity';
    return npcHead('⛵ ' + n.name, false) + talkBlock(n, n.line)
        + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.twFerry()">⛵ ' + (city ? '마물의 숲 나루터로' : '강 건너 요새 도시로') + '</button></div>';
  }
  function twFerry() { closeSmith(); travel('/api/tower-mine-tw?action=ferry', '배를 타지 못했어요.'); }
  // [2026-11-15] 월드보스 -- 위치 알림 wb {at, end, on, name, map, hp(천분율), n, act(지금 싸우는 사람), share(보상 조건 %), down(일어나는 시각), zones[[x, y, 터지는 시각]], zr, zw}:
  //  예고 10분 전부터 화면 위 띠 + [참여](어디서든 동쪽 성곽 내성 앞으로). 쓰러지면 그 자리에서 10초(화면 회색 + 작은 카운트), 화염 장판은 예고 칸이 차오르다 터진다.
  // [2026-11-17] "우리는 하나" -- 보스 맵에서 보스전이 시작되면 함께 있는 인원만큼 강해진다(서버 mineUnityCalc). 화면 위 띠 + 켜질 때/인원이 바뀔 때 큰 안내 + 모두의 발밑 금빛 고리.
  var unity = null, unityBar = null;
  function unityShow(u) {
    if (!el.ov) return;
    var was = unity;
    unity = u || null;
    if (!unityBar) { unityBar = document.createElement('div'); unityBar.className = 'mine-wb-bar'; unityBar.style.top = 'calc(var(--patch-h, 0px) + 46px)'; unityBar.style.background = 'rgba(40,30,10,.88)'; unityBar.style.borderColor = '#ffd27a'; el.ov.appendChild(unityBar); }
    if (!unity) { unityBar.style.display = 'none'; return; }
    unityBar.innerHTML = '🤝 <b style="color:#ffd27a">우리는 하나</b> · ' + unity.n + '명 함께 · ⚔️ 공격력 <b>+' + unity.atk + '%</b> · ❤️ 체력 <b>x' + unity.hp + '</b>';
    unityBar.style.display = '';
    if (!was || was.n !== unity.n) { flashT0 = performance.now(); banner = { text: '🤝 우리는 하나! ' + unity.n + '명이 함께 -- 공격력 +' + unity.atk + '% · 체력 x' + unity.hp, t0: performance.now() }; }
  }
  function unityRing(x, y, now) { var TSn = T * scale, p = 0.5 + 0.5 * Math.sin(now / 220); ctx.strokeStyle = 'rgba(255,214,110,' + (0.55 + 0.35 * p) + ')'; ctx.lineWidth = Math.max(1, 1.5 * scale); ctx.beginPath(); ctx.ellipse(x + TSn / 2, y + TSn * 0.82, TSn * (0.5 + 0.06 * p), TSn * 0.2, 0, 0, Math.PI * 2); ctx.stroke(); for (var i = 0; i < 3; i++) { var a = now / 400 + i * 2.1; fxDot(x + TSn / 2 + Math.cos(a) * TSn * 0.45, y + TSn * 0.82 + Math.sin(a) * TSn * 0.18 - (now / 30 + i * 40) % (TSn * 0.8), 1.2 * scale, '#ffe9a8', 0.7); } }
  var wbInfo = null, wbBar = null, wbDownUntil = 0, wbDownEl = null, wbDownTimer = 0;
  // [2026-11-16] "월드보스 등장/퇴장 이펙트 -- 날아서 오고 날아서 간다" -- 정각에 오른쪽 위 하늘에서 날아와 내려앉고(WB_FLY_MS), 시간이 다 되면 왼쪽 위 하늘로 날아간다(wbLeave).
  var WB_FLY_MS = 2800, wbLandAt = 0, wbGoneSeen = 0, wbLeave = null;
  function wbRender() {
    if (!el.ov) return;
    if (!wbBar) { wbBar = document.createElement('div'); wbBar.className = 'mine-wb-bar'; el.ov.appendChild(wbBar); }
    var w = wbInfo, tn = nowSrv();
    if (w && w.down && w.down > wbDownUntil) { wbDownUntil = w.down; wbDownShow(); }
    if (!w || !map || tn >= w.end) { wbBar.style.display = 'none'; wbBarOpen = false; if (!noteQ && noteBtn) noteBtn.style.display = 'none'; return; }
    // [2026-11-20] "심연의 거룡이 나와 있을 때도 오른쪽 알림 버튼을 눌러야만 나오게, 너무 불편해" -- 띠는 🔔를 눌렀을 때만(누를 때마다 열고 닫기), 그동안 🔔는 늘 보인다(새 알림 없으면 반짝이지 않게)
    noteBtnMake();
    noteBtn.style.display = '';
    noteBtn.classList.toggle('quiet', !noteQ);
    if (!wbBarOpen) { wbBar.style.display = 'none'; return; } // [2026-11-16] "테스트 버튼은 없애도" -- 띠의 관리자 시험 버튼 없앰(보스 창의 관리자 버튼은 남김)
    var inWb = map.id === (w.map || 'castle'), mins = function (ms) { return Math.max(1, Math.ceil(ms / 60000)) + '분'; };
    var h;
    if (!w.on) h = '🐉 월드보스 「' + escAttr(w.name) + '」 ' + callHhmm(w.at) + ' 동쪽 성곽에 등장 (' + mins(w.at - tn) + ' 뒤)' + (w.hp != null && w.hp < 1000 ? ' · 남은 체력 ' + Math.max(1, Math.round(w.hp / 10)) + '%' : '');
    else h = '🐉 ' + escAttr(w.name) + ' <span class="wb-hp"><i style="width:' + ((w.hp | 0) / 10) + '%"></i></span> ' + Math.round((w.hp | 0) / 10) + '%'
        + (w.act ? ' · ' + w.act + '명 싸우는 중' : w.n ? ' · ' + w.n + '명 참여' : '') + (w.buff ? ' · <b style="color:#ffb04a">💪 공격력 +' + w.buff + '%</b>' : '') /* [2026-11-17] 인원 버프 */
        + (w.share != null ? (w.share >= 100 ? ' · ✅ 보상 조건 달성' : ' · 내 참여 ' + w.share + '%') : '') + ' · ' + mins(w.end - tn) + ' 남음';
    wbBar.innerHTML = h;
    wbBar.style.display = '';
  }
  // [2026-11-16] "월드보스 생성 시 인게임 알림" -- 어느 맵에 있든 화면 가운데 위에 큰 알림(예고 때 한 번, 나타난 순간 한 번 + [참여하기]). 7초 뒤 사라지고 누르면 닫힌다.
  var wbAlertEl = null, wbAlertT = 0, wbPreSeen = 0, wbOnSeen = 0, mapZones = [];
  // [2026-11-18] "보스 알림과 이동 알림 문구를 필살 버튼 위쪽에 반짝이는 버튼 하나로, 누르면 멘트가 나오게" -- 알림이 오면 창을 띄우지 않고 🔔 버튼만 반짝인다.
  //  누르면 그때 마지막 알림을 창(참여하기 버튼 포함)으로 보여 주고 버튼은 사라진다. 10분이 지난 알림은 버튼도 저절로 사라진다.
  var noteQ = null, noteBtn = null, wbBarOpen = false;
  function noteBtnMake() { if (!noteBtn && el.ov) { noteBtn = document.createElement('button'); noteBtn.type = 'button'; noteBtn.className = 'mine-note-btn'; noteBtn.setAttribute('aria-label', '알림'); noteBtn.textContent = '🔔'; noteBtn.onclick = noteOpen; el.ov.appendChild(noteBtn); } }
  function wbAlert(title, sub, join) {
    if (!el.ov) return;
    noteQ = { title: title, sub: sub, join: join, at: Date.now() };
    noteBtnMake();
    noteBtn.style.display = '';
    noteBtn.classList.remove('quiet');
    noteBtn.classList.remove('ring'); void noteBtn.offsetWidth; noteBtn.classList.add('ring');
    killFeed(title + ' -- ' + sub, '#d8a8ff');
  }
  function wbActive() { return !!(wbInfo && map && nowSrv() < wbInfo.end); }
  function noteOpen() {
    var q = noteQ; noteQ = null;
    if (q && Date.now() - q.at < 600000) { wbAlertShow(q.title, q.sub, q.join); wbBarOpen = true; }
    else wbBarOpen = !wbBarOpen; // [2026-11-20] 거룡 띠 열고 닫기
    if (noteBtn) { noteBtn.style.display = wbActive() ? '' : 'none'; noteBtn.classList.add('quiet'); }
    wbRender();
  }
  function noteExpire() { if (noteQ && Date.now() - noteQ.at >= 600000) { noteQ = null; if (noteBtn && !wbActive()) noteBtn.style.display = 'none'; } }
  function wbAlertShow(title, sub, join) {
    if (!el.ov) return;
    if (!wbAlertEl) { wbAlertEl = document.createElement('div'); wbAlertEl.className = 'mine-wb-alert'; wbAlertEl.onclick = function (e) { if (!e.target || e.target.tagName !== 'BUTTON') wbAlertEl.style.display = 'none'; }; el.ov.appendChild(wbAlertEl); }
    wbAlertEl.innerHTML = '<b>' + title + '</b><span>' + sub + '</span>' + (join ? '<button type="button" class="mine-mini-btn" onclick="TowerMine.wbJoin()">⚔️ 참여하기</button>' : '');
    wbAlertEl.style.display = '';
    wbAlertEl.classList.remove('go'); void wbAlertEl.offsetWidth; wbAlertEl.classList.add('go');
    clearTimeout(wbAlertT);
    wbAlertT = setTimeout(function () { if (wbAlertEl) wbAlertEl.style.display = 'none'; }, 9000);
  }
  function wbNotice() {
    noteExpire();
    var w = wbInfo, tn = nowSrv();
    if (!w || !map) return;
    if (!w.on && wbPreSeen !== w.at) { wbPreSeen = w.at; wbAlert('🐉 월드보스 예고', '「' + escAttr(w.name) + '」이(가) ' + callHhmm(w.at) + '에 동쪽 성곽에 나타나요', false); }
    else if (w.on && wbOnSeen !== w.at) { wbOnSeen = w.at; if (tn - w.at < 45000) { flashT0 = performance.now(); wbAlert('🐉 월드보스 출현!', '「' + escAttr(w.name) + '」이(가) 동쪽 성곽 내성에 나타났어요', map.id !== (w.map || 'castle')); } }
  }
  function wbJoin() {
    if (wbAlertEl) wbAlertEl.style.display = 'none';
    if (!opened || !map || busy) return;
    setAuto(false);
    travel('/api/tower-mine-wb?action=join', '동쪽 성곽에 가지 못했어요.');
  }
  function downMe() { return wbDownUntil > nowSrv(); }
  /** 월드보스에 쓰러짐 -- 자리는 그대로, 화면만 회색으로 두고 작은 카운트(10초) 뒤 일어난다. */
  // [2026-11-20] 장갑 효과 표시 -- 광전사 불굴(체력 1로 버티고 5초 무적), 쌍격의 장갑 기절
  var berserkInvUntil = 0, gloveFxT = 0;
  function gloveFx(d) {
    if (!d) return;
    var t = performance.now();
    if (d.berserkSave) { berserkInvUntil = d.berserkSave; flashT0 = t; floats.push({ x: P.x, y: P.y - 1.2, text: '🔥 불굴! 5초 무적', color: '#ff6a3a', big: true, t0: t }); msg('🔥 광전사의 불굴 -- 쓰러질 일격을 버텼어요! 5초 동안 무적이에요.', 2600); }
    else if (d.berserkInv && t - gloveFxT > 700) { gloveFxT = t; floats.push({ x: P.x, y: P.y - 0.8, text: '🛡️ 무적', color: '#ffb07a', t0: t }); }
  }
  // [2026-11-20] "광전사 불굴 무적 이펙트 -- 캐릭터 주변 흰색 보호막 + 시간 초가 줄어드는 것" -- 무적이 끝나는 시각(서버 berserkSave)까지
  //  흰 비눗방울 같은 보호막(가장자리가 숨 쉬듯 빛나고 반짝이가 돈다) + 바깥 고리가 남은 시간만큼 줄어든다 + 머리 위 남은 초. 마지막 1.5초는 깜빡인다.
  function berserkShieldDraw(cx, cy, now) { // (cx, cy) = 캐릭터 그림 왼쪽 위
    var left = berserkInvUntil - nowSrv();
    if (left <= 0) return;
    var TS = T * scale, u = TS / 16, mx = cx + TS / 2, my = cy + TS * 0.55, rx = TS * 0.8, ry = TS * 0.92, f = Math.max(0, Math.min(1, left / 5000));
    ctx.save();
    ctx.globalAlpha = left < 1500 && Math.floor(now / 120) % 2 ? 0.45 : 1;
    var g = ctx.createRadialGradient(mx - rx * 0.3, my - ry * 0.35, u, mx, my, rx);
    g.addColorStop(0, 'rgba(255,255,255,0.38)'); g.addColorStop(0.6, 'rgba(230,245,255,0.08)'); g.addColorStop(1, 'rgba(255,255,255,0.30)');
    ctx.fillStyle = g; ctx.beginPath(); ctx.ellipse(mx, my, rx, ry, 0, 0, Math.PI * 2); ctx.fill();
    ctx.lineWidth = 1.5 * u; ctx.strokeStyle = 'rgba(255,255,255,' + (0.6 + 0.25 * Math.sin(now / 150)).toFixed(2) + ')'; ctx.stroke();
    ctx.strokeStyle = 'rgba(255,255,255,.9)'; ctx.lineWidth = 1.2 * u; ctx.beginPath(); ctx.ellipse(mx, my, rx * 0.78, ry * 0.8, 0, Math.PI * 1.1, Math.PI * 1.45); ctx.stroke(); // 반사광
    ctx.strokeStyle = 'rgba(0,0,0,.35)'; ctx.lineWidth = 3 * u; ctx.beginPath(); ctx.ellipse(mx, my, rx + 3 * u, ry + 3 * u, 0, 0, Math.PI * 2); ctx.stroke(); // 남은 시간 고리(바탕)
    ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 2 * u; ctx.beginPath(); ctx.ellipse(mx, my, rx + 3 * u, ry + 3 * u, 0, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * f); ctx.stroke();
    for (var i = 0; i < 6; i++) { var an = now / 700 + i * Math.PI / 3; ctx.fillStyle = i % 2 ? '#ffffff' : '#dff4ff'; ctx.fillRect(Math.round(mx + Math.cos(an) * rx - u), Math.round(my + Math.sin(an) * ry - u), Math.round(2 * u), Math.round(2 * u)); }
    ctx.restore();
    var txt = '🛡️ ' + (left / 1000).toFixed(1), fy = cy - 10 * u;
    ctx.save(); ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.lineWidth = 3 * dpr; ctx.strokeStyle = '#1a1a2a'; ctx.strokeText(txt, mx, fy); ctx.fillStyle = '#ffffff'; ctx.fillText(txt, mx, fy); ctx.restore();
  }
  function wbFall(d) {
    wbDownUntil = d.wbDown;
    setAuto(false);
    if (d.combat) setCombat(d.combat);
    if (d.message) msg(d.message, 3000);
    wbDownShow();
  }
  function wbDownShow() {
    if (!el.ov || !el.cv) return;
    if (!wbDownEl) { wbDownEl = document.createElement('div'); wbDownEl.className = 'mine-wb-down'; el.ov.appendChild(wbDownEl); }
    var left = wbDownUntil - nowSrv();
    if (left > 0) {
      el.cv.style.filter = 'grayscale(1) brightness(.72)';
      wbDownEl.textContent = '💫 ' + Math.ceil(left / 1000) + '초 뒤 다시 일어나요';
      wbDownEl.style.display = '';
      if (!wbDownTimer) wbDownTimer = setInterval(wbDownShow, 250);
      return;
    }
    if (wbDownTimer) { clearInterval(wbDownTimer); wbDownTimer = 0; }
    el.cv.style.filter = '';
    wbDownEl.style.display = 'none';
    if (wbDownUntil) { wbDownUntil = 0; floats.push({ x: P.x, y: P.y - 0.6, text: '✨ 다시 일어났어요!', color: '#ffe14d', big: true, t0: performance.now() }); }
  }
  /** [2026-11-20] 보스 이름 옆 "🎁 3/5" -- 오늘 남은 보상 횟수. */
  function bossRwTag(name) {
    var r = ((st && st.bossRw) || []).filter(function (x) { return x.name === name; })[0];
    if (!r) return '';
    var left = Math.max(0, r.max - r.used);
    return ' · <b style="color:' + (left ? '#7cf08a' : '#ff8a8a') + '">🎁 보상 ' + left + '/' + r.max + '</b>';
  }
  function worldBossHtml() {
    var L = (st && st.bosses) || [], tn = nowSrv();
    var rows = L.map(function (b) {
      var sub = b.wbTest ? '🛠️ 일반 공개 전(관리자만) -- 아래 버튼으로 시험 회차를 열어요' : b.wbKilled ? '✅ 이번 맵 주기엔 쓰러졌어요 -- 맵이 초기화되면 다시 나타나요' : b.wbAt ? '⏰ 매시 정각부터 10분 -- 남은 체력 ' + Math.max(1, Math.round((b.wbHp == null ? 1000 : b.wbHp) / 10)) + '%(맵 초기화 때까지 이어져요) · 쓰러뜨리면 싸운 모두에게 같은 보상' + (b.alive ? '' : ' · 다음 ' + callHhmm(b.wbAt)) // [2026-11-15] 월드보스
          : b.need ? '⚔️ 마물 처치 ' + (b.kills | 0) + '/' + b.need + ' -- 다 차면 나타나요'
          : (b.callAt > tn ? '🔒 ' + callHhmm(b.callAt) + '부터 소환 가능 (' + callLeftTxt(b.callAt - tn) + ' 뒤)' : '✅ 지금 소환할 수 있어요');
      return '<div class="mine-smith-row" style="display:block"><b>' + escAttr(b.name) + '</b> <small>' + escAttr(b.mapName || '') + '</small> · '
          + (b.alive ? '<b style="color:#ff6a6a">👑 나와 있어요</b>' : '<span style="color:#a89ec8">💤 없음</span>') + bossRwTag(b.name) + '<br><small>' + sub + '</small></div>';
    }).join('');
    // [2026-11-20] "동일 보스는 하루 5회까지만 보상 -- 지도에서 보스를 볼 때 몇 중 몇 남았는지 보스별로" -- 목록에 없는 보스(크라켄, 보로네오 등)도 아래 표에
    var rwAll = ((st && st.bossRw) || []).map(function (r) { var left = Math.max(0, r.max - r.used); return '<span style="display:inline-block;margin:2px 8px 2px 0;' + (left ? '' : 'opacity:.55') + '">' + escAttr(r.name) + ' <b style="color:' + (left ? '#7cf08a' : '#ff8a8a') + '">' + left + '/' + r.max + '</b></span>'; }).join('');
    if (rwAll) rows += '<div class="mine-smith-say" style="font-size:12px">🎁 <b>오늘 남은 보스 보상</b> (보스마다 하루 ' + (((st.bossRw || [])[0] || {}).max || 5) + '번, 0시에 다시)<br>' + rwAll + '</div>';
    var adm = st && st.adminMaps ? '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.wbTest(\'start\')">🛠️ 월드보스 시험 회차 시작 (10분, 동쪽 성곽 내성)</button><button type="button" onclick="TowerMine.wbTest(\'stop\')">🛠️ 월드보스 회차 끝내기</button></div>' : ''; // [2026-11-15] 관리자 시험
    return '<div class="mine-smith-say" style="border:1px solid #ffd27a">👑 <b>보스</b> -- 보스룸 소환사에게 말을 걸어 불러내요. 소환은 보스마다, 사람마다 2시간에 한 번이에요.</div>'
        + (rows || '<div class="mine-smith-say">아직 갈 수 있는 보스룸이 없어요.</div>') + adm;
  }
  function hermitNpc() { return { x: hermit.x, y: hermit.y, kind: 'hermit', name: '???', line: '...' }; }
  function armorerNpc() { return { x: armorer.x, y: armorer.y, kind: 'armorer', name: '떠돌이 갑옷장이', line: '...쇠는 달궜을 때 두드려야지. 갑옷을 보여 보게.' }; }
  function hermitHtml(n) {
    var h = hermitArmor ? npcHead('🛡️ 떠돌이 갑옷장이', false) + talkBlock(n, n.line) : npcHead('❔ ???', false) + talkBlock(n, '...');
    if (hermitStep === 'talk') return h + '<div class="mine-smith-menu list"><button type="button" onclick="TowerMine.hermitGo(\'list\')">' + (hermitArmor ? '갑옷을 보여 준다' : '보스를 모두 처치했다') + '</button></div>';
    if (hermitStep === 'list') {
      if (!hermitList) return h + '<div class="mine-smith-say">...</div>';
      if (!hermitList.length) return h + '<div class="mine-smith-say">' + (hermitArmor ? '...갑옷이 없군.' : '...무기가 없군.') + '</div>';
      return h + '<div class="mine-smith-say">' + (hermitArmor ? '...어느 갑옷을 두드려 줄까.' : '...무기를 보여라.') + '</div>' + hermitList.map(function (w) {
        return '<div class="mine-smith-row"><span class="nm" style="color:' + GRADE_COL[w.grade] + '">' + (hermitArmor ? '🛡️ ' : '🗡️ ') + w.name + '<small>' + w.gradeName + (w.maxName ? '' : ' · 더 올릴 수 없음') + '</small></span>'
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
    var q = step === 'do' ? 'action=' + (hermitArmor ? 'ado' : 'do') + '&code=' + encodeURIComponent(hermitPick.code) : 'action=' + (hermitArmor ? 'alist' : 'list');
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
    if (n.kind === 'hermit') { setAuto(false); hermitStep = 'talk'; hermitArmor = false; openSmith(n, 'hermit'); return; } // [2026-10-30]
    if (n.kind === 'armorer') { setAuto(false); hermitStep = 'talk'; hermitArmor = true; openSmith(n, 'hermit'); return; } // [2026-11-20] 떠돌이 갑옷장이(같은 창)
    if (n.kind === 'towergate') { twFloors(); return; } // [2026-11-15] 탑 관리인
    if (n.kind === 'tokenshop') { setAuto(false); tokenShop = null; openSmith(n, 'token'); tokenGo('list'); return; } // [2026-11-15] 증표 상인
    if (n.kind === 'riverferry') { setAuto(false); openSmith(n, 'river'); return; } // [2026-11-15] 강 뱃사공
    if (n.kind === 'seaferry') { setAuto(false); openSmith(n, 'sail'); return; } // [2026-11-18] 배 띄우기
    if (n.kind === 'wpkeeper') { setAuto(false); wpKeep = null; openSmith(n, 'wpkeep'); wpKeepGo('wpdur'); return; } // [2026-11-18] 워프 포인트 관리인
    if (n.kind === 'lighthouse') { lhBell(n); return; } // [2026-11-18] 등대 종 -- 구조선
    if (n.kind === 'mailbox') { setAuto(false); mailData = null; openSmith(n, 'mail'); mailGo('mail', null); return; } // [2026-11-17] 우체부
    if (n.kind === 'wbboard') { setAuto(false); wbBoard = null; openSmith(n, 'wbboard'); api('/api/tower-mine-wb?action=board').then(function (d) { wbBoard = d; if (smithOpen && smithView === 'wbboard') renderSmith(smithNpc); }).catch(function () { }); return; } // [2026-11-17] 월드보스 게시판
    if (n.kind === 'refiner') { setAuto(false); refineInfo = null; openSmith(n, 'refine'); refineGo('refineInfo', 0); return; } // [2026-11-17] 악마 광석 정제사
    if (n.kind === 'alchemist') { setAuto(false); alchemyInfo = null; openSmith(n, 'alchemy'); alchemyGo('alchemyInfo', 0, 0); return; } // [2026-11-20] 연금술사
    if (n.kind === 'primalsmith') { setAuto(false); primalInfo = null; openSmith(n, 'primal'); primalGo('primalInfo'); return; } // [2026-11-20] 태초의 보석 세공사
    if (n.kind === 'bosscall') { // [2026-11-13] 보스 소환사
      setAuto(false); bossCallInfo = null; openSmith(n, 'bosscall');
      api('/api/tower-mine-bosscall?action=info').then(function (d) { bossCallInfo = d; if (smithOpen && smithView === 'bosscall') renderSmith(smithNpc); }).catch(function () {});
      return;
    }
    if (n.kind === 'smith') openSmith(n, 'menu');
    else if (n.kind === 'captain') openSmith(n, 'captain'); // [2026-10-24] 선장 퀘스트 창
    else if (n.kind === 'boutique') openSmith(n, 'boutique'); // [2026-10-24] 명품상점
    else if (n.kind === 'barber') { tryLook = null; openSmith(n, 'barber'); } // [2026-11-20] 미용실
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
    else if (n.kind === 'armorer') { setAuto(false); openSmith(n, 'aenh'); } // [2026-11-18] 방어구 강화사, [2026-11-19] 왕도 무기 강화사 옆으로
    else if (n.kind === 'mercenary') { setAuto(false); openSmith(n, 'merc'); api('/api/tower-mine-merc?action=list').then(function (d) { mercShop = d; if (d && d.list) st.mercs = d.list; if (d && d.merc) st.merc = d.merc; if (d) st.merc2 = d.merc2 || null; if (smithOpen && smithView === 'merc') renderSmith(smithNpc); }).catch(function () {}); } // [2026-11-05] 왕도 용병소
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
      var hv = c.have; (st && st.ores || []).forEach(function (o) { if (o.grade === c.grade && o.qty != null) hv = o.qty; }); // [2026-11-18] "아쿠아마린 34개인데 반지 NPC는 29개" -- 창을 열 때 받아 둔 옛 수 대신 지금 수
      return '<span class="' + (hv >= c.need ? 'ok' : 'no') + '"><img src="' + oreIcon(c.grade) + '" alt="">' + c.name + ' ' + hv + '/' + c.need + '</span>';
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
  // [2026-11-19] "치장상점 열 때나 인벤토리 치장 탭이 너무 렉" -- 카드마다(100개 넘게) 입은 모습을 그리고 PNG로 바꾸는 일을 창을 그릴 때 한꺼번에 했다.
  //  카드 그림은 빈 그림으로 먼저 내고(data-lq), 다음 프레임부터 한 프레임에 LOOK_Q_MS 안에서 몇 장씩 그려 채운다(같은 모습은 캐시 -- 다시 열면 바로).
  var LOOK_BLANK = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7', LOOK_Q_MS = 8;
  var lookQ = [], lookQWait = {}, lookQSeq = 0, lookQOn = false;
  function lookImgLazy(lk, dir) { // src="..." 안에 넣는다 -- 캐시에 없으면 빈 그림 + data-lq 번호
    var key = JSON.stringify(lk) + dir;
    if (lookImgCache[key]) return lookImgCache[key];
    var id = 'lq' + (++lookQSeq);
    if (!lookQWait[key]) { lookQWait[key] = []; lookQ.push({ key: key, lk: lk, dir: dir }); }
    lookQWait[key].push(id);
    if (!lookQOn) { lookQOn = true; requestAnimationFrame(lookQRun); }
    return LOOK_BLANK + '" data-lq="' + id;
  }
  function lookQRun() {
    var t0 = performance.now();
    while (lookQ.length && performance.now() - t0 < LOOK_Q_MS) {
      var q = lookQ.shift(), src;
      try { src = lookImg(q.lk, q.dir); } catch (e) { src = LOOK_BLANK; }
      (lookQWait[q.key] || []).forEach(function (id) { var im = document.querySelector('img[data-lq="' + id + '"]'); if (im) { im.src = src; im.removeAttribute('data-lq'); } });
      delete lookQWait[q.key];
    }
    if (lookQ.length) requestAnimationFrame(lookQRun); else lookQOn = false;
  }
  // [2026-11-19] "치장 탭은 게임 시작 때 로딩하면" -- 들어온 뒤(그리고 내 모습이 바뀌면) 치장 카드/미리보기 그림을 쉬는 틈(requestIdleCallback)에 몇 장씩 미리 만들어 둔다.
  var lookWarmKey = null, lookWarmT = 0, lookWarmN = 0, lookIdleOn = false;
  function lookWarm() {
    if (!st || !st.cosShop || !st.cosShop.length) return;
    var lk = st.look || {}, wk = JSON.stringify(lk);
    if (lookWarmKey === wk) return;
    if (lookWarmKey && ++lookWarmN > 4) { lookImgCache = {}; lookWarmN = 0; } // 모습이 여러 번 바뀌면 옛 그림은 비운다(메모리)
    lookWarmKey = wk;
    var add = function (l, dir) { var key = JSON.stringify(l) + dir; if (!lookImgCache[key] && !lookQWait[key]) { lookQWait[key] = []; lookQ.push({ key: key, lk: l, dir: dir }); } };
    ['down', 'right', 'up'].forEach(function (d) { add(lk, d); });
    ['hat', 'hair', 'top', 'bottom', 'shoes', 'wing'].forEach(function (s) { add(lookWith(s, ''), 'down'); });
    st.cosShop.forEach(function (c) { add(lookWith(c.slot, c.look), 'down'); });
    add(lookWith('gender', lk.gender === 1 ? 0 : 1), 'down');
    if (lookQ.length && !lookIdleOn) { lookIdleOn = true; lookIdleNext(); }
  }
  function lookIdleNext() { if (window.requestIdleCallback) window.requestIdleCallback(lookIdleRun, { timeout: 2000 }); else setTimeout(function () { lookIdleRun(null); }, 60); }
  function lookIdleRun(dl) {
    var t0 = performance.now();
    while (lookQ.length && (dl && !dl.didTimeout ? dl.timeRemaining() > 2 : performance.now() - t0 < 4)) {
      var q = lookQ.shift(), src;
      try { src = lookImg(q.lk, q.dir); } catch (e) { src = LOOK_BLANK; }
      (lookQWait[q.key] || []).forEach(function (id) { var im = document.querySelector('img[data-lq="' + id + '"]'); if (im) { im.src = src; im.removeAttribute('data-lq'); } });
      delete lookQWait[q.key];
    }
    if (lookQ.length) lookIdleNext(); else lookIdleOn = false;
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
        + '<div class="mine-smith-row"><img class="mine-look-mini" src="' + lookImgLazy(lookWith('gender', lk.gender === 1 ? 0 : 1), 'down') + '" alt="" onclick="TowerMine.tryOn(\'gender\')">'
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
            + '<img src="' + lookImgLazy(lookWith(c.slot, c.look), 'down') + '" alt="' + c.name + ' 입은 모습" onclick="TowerMine.tryOn(\'' + c.code + '\')">'
            + '<div class="cos-nm">' + c.name + '</div>'
            + (c.note ? '<small class="cos-note">✨ ' + c.note + '</small>' : '') // [2026-10-28] 운영자 전용 설명
            + (c.owned ? '<small class="cos-own">가지고 있어요</small>' : costHtml(c.costs)) + btn + '</div>';
      });
      h += '</div></div>';
    });
    return h;
  }
  /** [2026-11-20] 미용실 -- 머리 모양(10종) / 염색(20색). 그림을 누르면 미리 보기, 버튼으로 바꾸기(기본으로 되돌리기는 무료). */
  function barberHtml(n) {
    var lk = st.look || {}, show = tryLook || lk, b = st.barber || {}, hs = st.hairStyles || [], hd = st.hairDyes || [], sc = st.hairStyleCur | 0, dc = st.hairDyeCur | 0;
    var h = npcHead('💇 ' + (n ? n.name : '미용실'), false) + talkBlock(n, n ? n.line : '')
        + '<div class="mine-look-top"><div class="mine-look-prevs">' + ['down', 'right', 'up'].map(function (d) { return '<img class="mine-look-prev" src="' + lookImg(show, d) + '" alt="">'; }).join('') + '</div>'
        + '<div class="mine-smith-say">' + (tryLook ? '👀 미리 보기: <b>' + tryName + '</b> <button type="button" class="mine-mini-btn" onclick="TowerMine.tryOn(\'me\')">내 모습</button>' : '지금 내 모습') + '<br><small>그림을 누르면 미리 볼 수 있어요.</small></div></div>';
    h += '<div class="cos-slot"><div class="cos-slot-h">💇 머리 모양 <small>' + costHtml(b.style) + '</small></div><div class="cos-car" data-slot="hstyle">';
    hs.forEach(function (s, i) {
      var cur = i === sc, lkS = lookWith('hairStyle', s.code || undefined);
      h += '<div class="cos-card' + (cur ? ' worn' : '') + '"><img src="' + lookImgLazy(lkS, 'right') + '" alt="' + s.name + '" onclick="TowerMine.barberTry(\'style\',' + i + ')"><div class="cos-nm">' + s.name + '</div>'
          + (cur ? '<span class="mine-worn">지금 머리</span>' : '<button type="button" ' + (i > 0 && !b.styleCan ? 'data-lack="1" ' : '') + 'onclick="TowerMine.barber(\'hairStyle\',' + i + ')">' + (i ? '바꾸기' : '되돌리기') + '</button>') + '</div>';
    });
    h += '</div></div><div class="cos-slot"><div class="cos-slot-h">🎨 염색 <small>' + costHtml(b.dye) + '</small>' + (dc ? ' <button type="button" class="mine-mini-btn" onclick="TowerMine.barber(\'hairDye\',0)">원래 색으로</button>' : '') + '</div><div class="cos-car" data-slot="hdye">';
    hd.forEach(function (c, i) {
      var cur = i + 1 === dc;
      h += '<div class="cos-card' + (cur ? ' worn' : '') + '"><img src="' + lookImgLazy(lookWith('hair', c.color), 'down') + '" alt="' + c.name + '" onclick="TowerMine.barberTry(\'dye\',' + i + ')"><div class="cos-nm"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + c.color + ';border:1px solid #0006;vertical-align:middle"></span> ' + c.name + '</div>'
          + (cur ? '<span class="mine-worn">물들임</span>' : '<button type="button" ' + (!b.dyeCan ? 'data-lack="1" ' : '') + 'onclick="TowerMine.barber(\'hairDye\',' + (i + 1) + ')">물들이기</button>') + '</div>';
    });
    return h + '</div></div>';
  }
  function barberTry(kind, i) {
    if (kind === 'style') { var s = (st.hairStyles || [])[i]; if (s) { tryLook = lookWith('hairStyle', s.code || undefined); tryName = s.name; } }
    else { var c = (st.hairDyes || [])[i]; if (c) { tryLook = lookWith('hair', c.color); tryName = c.name + ' 염색'; } }
    renderSmith(smithNpc);
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
    { id: 'towergate', name: '거대한 탑 입구 🗼', x: 0, y: 0, fill: '#3a4a7a', ink: '#f0f4ff', w: 76 }, // [2026-11-17] 전체 지도에 요새 도시 구간
    { id: 'towercity', name: '요새 도시 엘리시온', x: 0, y: 0, fill: '#868b98', ink: '#ffffff', town: true },
    { id: 'demonwest', name: '서쪽 악마의 들판 😈', x: 0, y: 0, fill: '#4a1a1a', ink: '#ffd0c0', w: 76 },
    { id: 'demoneast', name: '동쪽 악마의 들판 😈', x: 0, y: 0, fill: '#4a1a1a', ink: '#ffd0c0', w: 76 },
    { id: 'demonwest2', name: '서쪽 들판 2 😈', x: 0, y: 0, fill: '#5a1414', ink: '#ffd0c0', w: 76 }, // [2026-11-17] 로드맵
    { id: 'demoneast2', name: '동쪽 들판 2 😈', x: 0, y: 0, fill: '#5a1414', ink: '#ffd0c0', w: 76 },
    { id: 'demonwest3', name: '서쪽 들판 3 😈', x: 0, y: 0, fill: '#6a0e0e', ink: '#ffd0c0', w: 76 },
    { id: 'demoneast3', name: '동쪽 들판 3 😈', x: 0, y: 0, fill: '#6a0e0e', ink: '#ffd0c0', w: 76 },
    { id: 'lastline', name: '최후의 방어선 🛡️', x: 0, y: 0, fill: '#5a4a3e', ink: '#ffe9c8', w: 80 },
    { id: 'abyssgate', name: '마계 입구 🌀', x: 0, y: 0, fill: '#3a0a4a', ink: '#f0c8ff', w: 76 },
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
    { id: 'isle7', name: '용암섬', x: 238, y: 675, fill: '#a83a1a', ink: '#fff', sub: true, w: 72 },
    // [2026-11-18] "전체 지도에도 반영" -- 먼 바다(윤슬항/바다 위/망망대해)와 연안 뱃길(내 배로 항구 사이)
    { id: 'seacity', name: '바다마을 윤슬항 ⚓', x: 0, y: 0, fill: '#c8a46a', ink: '#2a1d10', town: true },
    { id: 'opensea', name: '바다 위 🐙', x: 0, y: 0, fill: '#2f6a9a', ink: '#fff', w: 76 }
  ];
  // [2026-10-28] "전체 지도 보기가 어렵다, 지역 구간별로 나눠 놓으면" -- 네 구간(탭)으로 나눠 그린다. 처음엔 지금 있는 구간.
  //  구간마다 칸 자리(x, y)를 따로 두고(이름/색/폭은 WORLD_NODES), 다른 구간으로 이어지는 곳은 점선 칸(@ -- 누르면 그 구간 탭으로).
  var WORLD_REGIONS = [
    // [2026-11-20] 전체 지도 그림 -- 거대한 탑(탑 입구 위로 솟은 돌탑)과 요새 도시 축소도가 들어가게 위아래로 넓혔다
    { id: 'fort', name: '🏰 요새 도시', vh: 470, // [2026-11-17] 로드맵 -- 요새 도시(가운데 아래), 북문 = 탑, 동/서 들판 1 -> 2 -> 3 -> 가운데 최후의 방어선 -> 마계 입구 -> 혼돈스러운 마계
      nodes: [['abyssgate', 135, 10], ['lastline', 135, 80], ['demonwest3', 30, 80], ['demoneast3', 240, 80], ['demonwest2', 30, 190], ['demoneast2', 240, 190], ['towergate', 135, 250],
        ['towercity', 135, 352], ['demonwest', 30, 352], ['demoneast', 240, 352], ['@ferry', 135, 440, 'desert', '⛵ 마물의 숲 나루터']],
      links: [['towercity', 'towergate'], ['towercity', 'demonwest'], ['towercity', 'demoneast'], ['demonwest', 'demonwest2'], ['demoneast', 'demoneast2'], ['demonwest2', 'demonwest3'], ['demoneast2', 'demoneast3'],
        ['demonwest3', 'lastline'], ['demoneast3', 'lastline'], ['lastline', 'abyssgate'], ['towercity', '@ferry', 'sea']] }, // [2026-11-17] 혼돈스러운 마계는 지도에 없다(정제사 소문으로만)
    { id: 'plain', name: '🌾 평원', vh: 390,
      nodes: [['@desert', 160, 30, 'desert', '🏜️ 사막·왕도 ▲'], ['cave1', -58, 115], ['cave2', -58, 165], ['cave3', -58, 215], ['cave4', -58, 265], ['field', 160, 180], ['castle', 268, 180], // [2026-11-20] 동굴 단면 그림(위에서부터 B1~B4)
        ['dungeon', 52, 240], ['beach', 160, 270], ['@sea', 92, 350, 'sea', '🏝️ 남쪽 바다 ⛵'], ['@snow', 240, 350, 'snow', '❄️ 설원 ⛵']],
      links: [['field', '@desert'], ['field', 'cave1'], ['cave1', 'cave2'], ['cave2', 'cave3'], ['cave3', 'cave4'], ['field', 'dungeon'], ['field', 'castle'], ['field', 'beach'], ['beach', '@sea', 'sea'], ['beach', '@snow', 'sea']] },
    { id: 'desert', name: '🏜️ 사막·왕도', vh: 460, tx: 226, // [2026-11-20] 왕도 축소도(실제 맵 그림)가 들어가게 왕도 둘레를 넓혔다
      nodes: [['ruins', 160, 30], ['royalroad', 160, 100], ['@snowtown', 268, 100, 'snow', '❄️ 설산 마을 ▶'], ['city', 160, 195], ['westroad', 44, 195], ['forestmaze', 44, 120], ['harpynest', 44, 50], ['harpyferry', -64, 50], ['steppe', 160, 295], ['desert', 160, 365],
        ['antnest', 44, 365], ['pyramid', 44, 435], ['@plain', 172, 435, 'plain', '🌾 평원 ▼']],
      links: [['royalroad', 'ruins'], ['royalroad', '@snowtown'], ['royalroad', 'city'], ['city', 'westroad'], ['westroad', 'forestmaze'], ['forestmaze', 'harpynest'], ['harpynest', 'harpyferry'], ['city', 'steppe'], ['steppe', 'desert'], ['desert', 'antnest', 'fall', '🕳️'], ['antnest', 'pyramid'], ['desert', '@plain']] },
    { id: 'snow', name: '❄️ 설원·설산', vh: 420,
      nodes: [['snowpeak', 160, 30], ['snowroad', 160, 90], ['snowtown', 160, 150], ['@royal', 46, 150, 'desert', '🏰 왕도 ◀'], ['snowcave', 272, 160], ['snowcave2', 272, 220], ['snow3', 160, 210], ['snow2', 160, 270], ['snow1', 160, 330],
        ['north', 160, 390], ['@beach2', 276, 390, 'plain', '⛵ 바닷가']],
      links: [['snowroad', 'snowpeak'], ['snowtown', 'snowroad'], ['snowtown', '@royal'], ['snowtown', 'snowcave'], ['snowcave', 'snowcave2'], ['snow3', 'snowtown'], ['snow2', 'snow3'], ['snow1', 'snow2'], ['north', 'snow1'], ['north', '@beach2', 'sea']] },
    // [2026-10-30] "다도해 뱃길에서 일곱 섬 가는 길도 실제 뱃길처럼" -- 실제 맵처럼 본섬에서 북쪽으로 물길, 왼쪽/오른쪽 번갈아 부두(동백 > 갈매기 > ... > 용암)
    // [2026-11-18] 먼 바다 -- 윤슬항 -> (오른쪽) 바다 위 -> (오른쪽 끝) 남쪽 바닷가 7시, 바닷가 7시 끝 = 망망대해, 5시 끝 = 다도해 뱃길. 연안 뱃길 = 항구 넷을 내 배로.
    { id: 'ocean', name: '🌊 먼 바다', vh: 300,
      nodes: [['seacity', 50, 70], ['opensea', 190, 70]], // [2026-11-18] 망망대해는 지도에 없다(사용자)
      links: [['seacity', 'opensea', 'sea', '⛵']] },
    // [2026-11-20] "일곱 섬은 본섬 오른쪽, 실제 맵(다도해 뱃길)처럼 세로 -- 아래부터 왼쪽/오른쪽 번갈아(동백 > 갈매기 > ... > 용암)", "신궁은 3층 건물, 보스방은 옆에(실제 맵도 신궁 동쪽 문)".
    //  신궁 1~3층은 본섬 아래 탑의 층(간격 WORLD_SHRINE_U), 보스방은 그 오른쪽 단층 -- 그림은 worldShrineSvg, 층 이름/인원은 worldHtml.
    { id: 'sea', name: '🏝️ 남쪽 바다', vh: 760, cx: 300, channel: { top: 150, isles: ['isle1', 'isle2', 'isle3', 'isle4', 'isle5', 'isle6', 'isle7'] },
      nodes: [['@beach', 60, 28, 'plain', '⛵ 남쪽 바닷가'], ['island', 40, 520], ['archi', 230, 520], ['archisea', 440, 520],
        ['shrine', 200, 711], ['shrine2', 200, 665], ['shrine3', 200, 619], ['shrineboss', 335, 711],
        ['isle1', 360, 450], ['isle2', 520, 400], ['isle3', 360, 350], ['isle4', 520, 300], ['isle5', 360, 250], ['isle6', 520, 200], ['isle7', 360, 150]],
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
  var WORLD_PLACE = { ocean: [-300, 830], fort: [-400, -425], /* [2026-11-20] 엘리시온 강(요새 도시 남쪽 ~ 나루터 북쪽) 자리 -- 45 위로 */ /* [2026-11-17] "나루터 -> 요새 도시 점선이 다른 맵을 뚫는다, 요새 도시를 더 위로" -- 점선이 동쪽 들판 아래로 비스듬히 지나가게 */ desert: [0, 0], snow: [360, 0], plain: [110, 440], sea: [150, 700] } /* [2026-11-11] 남쪽 바다를 오른쪽으로 270 -- 바닷가 -> 남쪽 섬 뱃길이 용암섬/산호섬을 가로지르지 않게 */ /* [2026-11-08] 평원을 오른쪽으로 110 -- 광산 동굴을 왼쪽으로 펼쳐 피라미드 보스 글자와 안 겹치게 */, WORLD_SIZE = [960, 1560]; // [2026-10-30] 남쪽 바다는 동쪽 성곽 옆이 아니라 평원 한참 아래로
  var WORLD_CROSS = [ // [2026-11-18] 먼 바다 -- 바다 위 -> 바닷가, 바닷가 5시 -> 다도해 뱃길(망망대해는 지도에 없다)
    ['ocean', 'opensea', 'plain', 'beach', 'sea', '⛵'], ['plain', 'beach', 'sea', 'archisea', 'sea', '5시 ⛵', [[760, 740], [760, 1220]]],['fort', 'towercity', 'desert', 'harpyferry', 'sea', '⛵'], ['desert', 'desert', 'plain', 'field', 'walk'], ['desert', 'royalroad', 'snow', 'snowtown', 'walk'], ['plain', 'beach', 'sea', 'island', 'sea', '⛵'], ['plain', 'beach', 'snow', 'north', 'sea', '⛵', [[650, 710], [650, 390]]]]; // [2026-11-04] 바닷가 -> 설원 뱃길은 동쪽 바다로 돌아서(성곽/설원 칸을 가로지르지 않게)
  // [2026-10-30] "던전 층수는 다르게 표기" -- 층이 있는 곳은 겹친 카드 + 층 표시(B1/1F), 이름에서 "N층"을 빼고 층 사이 길은 노란 점선
  var WORLD_FLOOR = { cave1: 'B1', cave2: 'B2', cave3: 'B3', cave4: 'B4', snowcave: 'B1', snowcave2: 'B2', shrine: '1F', shrine2: '2F', shrine3: '3F', snow1: '1F', snow2: '2F', snow3: '3F' };
  var worldVB = null, wMoved = false;
  function worldPos(rid, nid) {
    var R = WORLD_REGIONS.filter(function (r) { return r.id === rid; })[0], o = WORLD_PLACE[rid];
    var e = R && R.nodes.filter(function (n) { return n[0] === nid; })[0];
    return e ? { x: o[0] + e[1], y: o[1] + e[2] } : null;
  }
  // ---- [2026-11-20] 전체 지도 -- 옛 전도 풍 ----
  // 사용자: "옛 전도 풍, 강/바다 이름, 강은 붓으로 그린 듯 자연스럽게, 지도 밖은 미탐사 구역(땅/바다를 그려 두고 구름으로)", "왕도 북쪽 샛길 -> 설산 마을은 땅으로 이어지게",
  //  "요새 도시 위쪽은 다 땅", "윤슬항은 땅이 아니다", "지도를 열 때 말고 게임 시작 때 미리 그려 두기",
  //  "신궁은 3층 건물(보스방은 옆에 따로)", "일곱 섬은 본섬 오른쪽, 실제 맵처럼 세로", "동굴/설산/요새 도시의 탑도 그림으로", "왕도는 대도시처럼, 요새 도시는 요새처럼(실제 맵과 비슷하게)".
  //  땅: 칸마다 원을 겹치고(+ 걸어서 잇는 길을 따라 둑, 요새 도시 북쪽 땅, 강둑, 미탐사 땅) 잡음을 더한 높이 표에서 등고선(마칭 스퀘어)을 뽑아 매끈한 선 그림(path)으로 --
  //  필터 없는 선 그림이라 확대해도 선명하고 끌 때 가볍다. 구름(미탐사)과 왕도/요새 도시 축소도(실제 맵 칸으로 그림)만 작은 그림. 모두 게임 시작 로딩(preloadExtra)에서 한 번 만든다.
  //  그림으로 그리는 곳(WORLD_GROUP)은 칸 대신 그림 위 띠(층 이름 + 인원) 또는 축소도 -- 같은 그림 안의 길은 안 그리고, 밖에서 오는 길은 그림의 입구(bg.entry)로.
  var worldBg = null, minePrevScheme = '';
  var WORLD_SEA_IDS = { opensea: 1, archisea: 1, seacity: 1 }; // 물 위 칸(땅을 깔지 않는다 -- 윤슬항은 물 위 항구)
  var WORLD_GROUP = { shrine: 'shrine', shrine2: 'shrine', shrine3: 'shrine', shrineboss: 'shrine', cave1: 'cave', cave2: 'cave', cave3: 'cave', cave4: 'cave',
    snowcave: 'icecave', snowcave2: 'icecave', towergate: 'tower', city: 'city', towercity: 'fort' };
  var WORLD_BAND = { shrine: '1층', shrine2: '2층', shrine3: '3층', shrineboss: '🥷 보스방', cave1: 'B1', cave2: 'B2', cave3: 'B3', cave4: 'B4', snowcave: 'B1', snowcave2: 'B2', towergate: '🗼 거대한 탑' };
  var WORLD_BAND_W = { shrine: 94, shrine2: 76, shrine3: 62, shrineboss: 70, cave1: 74, cave2: 74, cave3: 74, cave4: 74, snowcave: 66, snowcave2: 66, towergate: 92 };
  var WORLD_BAND_STY = { shrine: ['rgba(255,248,230,.93)', '#3a1a0c'], boss: ['rgba(70,22,34,.93)', '#ffd8e0'], cave: ['rgba(40,28,20,.9)', '#f4e2c0'], icecave: ['rgba(232,244,252,.94)', '#1a3a50'], tower: ['rgba(255,248,230,.94)', '#3a1a0c'] };
  var WORLD_LV_DX = { shrine: 70, shrine2: 61, shrine3: 53, shrineboss: 56, cave1: 62, cave2: 62, cave3: 62, cave4: 62, snowcave: 62, snowcave2: 62 }; // 권장 Lv 글씨는 그림 왼쪽 바깥에
  var WORLD_SHRINE_W = [124, 106, 90], WORLD_SHRINE_U = 46, WORLD_SHRINE_H = 38;
  // [2026-11-20] "엘리시온 강은 어디니? 악마 사냥터 동쪽은 불의 강, 나루터는 낚시가 안 돼(버튼이 없었다)" -- 실제 맵: 요새 도시 남쪽을 가로지르는 큰 강 + 해자, 나루터 북쪽 강, 탑 입구 연못이 엘리시온 강 낚시 지역.
  //  지도도 그렇게: 엘리시온 강은 동쪽 샘에서 나루터 북쪽, 요새 도시 남쪽(해자 물이 흘러듦)을 지나 서쪽 바다로. 악마의 들판 동쪽 강은 불의 강(용암 -- 북쪽 산에서 흘러 용암 웅덩이로 끝남).
  var WORLD_RIVER = [[-4, -20], [-28, 2], [-64, 15], [-110, 11], [-160, 6], [-210, 12], [-265, 20], [-320, 14], [-370, 22], [-420, 34], [-472, 50]];
  var WORLD_TRIB = [[-246, -34], [-250, -10], [-256, 18]]; // 요새 도시 해자에서 강으로
  var WORLD_FIRE = [[-112, -600], [-98, -545], [-118, -490], [-100, -430], [-118, -372], [-101, -330], [-118, -290], [-102, -250], [-120, -205], [-104, -160], [-121, -112], [-104, -72], [-108, -52]];
  var WORLD_LAKES = [[325, 556, 24, 10], [256, 374, 14, 7]];
  var WORLD_WATERS = [['엘리시온 강', -330, 38, -3, 13], ['불의 강', -84, -200, 82, 13, '#b8401a'], ['북쪽 바다', 735, 360, 0, 22], ['남쪽 바다', 40, 1110, 0, 30], ['다 도 해', 395, 1080, 0, 22], ['중앙 평원 호수', 330, 536, 0, 9], ['사막 오아시스', 262, 357, 0, 9]]; // [2026-11-20] 오아시스 글씨가 북쪽 사막 칸과 겹쳐 오른쪽으로
  var WORLD_MOUNTS = [[-140, -628], [-112, -642], [-84, -626], [-160, -604], [-62, -604], [-122, -590], [-420, -470], [-446, -452], [-620, -390], [-300, -540], [-700, -610], [-520, -720], [300, -430], [330, -410], [130, -330], [160, -345]];
  var WORLD_UNEXP = [[-690, -640, 170, 12], [300, -420, 140, 10], [560, -380, 110, 8], [1040, 160, 130, 10], [1000, 520, 110, 8], [1300, 900, 110, 8], [-690, 330, 150, 10], [-640, 620, 90, 6],
    [-640, 1300, 140, 10], [-480, 1560, 90, 6], [1120, 1450, 140, 10], [240, 1750, 160, 11], [720, 1800, 110, 8]];
  var WG = { x0: -1300, y0: -1450, x1: 1900, y1: 2600, c: 8 }; // 높이 표 범위(지도 밖 구름까지)
  function wHash(i, j) { var h = (Math.imul(i, 374761393) + Math.imul(j, 668265263)) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) >>> 0) / 4294967296; }
  function wNoise(x, y) {
    var i = Math.floor(x), j = Math.floor(y), fx = x - i, fy = y - j, u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
    var a = wHash(i, j), b = wHash(i + 1, j), c = wHash(i, j + 1), d = wHash(i + 1, j + 1);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  }
  function wFbm(x, y, o) { var s = 0, amp = 0.5, f = 1, n = 0; for (var k = 0; k < o; k++) { s += wNoise(x * f + k * 17.3, y * f - k * 9.1) * amp; n += amp; amp *= 0.5; f *= 2; } return s / n; }
  function wRnd(seed) { var sd = seed; return function () { sd = (sd * 16807) % 2147483647; return sd / 2147483647; }; }
  function worldNodePos() {
    var P = {};
    WORLD_REGIONS.forEach(function (R) { var o = WORLD_PLACE[R.id]; R.nodes.forEach(function (e) { if (e[0].charAt(0) !== '@') P[e[0]] = { x: o[0] + e[1], y: o[1] + e[2], r: R.id }; }); });
    return P;
  }
  /** 길(선분) -- 구간 안 길 + 구간 사이 길 + 일곱 섬 뱃길. walkOnly면 걸어서 잇는 길만(땅 둑). */
  function worldSegs(P, walkOnly) {
    var s = [], bad = function (id) { return WORLD_SEA_IDS[id] || WORLD_GROUP[id] === 'shrine'; };
    WORLD_REGIONS.forEach(function (R) {
      R.links.forEach(function (l) {
        var p = P[l[0]], q = P[l[1]], ty = l[2] || 'walk';
        if (!p || !q || (walkOnly && (ty === 'sea' || bad(l[0]) || bad(l[1])))) return;
        s.push([p.x, p.y, q.x, q.y]);
      });
      if (R.channel && P.archisea && !walkOnly) {
        var chx = P.archisea.x, ctop = WORLD_PLACE[R.id][1] + R.channel.top;
        s.push([chx, P.archisea.y, chx, ctop]);
        R.channel.isles.forEach(function (id) { var p = P[id]; if (p) s.push([chx, p.y, p.x, p.y]); });
      }
    });
    WORLD_CROSS.forEach(function (c) {
      var p = worldPos(c[0], c[1]), q = worldPos(c[2], c[3]);
      if (!p || !q || (walkOnly && c[4] !== 'walk')) return;
      var pts = [[p.x, p.y]].concat(c[6] || []).concat([[q.x, q.y]]);
      for (var i = 0; i < pts.length - 1; i++) s.push([pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]]);
    });
    return s;
  }
  function worldCr(pts, step) { // Catmull-Rom 샘플
    var out = [];
    for (var i = 0; i < pts.length - 1; i++) {
      var p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
      for (var t = 0; t < 1 - 1e-6; t += step) {
        var t2 = t * t, t3 = t2 * t, q = [];
        for (var k = 0; k < 2; k++) q.push(0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3));
        out.push(q);
      }
    }
    out.push(pts[pts.length - 1]);
    return out;
  }
  function worldLandPts(P) {
    var pts = [], rnd = wRnd(11);
    Object.keys(P).forEach(function (id) {
      if (WORLD_SEA_IDS[id]) return;
      var p = P[id], isle = /^isle/.test(id), r = isle ? 34 : p.r === 'fort' ? 56 : 70;
      pts.push([p.x, p.y, r]);
      if (!isle) for (var k = 0; k < 5; k++) { var a = ((id.charCodeAt(0) * 37 + id.length * 13 + k * 71) % 360) * Math.PI / 180; pts.push([p.x + Math.cos(a) * r * 0.75, p.y + Math.sin(a) * r * 0.75, r * 0.5]); }
    });
    if (P.city) pts.push([P.city.x, P.city.y, 95]); // 왕도/요새 도시 축소도 아래는 넉넉히 땅
    if (P.towercity) pts.push([P.towercity.x, P.towercity.y, 85]);
    if (P.shrine && P.shrineboss) pts.push([(P.shrine.x + P.shrineboss.x) / 2, P.shrine.y - 30, 100]); // 신궁 건물 아래
    if (P.snowtown && P.snow1) for (var my = P.snowpeak.y; my <= P.snow1.y + 20; my += 40) pts.push([P.snowtown.x, my, 120 + (my - P.snowpeak.y) * 0.25]); // 설산 그림 아래
    if (P.towergate && P.towercity) { pts.push([P.towergate.x, P.towergate.y - 60, 95]); pts.push([P.towergate.x, (P.towergate.y + P.towercity.y) / 2, 85]); } // 거대한 탑 둘레
    worldSegs(P, true).forEach(function (s) { // 걸어서 잇는 길은 땅으로(왕도 북쪽 샛길 -> 설산 마을 등)
      var dx = s[2] - s[0], dy = s[3] - s[1], n = Math.ceil(Math.sqrt(dx * dx + dy * dy) / 35);
      for (var i = 1; i < n; i++) pts.push([s[0] + dx * i / n, s[1] + dy * i / n, 44]);
    });
    for (var y = -1060; y <= -300; y += 60) for (var x = -900; x <= 40; x += 60) pts.push([x + (rnd() - 0.5) * 30, y + (rnd() - 0.5) * 30, 46 + rnd() * 14]); // 요새 도시 북쪽은 모두 땅
    worldCr(WORLD_FIRE, 0.5).forEach(function (q) { pts.push([q[0] + 8, q[1], 46]); }); // 강둑(강이 땅 위로 흐르게) -- 불의 강
    worldCr(WORLD_RIVER.slice(0, 9), 0.5).forEach(function (q) { pts.push([q[0], q[1], 40]); }); // 엘리시온 강(하구 앞까지)
    var ur = wRnd(29);
    WORLD_UNEXP.forEach(function (u) { // 미탐사 땅(구름 아래)
      pts.push([u[0], u[1], u[2] * 0.6]);
      for (var k = 0; k < u[3]; k++) { var a = ur() * Math.PI * 2, d = ur() * u[2]; pts.push([u[0] + Math.cos(a) * d, u[1] + Math.sin(a) * d, u[2] * (0.3 + ur() * 0.35)]); }
    });
    return pts;
  }
  function worldField(circles) {
    var c = WG.c, W = Math.ceil((WG.x1 - WG.x0) / c) + 1, H = Math.ceil((WG.y1 - WG.y0) / c) + 1, f = new Float32Array(W * H), i, j;
    for (i = 0; i < f.length; i++) f[i] = -30;
    circles.forEach(function (q) {
      var r = q[2], m = r + 30, i0 = Math.max(0, Math.floor((q[0] - m - WG.x0) / c)), i1 = Math.min(W - 1, Math.ceil((q[0] + m - WG.x0) / c));
      var j0 = Math.max(0, Math.floor((q[1] - m - WG.y0) / c)), j1 = Math.min(H - 1, Math.ceil((q[1] + m - WG.y0) / c));
      for (var jj = j0; jj <= j1; jj++) { var yy = WG.y0 + jj * c - q[1]; for (var ii = i0; ii <= i1; ii++) { var xx = WG.x0 + ii * c - q[0], v = r - Math.sqrt(xx * xx + yy * yy), k = jj * W + ii; if (v > f[k]) f[k] = v; } }
    });
    for (j = 0; j < H; j++) for (i = 0; i < W; i++) f[j * W + i] += (wFbm((WG.x0 + i * c) / 60, (WG.y0 + j * c) / 60, 3) - 0.5) * 26; // 울퉁불퉁한 해안
    return { f: f, W: W, H: H };
  }
  /** 높이 표에서 L 높이 등고선 -> 닫힌 곡선들(path d). 칸 모서리 번호로 선분을 이어 붙인다. */
  function worldContour(F, L) {
    var f = F.f, W = F.W, H = F.H, c = WG.c, pt = {}, adj = {}, segs = [];
    var ep = function (t, i, j) {
      var k = t + i + ',' + j;
      if (!pt[k]) { var a = f[j * W + i], b = t === 'h' ? f[j * W + i + 1] : f[(j + 1) * W + i], s = (L - a) / (b - a); pt[k] = t === 'h' ? [WG.x0 + (i + s) * c, WG.y0 + j * c] : [WG.x0 + i * c, WG.y0 + (j + s) * c]; }
      return k;
    };
    var add = function (a, b) { var n = segs.length; segs.push([a, b]); (adj[a] = adj[a] || []).push(n); (adj[b] = adj[b] || []).push(n); };
    for (var j = 0; j < H - 1; j++) for (var i = 0; i < W - 1; i++) {
      var idx = (f[j * W + i] > L ? 8 : 0) | (f[j * W + i + 1] > L ? 4 : 0) | (f[(j + 1) * W + i + 1] > L ? 2 : 0) | (f[(j + 1) * W + i] > L ? 1 : 0);
      if (idx === 0 || idx === 15) continue;
      var T = function () { return ep('h', i, j); }, R = function () { return ep('v', i + 1, j); }, B = function () { return ep('h', i, j + 1); }, Lf = function () { return ep('v', i, j); };
      switch (idx) {
        case 1: case 14: add(Lf(), B()); break;
        case 2: case 13: add(B(), R()); break;
        case 3: case 12: add(Lf(), R()); break;
        case 4: case 11: add(T(), R()); break;
        case 6: case 9: add(T(), B()); break;
        case 7: case 8: add(Lf(), T()); break;
        case 5: add(Lf(), B()); add(T(), R()); break;
        case 10: add(Lf(), T()); add(B(), R()); break;
      }
    }
    var used = new Uint8Array(segs.length), d = '';
    for (var s0 = 0; s0 < segs.length; s0++) {
      if (used[s0]) continue;
      used[s0] = 1;
      var loop = [segs[s0][0]], cur = segs[s0][1], guard = 0;
      while (cur !== loop[0] && guard++ < 200000) {
        loop.push(cur);
        var nx = (adj[cur] || []).filter(function (n) { return !used[n]; })[0];
        if (nx == null) break;
        used[nx] = 1;
        cur = segs[nx][0] === cur ? segs[nx][1] : segs[nx][0];
      }
      if (loop.length < 5) continue;
      var P2 = loop.map(function (k) { return pt[k]; }), n2 = P2.length;
      var bx0 = 1e9, bx1 = -1e9, by0 = 1e9, by1 = -1e9;
      P2.forEach(function (p) { bx0 = Math.min(bx0, p[0]); bx1 = Math.max(bx1, p[0]); by0 = Math.min(by0, p[1]); by1 = Math.max(by1, p[1]); });
      if (bx1 - bx0 < 44 && by1 - by0 < 44) continue; // 잡음으로 생긴 작은 섬/웅덩이는 버린다
      var mid = function (a, b) { return ((a[0] + b[0]) / 2).toFixed(1) + ',' + ((a[1] + b[1]) / 2).toFixed(1); };
      d += 'M' + mid(P2[n2 - 1], P2[0]);
      for (var q = 0; q < n2; q++) d += 'Q' + P2[q][0].toFixed(1) + ',' + P2[q][1].toFixed(1) + ' ' + mid(P2[q], P2[(q + 1) % n2]);
      d += 'Z';
    }
    return d;
  }
  function worldRibbon(pts, w0, w1, flare, seed) { // 강 -- 가늘게 시작해 점점 넓어지고, 하구에서 벌어진다
    var c = worldCr(pts, 0.1), Lp = [], Rp = [];
    c.forEach(function (p, i) {
      var q = c[Math.min(c.length - 1, i + 1)], o = c[Math.max(0, i - 1)], dx = q[0] - o[0], dy = q[1] - o[1], dd = Math.sqrt(dx * dx + dy * dy) || 1;
      var t = i / (c.length - 1), w = (w0 + (w1 - w0) * Math.pow(t, 1.3)) * (0.82 + 0.36 * wNoise(i * 0.35, seed)) + (flare && t > 0.88 ? (t - 0.88) * 170 : 0);
      Lp.push([p[0] - dy / dd * w / 2, p[1] + dx / dd * w / 2]); Rp.push([p[0] + dy / dd * w / 2, p[1] - dx / dd * w / 2]);
    });
    var all = Lp.concat(Rp.reverse());
    return 'M' + all.map(function (v) { return v[0].toFixed(1) + ',' + v[1].toFixed(1); }).join('L') + 'Z';
  }
  function worldBlob(cx, cy, rx, ry, seed) { // 호수 -- 일그러진 타원
    var d = '';
    for (var a = 0, k = 0; a < Math.PI * 2 - 1e-6; a += Math.PI / 12, k++) { var m = 0.85 + 0.3 * wNoise(k * 0.7, seed); d += (k ? 'L' : 'M') + (cx + Math.cos(a) * rx * m).toFixed(1) + ',' + (cy + Math.sin(a) * ry * m).toFixed(1); }
    return d + 'Z';
  }
  function worldMountSvg() { // 옛 지도식 산(먹선 봉우리 + 한쪽 빗금)
    return WORLD_MOUNTS.map(function (m) {
      var x = m[0], y = m[1], h = 14, w = 12;
      return '<path d="M' + (x - w) + ',' + (y + 6) + ' Q' + (x - 4) + ',' + (y - h + 4) + ' ' + x + ',' + (y - h) + ' Q' + (x + 5) + ',' + (y - h + 5) + ' ' + (x + w) + ',' + (y + 6) + '" fill="#e2cfa0" stroke="#5a3e22" stroke-width="1.3"/>'
          + '<path d="M' + (x + 2) + ',' + (y - h + 4) + 'L' + (x + 3) + ',' + (y + 4) + 'M' + (x + 5) + ',' + (y - h + 8) + 'L' + (x + 6) + ',' + (y + 5) + 'M' + (x + 8) + ',' + (y - 3) + 'L' + (x + 9) + ',' + (y + 5) + '" stroke="#7a5a36" stroke-width=".8"/>';
    }).join('');
  }
  /** 기와지붕 하나(처마 끝이 들리고 오른쪽 옆면) -- 아래 너비 rw(처마), 위 너비 rw2, 아래 y, 위 y. */
  function worldRoof(bx, rw, rw2, ryb, rtop) {
    var ddx = 16, ddy = -9, s = '';
    s += '<path d="M' + (bx + rw / 2) + ',' + (ryb - 6) + 'l' + ddx + ',' + ddy + 'L' + (bx + rw2 / 2 + ddx) + ',' + (rtop + ddy) + 'L' + (bx + rw2 / 2) + ',' + rtop + 'Z" fill="#26323e"/>';
    s += '<path d="M' + (bx - rw / 2) + ',' + (ryb - 6) + 'Q' + bx + ',' + (ryb + 6) + ' ' + (bx + rw / 2) + ',' + (ryb - 6) + 'L' + (bx + rw2 / 2) + ',' + rtop + 'L' + (bx - rw2 / 2) + ',' + rtop + 'Z" fill="#3a4a5a" stroke="#1a222a" stroke-width="1"/>';
    for (var tl = -2; tl <= 2; tl++) s += '<path d="M' + (bx + tl * rw / 6) + ',' + (ryb - 2 - Math.abs(tl) * 1.2) + 'L' + (bx + tl * rw2 / 6) + ',' + (rtop + 1) + '" stroke="#5a6a7a" stroke-width=".7"/>';
    return s;
  }
  /** 다도해 본섬 신궁 -- 3층 탑(1·2·3층) + 오른쪽 옆 단층 보스방(닌자 두목의 방, 실제 맵에서도 신궁 동쪽 문). 층 띠/인원은 worldHtml. 들어가는 곳 = 탑 꼭대기. */
  function worldShrineSvg(P, entry) {
    if (!P.shrine) return '';
    var bx = P.shrine.x, gy = P.shrine.y + WORLD_SHRINE_H / 2, U = WORLD_SHRINE_U, h = WORLD_SHRINE_H, ddx = 16, ddy = -9, s = '', ws = WORLD_SHRINE_W;
    var hx = P.shrineboss ? P.shrineboss.x : bx + 130, hw = 96, xr0 = hx + hw / 2 + 14;
    s += '<ellipse cx="' + ((bx + hx) / 2 + 8) + '" cy="' + (gy + 12) + '" rx="' + ((xr0 - bx) / 2 + 90) + '" ry="15" fill="#5a3e22" opacity=".18"/>'; // 그림자
    s += '<path d="M' + (bx - 80) + ',' + gy + 'H' + xr0 + 'l' + ddx + ',' + ddy + 'v10l' + (-ddx) + ',' + (-ddy) + 'H' + (bx - 80) + 'z" fill="#8f8270"/><rect x="' + (bx - 80) + '" y="' + gy + '" width="' + (xr0 - bx + 80) + '" height="10" fill="#b0a48c" stroke="#5a4a36" stroke-width=".8"/>'; // 돌 기단(탑과 보스방 함께)
    [bx, hx].forEach(function (sx) { s += '<rect x="' + (sx - 14) + '" y="' + (gy + 10) + '" width="28" height="4" fill="#a09478"/><rect x="' + (sx - 18) + '" y="' + (gy + 14) + '" width="36" height="4" fill="#968a70"/>'; }); // 계단
    var hy = gy, hyt = hy - h; // 보스방(단층, 어두운 앞면, 큰 팔작지붕)
    s += '<path d="M' + (hx + hw / 2) + ',' + hyt + 'l' + ddx + ',' + ddy + 'V' + (hy + ddy) + 'L' + (hx + hw / 2) + ',' + hy + 'Z" fill="#4a2a34" stroke="#2a1018" stroke-width=".8"/>';
    s += '<rect x="' + (hx - hw / 2) + '" y="' + hyt + '" width="' + hw + '" height="' + h + '" fill="#3a2430" stroke="#2a1018" stroke-width="1"/>';
    for (var pi = 0; pi < 4; pi++) s += '<rect x="' + (hx - hw / 2 + 2 + pi * (hw - 9) / 3) + '" y="' + hyt + '" width="5" height="' + h + '" fill="#8a1e1e"/>';
    s += worldRoof(hx, hw + 34, 30, hyt + 4, hyt - 24);
    s += '<path d="M' + (hx + hw / 2 + 10) + ',' + (hyt - 6) + 'v26" stroke="#2a1a14" stroke-width="1.4"/><path d="M' + (hx + hw / 2 + 10) + ',' + (hyt - 6) + 'h12v14h-12z" fill="#c0281e"/>'; // 붉은 깃발
    for (var k = 0; k < 3; k++) { // 3층 탑
      var w = ws[k], yb = gy - k * U, yt = yb - h, xl = bx - w / 2, xr = bx + w / 2;
      s += '<path d="M' + xr + ',' + yt + 'l' + ddx + ',' + ddy + 'V' + (yb + ddy) + 'L' + xr + ',' + yb + 'Z" fill="#b89a70" stroke="#4a3020" stroke-width=".8"/>';
      s += '<rect x="' + xl + '" y="' + yt + '" width="' + w + '" height="' + h + '" fill="#ead6b0" stroke="#4a3020" stroke-width="1"/>';
      s += '<rect x="' + (xl + 2) + '" y="' + yt + '" width="5" height="' + h + '" fill="#b8322a"/><rect x="' + (xr - 7) + '" y="' + yt + '" width="5" height="' + h + '" fill="#b8322a"/>';
      for (var wi = 0; wi < 2; wi++) { var wx = wi ? xr - 20 : xl + 10; s += '<rect x="' + wx + '" y="' + (yt + 8) + '" width="10" height="' + (h - 16) + '" fill="#5a2a1a"/><path d="M' + (wx + 5) + ',' + (yt + 8) + 'v' + (h - 16) + 'M' + wx + ',' + (yt + h / 2) + 'h10" stroke="#c8a060" stroke-width=".8"/>'; }
      s += worldRoof(bx, w + 30, k === 2 ? 6 : ws[k + 1] - 6, yt + 4, yt - (k === 2 ? 26 : 10));
    }
    var topY = gy - 2 * U - h - 26;
    s += '<path d="M' + bx + ',' + topY + 'v-22" stroke="#d8b04a" stroke-width="2.4"/>';
    for (var ri = 0; ri < 4; ri++) s += '<ellipse cx="' + bx + '" cy="' + (topY - 4 - ri * 4.5) + '" rx="' + (5 - ri) + '" ry="1.4" fill="#d8b04a"/>'; // 상륜
    s += '<circle cx="' + bx + '" cy="' + (topY - 24) + '" r="2.6" fill="#ffe08a"/>';
    var tx = bx - 64, ty = gy + 4; // 기단 앞 붉은 문(도리이)
    s += '<rect x="' + (tx - 12) + '" y="' + (ty - 2) + '" width="3" height="26" fill="#c8402a"/><rect x="' + (tx + 9) + '" y="' + (ty - 2) + '" width="3" height="26" fill="#c8402a"/>'
        + '<path d="M' + (tx - 17) + ',' + (ty - 5) + 'Q' + tx + ',' + (ty - 1) + ' ' + (tx + 17) + ',' + (ty - 5) + 'v3Q' + tx + ',' + (ty + 2) + ' ' + (tx - 17) + ',' + (ty - 2) + 'Z" fill="#2a1a14"/><rect x="' + (tx - 13) + '" y="' + (ty + 3) + '" width="26" height="2.4" fill="#c8402a"/>';
    s += '<text x="' + ((bx + hx) / 2) + '" y="' + (gy + 36) + '" text-anchor="middle" class="wn">⛩️ 신궁</text>';
    entry.shrine = [bx, topY - 28];
    return s;
  }
  /** 동굴 단면 -- 땅 위 바위 언덕의 굴 입구, 땅속으로 층(B1~)마다 방이 이어진다. ice = 설산 동굴(눈 덮인 얼음). 들어가는 곳 = 굴 입구. */
  function worldCaveSvg(P, ids, ice, label, key, entry) {
    var a = P[ids[0]], z = P[ids[ids.length - 1]];
    if (!a || !z) return '';
    var x = a.x, y0 = a.y - 30, y1 = z.y + 24, s = '', rock = ice ? '#dfeaf2' : '#b89a6a', ink = ice ? '#4a6a80' : '#5a3e22';
    var soil = ice ? '#9ab8ca' : '#8a6e4e', strata = ice ? '#7f9fb4' : '#735a3e', room = ice ? '#22384a' : '#2a1e16', tun = ice ? '#2e4a5e' : '#3a2a1e';
    s += '<path d="M' + (x - 64) + ',' + y0 + 'Q' + (x - 70) + ',' + ((y0 + y1) / 2) + ' ' + (x - 60) + ',' + y1 + 'Q' + x + ',' + (y1 + 14) + ' ' + (x + 60) + ',' + y1 + 'Q' + (x + 70) + ',' + ((y0 + y1) / 2) + ' ' + (x + 64) + ',' + y0 + 'Z" fill="' + soil + '" stroke="' + ink + '" stroke-width="1.2"/>'; // 땅속
    for (var yy = y0 + 12; yy < y1 - 4; yy += 13) s += '<path d="M' + (x - 62) + ',' + yy + 'Q' + (x - 30) + ',' + (yy - 4) + ' ' + x + ',' + yy + 'T' + (x + 62) + ',' + yy + '" fill="none" stroke="' + strata + '" stroke-width=".9" opacity=".7"/>'; // 지층
    var px = x - 4, py = y0; // 굴 입구
    var prev = [px, py];
    ids.forEach(function (id, i) {
      var p = P[id], side = i % 2 ? 1 : -1, tx = x + side * 26;
      s += '<path d="M' + prev[0] + ',' + prev[1] + 'Q' + tx + ',' + ((prev[1] + p.y) / 2) + ' ' + tx + ',' + p.y + '" fill="none" stroke="' + tun + '" stroke-width="7" stroke-linecap="round"/>'; // 굴
      prev = [x - side * 26, p.y];
    });
    ids.forEach(function (id, i) { var p = P[id]; s += '<path d="' + worldBlob(x + (i % 2 ? 4 : -4), p.y, 50, 15, 30 + i) + '" fill="' + room + '" stroke="' + ink + '" stroke-width=".8"/>'; }); // 층마다 방
    var gr = wRnd(ice ? 41 : 43);
    for (var g = 0; g < 14; g++) { var gx = x - 56 + gr() * 112, gyy = y0 + 8 + gr() * (y1 - y0 - 16); s += '<path d="M' + gx + ',' + (gyy - 2.5) + 'l2,2.5l-2,2.5l-2,-2.5z" fill="' + (ice ? '#c8f4ff' : ['#ffd84a', '#8ad8ff', '#ff8aa8', '#c0c0c0'][g % 4]) + '" opacity=".9"/>'; } // 광석/얼음 결정
    s += '<path d="M' + (x - 68) + ',' + (y0 + 1) + 'C' + (x - 52) + ',' + (y0 - 30) + ' ' + (x - 20) + ',' + (y0 - 42) + ' ' + x + ',' + (y0 - 42) + 'C' + (x + 26) + ',' + (y0 - 42) + ' ' + (x + 52) + ',' + (y0 - 24) + ' ' + (x + 68) + ',' + (y0 + 1) + 'Z" fill="' + rock + '" stroke="' + ink + '" stroke-width="1.3"/>'; // 바위 언덕
    if (ice) s += '<path d="M' + (x - 30) + ',' + (y0 - 34) + 'Q' + x + ',' + (y0 - 48) + ' ' + (x + 30) + ',' + (y0 - 32) + 'l-6,7l-6,-4l-7,6l-6,-5l-7,6l-6,-6l-7,5z" fill="#ffffff" stroke="#9ab8ca" stroke-width=".7"/>'; // 눈
    for (var hk = 0; hk < 5; hk++) s += '<path d="M' + (x + 22 + hk * 8) + ',' + (y0 - 30 + hk * 6) + 'l6,9" stroke="' + ink + '" stroke-width=".8" opacity=".7"/>'; // 빗금
    s += '<path d="M' + (px - 13) + ',' + y0 + 'Q' + (px - 13) + ',' + (y0 - 20) + ' ' + px + ',' + (y0 - 20) + 'Q' + (px + 13) + ',' + (y0 - 20) + ' ' + (px + 13) + ',' + y0 + 'Z" fill="#14100c" stroke="' + ink + '" stroke-width="1"/>'; // 굴 입구
    if (!ice) s += '<path d="M' + (px - 13) + ',' + (y0 - 1) + 'V' + (y0 - 14) + 'M' + (px + 13) + ',' + (y0 - 1) + 'V' + (y0 - 14) + 'M' + (px - 15) + ',' + (y0 - 15) + 'H' + (px + 15) + '" stroke="#8a5a30" stroke-width="2.2"/>'; // 갱도 나무틀
    s += '<path d="M' + (x - 70) + ',' + y0 + 'H' + (x + 70) + '" stroke="' + (ice ? '#ffffff' : '#6a8a3a') + '" stroke-width="2.4" opacity=".8"/>'; // 땅 위
    s += '<text x="' + x + '" y="' + (y1 + 26) + '" text-anchor="middle" class="wn">' + label + '</text>';
    entry[key] = [px, y0 - 10];
    return s;
  }
  /** 설산 -- 설원에서 봉우리까지 칸들이 오르는 큰 산(눈 덮인 위쪽, 오른쪽 그늘, 옆 봉우리). */
  function worldSnowMtnSvg(P) {
    if (!P.snowtown || !P.snowpeak || !P.snow1) return '';
    var x = P.snowtown.x, pk = P.snowpeak.y - 48, base = P.snow1.y + 34, s = '';
    var side = function (cx, top, hw, bot) {
      return '<path d="M' + (cx - hw) + ',' + bot + 'L' + (cx - hw * 0.45) + ',' + (top + (bot - top) * 0.45) + 'L' + cx + ',' + top + 'L' + (cx + hw * 0.5) + ',' + (top + (bot - top) * 0.5) + 'L' + (cx + hw) + ',' + bot + 'Z" fill="#c4b8a6" stroke="#5a3e22" stroke-width="1.2"/>'
          + '<path d="M' + cx + ',' + top + 'L' + (cx + hw) + ',' + bot + 'H' + (cx + hw * 0.15) + 'Z" fill="#5a4a3a" opacity=".2"/>'
          + '<path d="M' + (cx - hw * 0.26) + ',' + (top + (bot - top) * 0.26) + 'L' + cx + ',' + top + 'L' + (cx + hw * 0.27) + ',' + (top + (bot - top) * 0.27) + 'l-7,6l-6,-5l-7,7l-6,-6l-7,5z" fill="#f6f9fb" stroke="#8aa0b0" stroke-width=".7"/>';
    };
    s += side(x - 118, base - 150, 70, base); // 옆 봉우리
    s += side(x + 128, base - 120, 66, base);
    s += '<path d="M' + (x - 158) + ',' + base + 'C' + (x - 128) + ',' + (base - 80) + ' ' + (x - 98) + ',' + (base - 120) + ' ' + (x - 66) + ',' + (base - 200) + 'L' + (x - 38) + ',' + (pk + 90) + 'L' + (x - 22) + ',' + (pk + 50) + 'L' + x + ',' + pk
        + 'L' + (x + 26) + ',' + (pk + 46) + 'L' + (x + 48) + ',' + (pk + 96) + 'L' + (x + 76) + ',' + (base - 190) + 'C' + (x + 104) + ',' + (base - 120) + ' ' + (x + 132) + ',' + (base - 70) + ' ' + (x + 160) + ',' + base + 'Z" fill="#cfc4b2" stroke="#5a3e22" stroke-width="1.5"/>'; // 큰 산
    s += '<path d="M' + x + ',' + pk + 'L' + (x + 26) + ',' + (pk + 46) + 'L' + (x + 48) + ',' + (pk + 96) + 'L' + (x + 76) + ',' + (base - 190) + 'C' + (x + 104) + ',' + (base - 120) + ' ' + (x + 132) + ',' + (base - 70) + ' ' + (x + 160) + ',' + base + 'H' + (x + 30) + 'Z" fill="#5a4a3a" opacity=".16"/>'; // 오른쪽 그늘
    var sl = pk + 150, cap = 'M' + x + ',' + pk + 'L' + (x + 26) + ',' + (pk + 46) + 'L' + (x + 48) + ',' + (pk + 96) + 'L' + (x + 62) + ',' + sl;
    for (var k = 0, sx = x + 62; sx > x - 50; sx -= 9, k++) cap += 'L' + (sx - 4.5) + ',' + (sl + (k % 2 ? -5 : 6));
    s += '<path d="' + cap + 'L' + (x - 55) + ',' + sl + 'L' + (x - 38) + ',' + (pk + 90) + 'L' + (x - 22) + ',' + (pk + 50) + 'Z" fill="#f8fbfd" stroke="#8aa0b0" stroke-width=".8"/>'; // 눈 덮인 위쪽
    for (var hk = 0; hk < 9; hk++) { var hy = base - 30 - hk * 22, hx = x + 40 + hk * 3; s += '<path d="M' + hx + ',' + hy + 'l9,12M' + (hx + 12) + ',' + (hy + 4) + 'l8,11" stroke="#5a3e22" stroke-width=".8" opacity=".5"/>'; } // 빗금
    for (var t = 0; t < 7; t++) { var tx = x - 140 + t * 44 + (t % 2) * 8, ty = base - 6 - (t % 3) * 5; s += '<path d="M' + tx + ',' + (ty - 12) + 'l-5,10h10z" fill="#4a6a4a" stroke="#2a3a2a" stroke-width=".6"/><path d="M' + tx + ',' + (ty - 16) + 'l-3,6h6z" fill="#ffffff"/>'; } // 기슭 전나무
    return s;
  }
  /** 요새 도시의 거대한 탑 -- 하늘로 솟은 돌탑(층 테두리, 창, 둘레 구름, 꼭대기 빛). 띠 = 탑 입구. */
  function worldTowerSvg(P) {
    if (!P.towergate) return '';
    var x = P.towergate.x, yb = P.towergate.y - 10, s = '', ddx = 9, ddy = -5, n = 7, segH = 14;
    s += '<ellipse cx="' + (x + 4) + '" cy="' + (yb + 12) + '" rx="40" ry="8" fill="#5a3e22" opacity=".2"/>';
    for (var k = 0; k < n; k++) {
      var w = 48 - k * 3.2, y2 = yb - k * segH, y1 = y2 - segH, xl = x - w / 2, xr = x + w / 2;
      s += '<path d="M' + xr + ',' + y1 + 'l' + ddx + ',' + ddy + 'V' + (y2 + ddy) + 'L' + xr + ',' + y2 + 'Z" fill="#9a907e" stroke="#4a4036" stroke-width=".7"/>';
      s += '<rect x="' + xl + '" y="' + y1 + '" width="' + w + '" height="' + segH + '" fill="' + (k % 2 ? '#d8d0c0' : '#cfc6b4') + '" stroke="#4a4036" stroke-width=".8"/>';
      s += '<rect x="' + (xl - 1.5) + '" y="' + (y1 - 1.5) + '" width="' + (w + 3) + '" height="3" fill="#b8ae9a" stroke="#4a4036" stroke-width=".5"/>'; // 층 테두리
      if (k > 0) for (var wi = -1; wi <= 1; wi += 2) s += '<rect x="' + (x + wi * w / 4 - 1.5) + '" y="' + (y1 + 4) + '" width="3" height="6" fill="#2a2430"/>'; // 창
    }
    var top = yb - n * segH;
    s += '<path d="M' + (x - 13) + ',' + top + 'L' + x + ',' + (top - 22) + 'L' + (x + 13) + ',' + top + 'Z" fill="#6a5a8a" stroke="#3a2a4a" stroke-width=".8"/>';
    s += '<circle cx="' + x + '" cy="' + (top - 26) + '" r="7" fill="#bfe8ff" opacity=".35"/><circle cx="' + x + '" cy="' + (top - 26) + '" r="3.2" fill="#e8f8ff"/>'; // 꼭대기 빛
    s += '<path d="M' + (x - 34) + ',' + (yb - 50) + 'q8,-7 16,-2q6,-6 14,-1q8,-5 16,1q8,-3 12,3" fill="none" stroke="#ffffff" stroke-width="3" stroke-linecap="round" opacity=".85"/>'; // 둘레 구름
    s += '<path d="M' + (x - 10) + ',' + yb + 'V' + (yb - 9) + 'Q' + x + ',' + (yb - 17) + ' ' + (x + 10) + ',' + (yb - 9) + 'V' + yb + 'Z" fill="#2a2018"/>'; // 문
    return s;
  }
  /** [2026-11-20] "도시 미니맵이 아니라 도시 같은 건축물/상징성을" -- 왕도 아스테리아: 성벽(앞/옆/뒤, 총안) + 네 모서리 원탑 + 정문 문루, 안쪽 빼곡한 붉은 지붕 집들, 가운데 푸른 돔 궁전과 첨탑, 대성당 첨탑. */
  function worldCitySvg(cx, cy) {
    var s = '', rnd = wRnd(77), i;
    var crenel = function (x0, x1, y, col) { var r = ''; for (var x = x0; x < x1 - 2; x += 5) r += '<rect x="' + x + '" y="' + (y - 3) + '" width="3" height="3" fill="' + col + '"/>'; return r; };
    var house = function (x, y, w, roof) { // 작은 집: 벽 + 박공 지붕 + 옆면
      return '<path d="M' + (x + w / 2) + ',' + (y - 6) + 'l3,-2v6l-3,2z" fill="#b8a888"/><rect x="' + (x - w / 2) + '" y="' + (y - 6) + '" width="' + w + '" height="6" fill="#efe4c8" stroke="#6a5038" stroke-width=".4"/>'
          + '<path d="M' + (x - w / 2 - 1) + ',' + (y - 6) + 'L' + (x - w / 4) + ',' + (y - 11) + 'H' + (x + w / 4 + 2) + 'L' + (x + w / 2 + 3) + ',' + (y - 8) + 'L' + (x + w / 2 + 1) + ',' + (y - 6) + 'Z" fill="' + roof + '" stroke="#5a2a20" stroke-width=".4"/>';
    };
    var tower = function (x, y, r, h, roof) { // 원탑: 몸통 + 원뿔 지붕
      return '<rect x="' + (x - r) + '" y="' + (y - h) + '" width="' + (2 * r) + '" height="' + h + '" fill="#cdbf9e" stroke="#5a4632" stroke-width=".7"/><ellipse cx="' + x + '" cy="' + y + '" rx="' + r + '" ry="' + (r * 0.4) + '" fill="#b8a888"/>'
          + '<rect x="' + (x + r * 0.3) + '" y="' + (y - h) + '" width="' + (r * 0.7) + '" height="' + h + '" fill="#000" opacity=".12"/>'
          + '<path d="M' + (x - r - 2) + ',' + (y - h) + 'L' + x + ',' + (y - h - r * 2.6) + 'L' + (x + r + 2) + ',' + (y - h) + 'Z" fill="' + roof + '" stroke="#5a2a20" stroke-width=".6"/><rect x="' + (x - 1) + '" y="' + (y - h + 5) + '" width="2" height="4" fill="#2a2018"/>';
    };
    var flag = function (x, y, col) { return '<path d="M' + x + ',' + y + 'v-10" stroke="#3a2a1a" stroke-width=".8"/><path d="M' + x + ',' + (y - 10) + 'h7l-2,2.5l2,2.5h-7z" fill="' + col + '"/>'; };
    s += '<ellipse cx="' + (cx + 6) + '" cy="' + (cy + 40) + '" rx="72" ry="12" fill="#3a2614" opacity=".18"/>';
    s += '<path d="M' + (cx - 60) + ',' + (cy + 34) + 'H' + (cx + 60) + 'L' + (cx + 52) + ',' + (cy - 32) + 'H' + (cx - 52) + 'Z" fill="#d8caa2"/>'; // 성안 바닥
    s += '<rect x="' + (cx - 52) + '" y="' + (cy - 42) + '" width="104" height="10" fill="#b8a888" stroke="#5a4632" stroke-width=".7"/>' + crenel(cx - 52, cx + 52, cy - 42, '#b8a888'); // 뒤 성벽
    s += '<path d="M' + (cx - 60) + ',' + (cy + 22) + 'L' + (cx - 52) + ',' + (cy - 42) + 'V' + (cy - 32) + 'L' + (cx - 60) + ',' + (cy + 34) + 'Z" fill="#a89878" stroke="#5a4632" stroke-width=".6"/>'; // 왼쪽 성벽
    s += '<path d="M' + (cx + 60) + ',' + (cy + 22) + 'L' + (cx + 52) + ',' + (cy - 42) + 'V' + (cy - 32) + 'L' + (cx + 60) + ',' + (cy + 34) + 'Z" fill="#8f8064" stroke="#5a4632" stroke-width=".6"/>'; // 오른쪽 성벽
    s += tower(cx - 52, cy - 32, 5, 16, '#a8483a') + tower(cx + 52, cy - 32, 5, 16, '#a8483a'); // 뒤 모서리 탑
    var rows = [cy - 22, cy - 12, cy - 1, cy + 10, cy + 21, cy + 30];
    rows.forEach(function (ry, ri) { // 집들(가운데 궁전 자리는 비운다)
      var half = 46 + ri * 1.5;
      for (var hx = cx - half; hx <= cx + half; hx += 10 + rnd() * 3) {
        if (Math.abs(hx - cx) < 30 && ry < cy + 16) continue;
        if (Math.abs(hx - cx) < 9 && ry >= cy + 16) continue; // 정문에서 궁전까지 큰길
        s += house(hx + (rnd() - 0.5) * 2, ry + (rnd() - 0.5) * 2, 7 + rnd() * 3, rnd() < 0.7 ? '#a8483a' : rnd() < 0.5 ? '#8a3a2e' : '#6a5a4a');
      }
    });
    s += '<rect x="' + (cx + 30) + '" y="' + (cy - 30) + '" width="9" height="16" fill="#e8ddc4" stroke="#5a4632" stroke-width=".6"/><path d="M' + (cx + 29) + ',' + (cy - 30) + 'L' + (cx + 34.5) + ',' + (cy - 52) + 'L' + (cx + 40) + ',' + (cy - 30) + 'Z" fill="#6a5a7a" stroke="#3a2a4a" stroke-width=".6"/><path d="M' + (cx + 34.5) + ',' + (cy - 52) + 'v-5M' + (cx + 32.5) + ',' + (cy - 55) + 'h4" stroke="#d8b04a" stroke-width="1"/>'; // 대성당 첨탑
    // 궁전: 넓은 본관 + 양쪽 탑 + 가운데 큰 탑의 푸른 돔
    var pyb = cy + 12;
    s += '<path d="M' + (cx + 26) + ',' + (pyb - 18) + 'l5,-3v18l-5,3z" fill="#c8bca4" stroke="#5a4632" stroke-width=".5"/><rect x="' + (cx - 26) + '" y="' + (pyb - 18) + '" width="52" height="18" fill="#f2ead6" stroke="#5a4632" stroke-width=".8"/>';
    for (i = 0; i < 6; i++) s += '<rect x="' + (cx - 22 + i * 8) + '" y="' + (pyb - 13) + '" width="3" height="6" rx="1.5" fill="#3a4a6a"/>';
    s += '<path d="M' + (cx - 28) + ',' + (pyb - 18) + 'L' + (cx - 22) + ',' + (pyb - 25) + 'H' + (cx + 24) + 'L' + (cx + 31) + ',' + (pyb - 21) + 'L' + (cx + 28) + ',' + (pyb - 18) + 'Z" fill="#3a5a8a" stroke="#1a2a4a" stroke-width=".6"/>';
    s += tower(cx - 22, pyb - 18, 5, 16, '#3a5a8a') + tower(cx + 22, pyb - 18, 5, 16, '#3a5a8a');
    s += '<rect x="' + (cx - 8) + '" y="' + (pyb - 44) + '" width="16" height="26" fill="#f2ead6" stroke="#5a4632" stroke-width=".8"/><rect x="' + (cx + 2) + '" y="' + (pyb - 44) + '" width="6" height="26" fill="#000" opacity=".1"/>';
    s += '<rect x="' + (cx - 2) + '" y="' + (pyb - 38) + '" width="4" height="7" rx="2" fill="#3a4a6a"/>';
    s += '<path d="M' + (cx - 10) + ',' + (pyb - 44) + 'Q' + (cx - 11) + ',' + (pyb - 58) + ' ' + cx + ',' + (pyb - 62) + 'Q' + (cx + 11) + ',' + (pyb - 58) + ' ' + (cx + 10) + ',' + (pyb - 44) + 'Z" fill="#4a72a8" stroke="#1a2a4a" stroke-width=".7"/><path d="M' + (cx - 4) + ',' + (pyb - 50) + 'Q' + (cx - 3) + ',' + (pyb - 57) + ' ' + cx + ',' + (pyb - 59) + '" stroke="#bfe0ff" stroke-width="1.2" fill="none"/>'; // 푸른 돔
    s += '<path d="M' + cx + ',' + (pyb - 62) + 'v-8" stroke="#d8b04a" stroke-width="1.6"/><circle cx="' + cx + '" cy="' + (pyb - 71) + '" r="2" fill="#ffd84a"/>';
    s += flag(cx - 22, pyb - 47, '#c0281e') + flag(cx + 22, pyb - 47, '#c0281e');
    // 앞 성벽 + 정문 문루 + 앞 모서리 탑
    s += '<rect x="' + (cx - 60) + '" y="' + (cy + 22) + '" width="120" height="12" fill="#c8b898" stroke="#5a4632" stroke-width=".8"/>' + crenel(cx - 60, cx + 60, cy + 22, '#c8b898');
    for (i = -50; i <= 50; i += 12) s += '<path d="M' + (cx + i) + ',' + (cy + 24) + 'v10" stroke="#a89878" stroke-width=".5"/>';
    s += '<rect x="' + (cx - 13) + '" y="' + (cy + 12) + '" width="26" height="22" fill="#d8caa8" stroke="#5a4632" stroke-width=".8"/>' + crenel(cx - 13, cx + 13, cy + 12, '#d8caa8'); // 문루
    s += '<path d="M' + (cx - 6) + ',' + (cy + 34) + 'V' + (cy + 24) + 'Q' + cx + ',' + (cy + 18) + ' ' + (cx + 6) + ',' + (cy + 24) + 'V' + (cy + 34) + 'Z" fill="#2a2018"/><path d="M' + (cx - 5) + ',' + (cy + 25) + 'h10M' + (cx - 5) + ',' + (cy + 29) + 'h10M' + (cx - 2) + ',' + (cy + 22) + 'v12M' + (cx + 2) + ',' + (cy + 22) + 'v12" stroke="#8a7a5a" stroke-width=".6"/>';
    s += flag(cx - 10, cy + 9, '#3a5a8a') + flag(cx + 10, cy + 9, '#3a5a8a');
    s += tower(cx - 60, cy + 34, 7, 24, '#a8483a') + tower(cx + 60, cy + 34, 7, 24, '#a8483a');
    return s;
  }
  /** [2026-11-20] 요새 도시 엘리시온 -- 물 위의 둥근 요새: 해자, 둥근 성벽(뒤/앞), 여섯 보루 망루, 가운데 높은 아성(깃발), 정문과 해자 다리. */
  function worldFortSvg(cx, cy) {
    var s = '', rx = 44, ry = 26, oy = cy + 4, H = 11, i;
    var arc = function (a0, a1, dy) { var p = []; for (var a = a0; a <= a1 + 1e-6; a += Math.PI / 24) p.push([cx + Math.cos(a) * rx, oy + Math.sin(a) * ry + (dy || 0)]); return p; };
    var poly = function (pts) { return pts.map(function (v) { return v[0].toFixed(1) + ',' + v[1].toFixed(1); }).join(' '); };
    var bastion = function (x, y) { // 네모 보루 망루(총안)
      var r = '<rect x="' + (x - 7) + '" y="' + (y - 20) + '" width="14" height="20" fill="#a8a092" stroke="#4a4238" stroke-width=".7"/><rect x="' + (x + 2) + '" y="' + (y - 20) + '" width="5" height="20" fill="#000" opacity=".13"/>';
      for (var k = 0; k < 3; k++) r += '<rect x="' + (x - 7 + k * 5) + '" y="' + (y - 23) + '" width="3" height="3" fill="#a8a092" stroke="#4a4238" stroke-width=".4"/>';
      return r + '<rect x="' + (x - 1) + '" y="' + (y - 15) + '" width="2" height="5" fill="#2a2430"/>';
    };
    s += '<ellipse cx="' + (cx + 5) + '" cy="' + (oy + 8) + '" rx="66" ry="40" fill="#3a2614" opacity=".15"/>';
    s += '<ellipse cx="' + cx + '" cy="' + oy + '" rx="62" ry="38" fill="#7fa8b8" stroke="#4a6a78" stroke-width="1"/>'; // 해자
    for (i = 0; i < 5; i++) s += '<path d="M' + (cx - 48 + i * 22) + ',' + (oy + 30 - (i % 2) * 58) + 'q4,-3 8,0" fill="none" stroke="#c8e0e8" stroke-width=".8"/>';
    s += '<ellipse cx="' + cx + '" cy="' + oy + '" rx="52" ry="31" fill="#c8bea0" stroke="#6a5a40" stroke-width=".8"/>'; // 둑
    s += '<ellipse cx="' + cx + '" cy="' + (oy - H) + '" rx="' + (rx - 3) + '" ry="' + (ry - 2) + '" fill="#b4ab94"/>'; // 성안 마당(성벽 위 높이에서 보이는 안쪽)
    var back = arc(Math.PI, 2 * Math.PI), backTop = arc(Math.PI, 2 * Math.PI, -H);
    s += '<polygon points="' + poly(back.concat(backTop.slice().reverse())) + '" fill="#8a8276" stroke="#4a4238" stroke-width=".7"/>'; // 뒤 성벽(안쪽 면)
    [Math.PI * 1.17, Math.PI * 1.5, Math.PI * 1.83].forEach(function (a) { s += bastion(cx + Math.cos(a) * rx, oy + Math.sin(a) * ry); });
    // 아성(가운데 높은 성채) + 두 작은 탑 + 깃발
    var kx = cx, kb = oy - 4;
    s += '<path d="M' + (kx + 13) + ',' + (kb - 34) + 'l6,-4v34l-6,4z" fill="#7a7266" stroke="#3a342c" stroke-width=".6"/><rect x="' + (kx - 13) + '" y="' + (kb - 34) + '" width="26" height="34" fill="#b8b0a2" stroke="#3a342c" stroke-width=".8"/>';
    for (i = 0; i < 5; i++) s += '<rect x="' + (kx - 13 + i * 5.5) + '" y="' + (kb - 38) + '" width="3.5" height="4" fill="#b8b0a2" stroke="#3a342c" stroke-width=".4"/>';
    s += '<rect x="' + (kx - 7) + '" y="' + (kb - 27) + '" width="2" height="6" fill="#2a2430"/><rect x="' + (kx + 5) + '" y="' + (kb - 27) + '" width="2" height="6" fill="#2a2430"/><rect x="' + (kx - 1) + '" y="' + (kb - 16) + '" width="2" height="6" fill="#2a2430"/>';
    [[-17, -26], [17, -26]].forEach(function (t) {
      var tx = kx + t[0], ty = kb + t[1] + 26;
      s += '<rect x="' + (tx - 4) + '" y="' + (ty - 40) + '" width="8" height="40" fill="#c4bcae" stroke="#3a342c" stroke-width=".6"/><path d="M' + (tx - 5) + ',' + (ty - 40) + 'L' + tx + ',' + (ty - 52) + 'L' + (tx + 5) + ',' + (ty - 40) + 'Z" fill="#4a5a7a" stroke="#2a3040" stroke-width=".6"/>';
    });
    s += '<path d="M' + kx + ',' + (kb - 38) + 'v-14" stroke="#3a2a1a" stroke-width="1"/><path d="M' + kx + ',' + (kb - 52) + 'h11l-3,3l3,3h-11z" fill="#3a5a9a" stroke="#1a2a4a" stroke-width=".4"/><circle cx="' + (kx + 4.5) + '" cy="' + (kb - 49) + '" r="1.4" fill="#ffd84a"/>';
    var front = arc(0, Math.PI), frontTop = arc(0, Math.PI, -H);
    s += '<polygon points="' + poly(front.concat(frontTop.slice().reverse())) + '" fill="#a8a092" stroke="#4a4238" stroke-width=".8"/>'; // 앞 성벽(바깥 면)
    frontTop.forEach(function (p, k) { if (k % 2 === 0 && k > 0 && k < frontTop.length - 1) s += '<rect x="' + (p[0] - 1.5) + '" y="' + (p[1] - 3) + '" width="3" height="3" fill="#a8a092" stroke="#4a4238" stroke-width=".3"/>'; });
    for (i = 1; i < front.length - 1; i += 3) s += '<path d="M' + front[i][0].toFixed(1) + ',' + front[i][1].toFixed(1) + 'v' + (-H) + '" stroke="#8a8276" stroke-width=".5"/>';
    s += '<rect x="' + (cx - 6) + '" y="' + (oy + ry + 12) + '" width="12" height="16" fill="#8a6a44" stroke="#4a3020" stroke-width=".6"/>'; // 해자 다리
    for (i = 0; i < 4; i++) s += '<path d="M' + (cx - 6) + ',' + (oy + ry + 15 + i * 4) + 'h12" stroke="#5a4028" stroke-width=".5"/>';
    s += '<path d="M' + (cx - 7) + ',' + (oy + ry + 1) + 'V' + (oy + ry - 7) + 'Q' + cx + ',' + (oy + ry - 13) + ' ' + (cx + 7) + ',' + (oy + ry - 7) + 'V' + (oy + ry + 1) + 'Z" fill="#2a2018"/>'; // 정문
    [Math.PI * 0.17, Math.PI * 0.5 - 0.42, Math.PI * 0.5 + 0.42, Math.PI * 0.83].forEach(function (a) { s += bastion(cx + Math.cos(a) * rx, oy + Math.sin(a) * ry + 2); });
    return s;
  }
  /** 바다마을 윤슬항 -- 땅이 아니라 물 위에 기둥을 박은 나무 부두와 수상 가옥, 등대. */
  function worldHarborSvg(P) {
    if (!P.seacity) return '';
    var x = P.seacity.x, y = P.seacity.y, s = '';
    for (var pi = -3; pi <= 3; pi++) s += '<rect x="' + (x + pi * 18 - 1.5) + '" y="' + (y + 16) + '" width="3" height="9" fill="#4a3020"/>'; // 기둥
    s += '<rect x="' + (x - 66) + '" y="' + (y - 18) + '" width="132" height="36" rx="2" fill="#a8784a" stroke="#4a3020" stroke-width="1.2"/>'; // 부두
    for (var pl = -60; pl < 66; pl += 8) s += '<path d="M' + (x + pl) + ',' + (y - 18) + 'v36" stroke="#7a5432" stroke-width=".7"/>';
    s += '<rect x="' + (x - 8) + '" y="' + (y + 18) + '" width="16" height="44" fill="#a8784a" stroke="#4a3020" stroke-width="1"/>'; // 잔교
    [[-48, -30], [-26, -32], [34, -30]].forEach(function (h) { // 수상 가옥
      var hx = x + h[0], hy = y + h[1];
      s += '<rect x="' + (hx - 9) + '" y="' + (hy - 2) + '" width="18" height="12" fill="#e8d8b0" stroke="#4a3020" stroke-width=".8"/><path d="M' + (hx - 12) + ',' + (hy - 1) + 'L' + hx + ',' + (hy - 11) + 'L' + (hx + 12) + ',' + (hy - 1) + 'Z" fill="#9a3a2a" stroke="#4a2018" stroke-width=".8"/>';
    });
    var lx = x + 70, ly = y - 4; // 등대
    s += '<path d="M' + (lx - 6) + ',' + (ly + 18) + 'L' + (lx - 4) + ',' + (ly - 22) + 'H' + (lx + 4) + 'L' + (lx + 6) + ',' + (ly + 18) + 'Z" fill="#f4efe2" stroke="#4a3020" stroke-width=".8"/>'
        + '<path d="M' + (lx - 5.5) + ',' + (ly + 6) + 'H' + (lx + 5.5) + 'L' + (lx + 5) + ',' + (ly - 2) + 'H' + (lx - 5) + 'Z" fill="#c0281e"/><rect x="' + (lx - 5) + '" y="' + (ly - 30) + '" width="10" height="8" fill="#ffe08a" stroke="#4a3020" stroke-width=".8"/><path d="M' + (lx - 6) + ',' + (ly - 30) + 'L' + lx + ',' + (ly - 37) + 'L' + (lx + 6) + ',' + (ly - 30) + 'Z" fill="#c0281e"/>';
    [[-40, 40], [24, 44]].forEach(function (b) { var bx2 = x + b[0], by2 = y + b[1]; s += '<path d="M' + (bx2 - 10) + ',' + by2 + 'Q' + bx2 + ',' + (by2 + 7) + ' ' + (bx2 + 10) + ',' + by2 + 'Z" fill="#6a4426"/><path d="M' + bx2 + ',' + by2 + 'v-12l7,9z" fill="#f4efe2"/>'; }); // 작은 배
    return s;
  }
  /** 구름(미탐사) -- 탐사한 곳(칸 둘레/길/강/큰 물 이름) 밖을 덮는 작은 그림. 멀어질수록 짙어진다. */
  function worldCloudCanvas(P, segs) {
    var k = 5, W = Math.ceil((WG.x1 - WG.x0) / k), H = Math.ceil((WG.y1 - WG.y0) / k), m1 = new Float32Array(W * H), m2 = new Float32Array(W * H);
    var disc = function (m, cx, cy, r) {
      var i0 = Math.max(0, Math.floor((cx - r - WG.x0) / k)), i1 = Math.min(W - 1, Math.ceil((cx + r - WG.x0) / k)), j0 = Math.max(0, Math.floor((cy - r - WG.y0) / k)), j1 = Math.min(H - 1, Math.ceil((cy + r - WG.y0) / k));
      for (var j = j0; j <= j1; j++) for (var i = i0; i <= i1; i++) { var dx = WG.x0 + i * k - cx, dy = WG.y0 + j * k - cy; if (dx * dx + dy * dy <= r * r) m[j * W + i] = 1; }
    };
    var stamp = function (m, mul) {
      Object.keys(P).forEach(function (id) { disc(m, P[id].x, P[id].y, (/^isle/.test(id) ? 90 : 140) * mul); }); // [2026-11-20] 칸 둘레는 확실히 맑게(110 -> 140)
      segs.forEach(function (s) { var dx = s[2] - s[0], dy = s[3] - s[1], n = Math.max(1, Math.ceil(Math.sqrt(dx * dx + dy * dy) / 20)); for (var i = 0; i <= n; i++) disc(m, s[0] + dx * i / n, s[1] + dy * i / n, 75 * mul); });
      worldCr(WORLD_RIVER, 0.5).concat(worldCr(WORLD_FIRE, 0.5)).forEach(function (q) { if (q[1] > -420) disc(m, q[0], q[1], 65 * mul); });
      WORLD_WATERS.forEach(function (w) { if (w[4] > 14) disc(m, w[1], w[2], w[4] * 3.2 * mul); });
    };
    var blur = function (m, r) { // 상자 흐림 3번(가로/세로)
      var tmp = new Float32Array(W * H), pass, i, j, acc;
      for (pass = 0; pass < 3; pass++) {
        for (j = 0; j < H; j++) { acc = 0; for (i = -r; i <= r; i++) acc += m[j * W + Math.min(W - 1, Math.max(0, i))]; for (i = 0; i < W; i++) { tmp[j * W + i] = acc / (2 * r + 1); acc += m[j * W + Math.min(W - 1, i + r + 1)] - m[j * W + Math.max(0, i - r)]; } }
        for (i = 0; i < W; i++) { acc = 0; for (j = -r; j <= r; j++) acc += tmp[Math.min(H - 1, Math.max(0, j)) * W + i]; for (j = 0; j < H; j++) { m[j * W + i] = acc / (2 * r + 1); acc += tmp[Math.min(H - 1, j + r + 1) * W + i] - tmp[Math.max(0, j - r) * W + i]; } }
      }
    };
    stamp(m1, 1); stamp(m2, 2.6);
    blur(m1, 6); blur(m2, 14);
    var cv = document.createElement('canvas'); cv.width = W; cv.height = H;
    var g = cv.getContext('2d'), im = g.createImageData(W, H), d = im.data, cl = function (v) { return v < 0 ? 0 : v > 1 ? 1 : v; };
    for (var j = 0; j < H; j++) for (var i = 0; i < W; i++) {
      var p = j * W + i, near = 1 - cl(m1[p]), far = 1 - cl(m2[p]);
      if (near <= 0.003) continue;
      var x = WG.x0 + i * k, y = WG.y0 + j * k, n1 = wFbm(x / 170, y / 170, 5), n2 = wFbm(x / 130 + 40, y / 130, 4);
      var haze = 0.45 * near + 0.2 * far, puff = cl((n1 - 0.5) * 3.4) * near, dense = cl((n2 - 0.42) * 3.0) * far;
      var a = 1 - (1 - haze) * (1 - puff) * (1 - dense), sh = cl((n1 - 0.35) * 2);
      d[p * 4] = 222 + 30 * sh; d[p * 4 + 1] = 226 + 27 * sh; d[p * 4 + 2] = 230 + 24 * sh; d[p * 4 + 3] = Math.round(255 * cl(a));
    }
    g.putImageData(im, 0, 0);
    return cv;
  }
  /** 전체 지도 바탕을 한 번 만든다(게임 시작 로딩에서 -- preloadExtra). 땅/강/산/그림은 선 그림 문자열, 구름/축소도는 그림 주소. */
  function worldBgBuild() {
    if (worldBg) return worldBg;
    var P = worldNodePos(), segs = worldSegs(P, false), entry = {};
    var F = worldField(worldLandPts(P)), land = worldContour(F, 0), inner = worldContour(F, 20), v = '';
    v += '<path d="' + land + '" fill="none" stroke="#bcd3cc" stroke-width="34" stroke-linejoin="round"/>'; // 얕은 물
    v += '<path d="' + land + '" fill="#e9dbb4" fill-rule="evenodd" stroke="#5a3e22" stroke-width="2.2" stroke-linejoin="round"/>'; // 땅 + 해안선(먹선)
    v += '<path d="' + inner + '" fill="#f1e8cc" fill-rule="evenodd" opacity=".55"/>';
    var ch = WORLD_REGIONS.filter(function (r) { return r.channel; })[0];
    if (ch && P.archisea) v += '<path d="M' + P.archisea.x + ',' + P.archisea.y + 'V' + (WORLD_PLACE[ch.id][1] + ch.channel.top) + '" stroke="#93b8b2" stroke-width="14" stroke-linecap="round"/>'; // 다도해 뱃길 물길
    var fire = worldRibbon(WORLD_FIRE, 1.5, 9, false, 3), riv = worldRibbon(WORLD_RIVER, 4, 13, true, 7), trib = worldRibbon(WORLD_TRIB, 3, 4, false, 9);
    v += '<path d="' + fire + '" fill="#e8642a" stroke="#7a2410" stroke-width="1.1"/><path d="' + worldBlob(-108, -50, 15, 9, 21) + '" fill="#e8642a" stroke="#7a2410" stroke-width="1.1"/>'; // 불의 강 + 용암 웅덩이
    v += '<path d="M' + worldCr(WORLD_FIRE, 0.25).map(function (q) { return q[0].toFixed(1) + ',' + q[1].toFixed(1); }).join('L') + '" fill="none" stroke="#ffd27a" stroke-width="1.1" stroke-dasharray="3 7"/>';
    for (var fk = 0; fk < 4; fk++) v += '<path d="M' + (-114 + fk * 6) + ',' + (-62 - fk * 3) + 'q-3,-6 0,-11q3,-5 0,-10" fill="none" stroke="#9a8a7a" stroke-width="1" opacity=".55"/>'; // 김
    v += '<path d="' + riv + '" fill="#a9c6c1" stroke="#6f8f8a" stroke-width="1"/><path d="' + trib + '" fill="#a9c6c1" stroke="#6f8f8a" stroke-width=".8"/>'; // 엘리시온 강
    v += '<path d="M' + worldCr(WORLD_RIVER, 0.25).map(function (q) { return q[0].toFixed(1) + ',' + q[1].toFixed(1); }).join('L') + '" fill="none" stroke="#d4e4df" stroke-width=".9" stroke-dasharray="5 9"/>';
    WORLD_LAKES.forEach(function (l, i) { v += '<path d="' + worldBlob(l[0], l[1], l[2], l[3], 5 + i) + '" fill="#a9c6c1" stroke="#6f8f8a" stroke-width="1"/>'; });
    v += worldMountSvg(); // [2026-11-20] '전체 지도 음영이 이상해' -- 그림(설산/탑/신궁/항구/동굴)은 구름 위에 그린다(top) -- 가장자리 그림에 구름 막이 덮이지 않게
    var top = worldSnowMtnSvg(P) + worldTowerSvg(P) + worldShrineSvg(P, entry) + worldHarborSvg(P)
        + worldCaveSvg(P, ['cave1', 'cave2', 'cave3', 'cave4'], false, '⛏️ 광산 동굴', 'cave', entry) + worldCaveSvg(P, ['snowcave', 'snowcave2'], true, '🧊 설산 동굴', 'icecave', entry);
    var ill = {}; // 왕도/요새 도시 그림 -- 길이 그림 밑으로 들어가게 칸 자리에서(worldHtml) 그린다
    if (P.city) ill.city = worldCitySvg(P.city.x, P.city.y);
    if (P.towercity) ill.towercity = worldFortSvg(P.towercity.x, P.towercity.y);
    worldBg = { vec: v, top: top, cloud: null, entry: entry, ill: ill };
    try {
      var cv = worldCloudCanvas(P, segs), done = function (url) { worldBg.cloud = url; if (smithOpen && smithView === 'world') renderSmith(smithNpc); };
      if (cv.toBlob) cv.toBlob(function (b) { if (b) done(URL.createObjectURL(b)); }, 'image/png'); else done(cv.toDataURL('image/png'));
    } catch (e) { }
    return worldBg;
  }
  /** [2026-11-20] "다른 리소스도 게임 시작 때 다 불러서 진행 중에 버벅임이 없게" -- 맵을 다 받은 뒤: 몬스터 그림 전부, 필살기/돌개바람 그림, 전체 지도 바탕. */
  function preloadExtra() {
    return new Promise(function (res) {
      if (opened && el.load.style.display !== 'none') el.load.textContent = '리소스 준비 중... (그림)';
      setTimeout(function () {
        try { // 몬스터 그림 -- 종류 표(mobTypes)는 모든 맵이 같다
          var ty = (map && map.mobTypes) || [];
          for (var k = 0; k < ty.length; k++) { if (!ty[k]) continue; try { mobSprite(k, false, 0); mobSprite(k, false, 1); if (ty[k].boss || ty[k].kind === 'ninja') { mobSprite(k, true, 0); mobSprite(k, true, 1); } } catch (e1) { } }
        } catch (e) { }
        try { ultImg('sword'); ultImg('zan'); if (st && st.adminMaps) whirlLoad(); } catch (e) { }
        try { worldBgBuild(); } catch (e) { }
        res();
      }, 0);
    });
  }
  function worldHtml() {
    var info = {}, W = 96, H = 32, lv = myLv(), adminW = !!(st && (st.adminMaps || st.ringMaps)); // [2026-11-15] 용의 반지를 끼면 지역을 눌러 이동
    var bg = worldBgBuild(); // [2026-11-20] 게임 시작 로딩에서 만들어 둔 바탕(없으면 지금)
    WORLD_NODES.forEach(function (n) { info[n.id] = n; });
    var STY = { walk: 'stroke="#7a5532" stroke-width="2.4"', sea: 'stroke="#1f5f86" stroke-width="2.2" stroke-dasharray="7 5"', fall: 'stroke="#8a5a30" stroke-width="2.4" stroke-dasharray="3 4"' }; // [2026-11-20] 옛 전도 -- 먹색 길, 남색 뱃길
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
      // [2026-11-20] "지역 이름(사막·왕도, 설원·설산) 글씨는 없애도 될 것 같아" -- 구간 이름은 위 버튼에만
      R.links.forEach(function (l) {
        var p = N[l[0]], q = N[l[1]];
        if (!p || !q) return;
        var gp = WORLD_GROUP[l[0]], gq = WORLD_GROUP[l[1]]; // [2026-11-20] 그림으로 그린 곳 -- 같은 그림 안 길은 안 그리고, 밖에서 오는 길은 그림의 입구로
        if (gp && gp === gq) return;
        if (gq && bg.entry[gq]) q = { id: q.id, x: bg.entry[gq][0], y: bg.entry[gq][1] };
        if (gp && bg.entry[gp]) p = { id: p.id, x: bg.entry[gp][0], y: bg.entry[gp][1] };
        lines += '<line x1="' + p.x + '" y1="' + p.y + '" x2="' + q.x + '" y2="' + q.y + '" ' + (WORLD_FLOOR[p.id] && WORLD_FLOOR[q.id] ? 'stroke="#a8742a" stroke-width="2.4" stroke-dasharray="2 5"' : STY[l[2] || 'walk']) + '/>'
            + (l[3] ? '<text x="' + ((p.x + q.x) / 2 + 6) + '" y="' + ((p.y + q.y) / 2 + 4) + '" class="wl">' + l[3] + '</text>' : '');
      });
      if (R.channel && N.archisea) { // 뱃길 + 섬으로 가는 갈래 -- [2026-11-20] 본섬 오른쪽에서 북쪽으로(실제 다도해 뱃길처럼), 물길 띠는 바탕 그림에
        var chx = N.archisea.x, ctop = o[1] + R.channel.top;
        lines += '<line x1="' + chx + '" y1="' + N.archisea.y + '" x2="' + chx + '" y2="' + ctop + '" ' + STY.sea + '/>';
        R.channel.isles.forEach(function (id) { var p = N[id]; if (p) lines += '<line x1="' + chx + '" y1="' + p.y + '" x2="' + p.x + '" y2="' + p.y + '" ' + STY.sea + '/>'; });
      }
      Object.keys(N).forEach(function (k) {
        var n = N[k], flr = WORLD_FLOOR[n.id], dm = (st && st.mapDims && st.mapDims[n.id]) || [100, 100]; // [2026-11-11] "실제 맵 크기를 반영해 크기가 조금씩 다르게" -- 넓이의 제곱근 비율(10칸 미궁 ~ 300칸 다도해)
        var nw = Math.round(14 + 5.2 * Math.sqrt(dm[0])), nh = Math.round(8 + 1.9 * Math.sqrt(dm[1]));
        var isHere = map && (map.id === n.id || (n.id === 'archi' && map.archi && !info[map.id]) || (n.id === 'forestmaze' && /^forest/.test(map.id)) || (n.id === 'towergate' && !!map.tower)); // [2026-11-11] 미궁 방/끝방은 미궁 한 칸
        if (isHere) here = { x: n.x, y: n.y };
        worldNodeXY[n.id] = { x: n.x, y: n.y };
        var grp = WORLD_GROUP[n.id], ill = bg.ill && bg.ill[n.id];
        if (ill) { // [2026-11-20] "도시 같은 건축물/상징성" -- 왕도(성벽·궁전·집들), 요새 도시(해자·둥근 성벽·아성) 그림 + 이름/인원(지금 있으면 붉은 점선 테)
          var ib = n.id === 'city' ? 46 : 52;
          nodes += (adminW ? '<g style="cursor:pointer" onclick="TowerMine.worldWarp(\'' + n.id + '\')">' : '') + ill
              + (isHere ? '<ellipse cx="' + n.x + '" cy="' + (n.y - 2) + '" rx="74" ry="60" fill="none" stroke="#c0281e" stroke-width="2.6" stroke-dasharray="7 4"/>' : '')
              + '<text x="' + n.x + '" y="' + (n.y + ib + 10) + '" text-anchor="middle" class="wn' + (isHere ? ' wn-here' : '') + '">' + (isHere ? '📍 ' : '') + n.name + ' <tspan class="wlv" data-cnt="' + n.id + '">👥' + (mapN[n.id] || 0) + '</tspan></text>'
              + (adminW ? '</g>' : '');
          return;
        }
        if (grp && WORLD_BAND[n.id]) { // [2026-11-20] 그림(신궁 층/동굴 층/탑 입구) 위 띠: "1층 👥n", 지금 있는 곳은 붉은 테
          var bw = WORLD_BAND_W[n.id] || 80, sty = WORLD_BAND_STY[n.id === 'shrineboss' ? 'boss' : grp] || WORLD_BAND_STY.shrine, blv = minLvOf(n.id);
          nodes += (adminW ? '<g style="cursor:pointer" onclick="TowerMine.worldWarp(\'' + n.id + '\')">' : '')
              + '<rect x="' + (n.x - bw / 2) + '" y="' + (n.y - 9) + '" width="' + bw + '" height="18" rx="3" fill="' + sty[0] + '" stroke="' + (isHere ? '#c0281e' : '#6a3a1a') + '" stroke-width="' + (isHere ? 2.6 : 0.8) + '"/>'
              + '<text x="' + n.x + '" y="' + (n.y + 3.5) + '" text-anchor="middle" class="wb" style="fill:' + sty[1] + '">' + (isHere ? '📍' : '') + WORLD_BAND[n.id] + ' <tspan data-cnt="' + n.id + '">👥' + (mapN[n.id] || 0) + '</tspan></text>'
              + (blv > 1 ? '<text x="' + (n.x - (WORLD_LV_DX[n.id] || bw / 2 + 4)) + '" y="' + (n.y + 3) + '" text-anchor="end" class="wlv"' + (blv > lv ? ' style="fill:#c0281e"' : '') + '>Lv' + blv + '</text>' : '')
              + (adminW ? '</g>' : '');
          if (bossOf[n.id] && bossOf[n.id].alive) labels += '<text x="' + (n.x + bw / 2 + 4) + '" y="' + (n.y + 3) + '" class="wl" style="fill:#c0281e;font-weight:800">👑 보스 출현 중</text>';
          return;
        }
        if (wanderAt && (wanderAt.h === n.id || wanderAt.a === n.id)) { // [2026-11-21] "다도해 떠상/악마 들판 떠상 현재 위치" -- 칸 오른쪽에 깃발 글씨
          var wtx = wanderAt.h === n.id ? '⚒️ 은둔한 대장장이' : '🛡️ 떠돌이 갑옷장이';
          labels += '<text x="' + (n.x + nw / 2 + 5) + '" y="' + (n.y + 4) + '" class="wl" style="fill:' + (wanderAt.h === n.id ? '#1a5a8a' : '#a8401a') + ';font-weight:800">' + wtx + ' 여기</text>';
        }
        if (bossOf[n.id]) {
          var bb = bossOf[n.id], by = n.y - nh / 2 - 5, bx = n.x - nw / 2, bh = ''; // [2026-11-11] "보스 출현 중이 너무 아래" -- 권장 Lv 밑(아래 칸과 붙어 보임) -> 칸 바로 위(지금 있는 곳이면 📍를 앞에 붙이고 "📍 여기"는 뺀다)
          if (bb.alive) labels += '<text x="' + bx + '" y="' + by + '" class="wl" style="fill:#c0281e;font-weight:800">' + bh + '👑 보스 출현 중</text>'; // [2026-11-13] 젠타임은 없애고(소환) "👑 보스" 버튼 목록에 내 소환 가능 시각
        }
        nodes += (adminW ? '<g style="cursor:pointer" onclick="TowerMine.worldWarp(\'' + n.id + '\')">' : '')
            // [2026-11-11] "사각형 안에 이름 말고 배경 없는 글씨, 권장 레벨 대신 Lv30 피라미드, 인원은 언제든" -- 칸(맵 크기) 안엔 인원만, 이름은 칸 아래 떠 있는 글씨
            + (flr ? '<rect x="' + (n.x - nw / 2 + 4) + '" y="' + (n.y - nh / 2 - 4) + '" width="' + nw + '" height="' + nh + '" rx="3" fill="' + n.fill + '" opacity=".55" stroke="#140d0a" stroke-width="1.5"/>' : '')
            + '<rect x="' + (n.x - nw / 2) + '" y="' + (n.y - nh / 2) + '" width="' + nw + '" height="' + nh + '" rx="3" fill="' + n.fill + '"'
            + ' stroke="' + (isHere ? '#c0281e' : '#3a2614') + '" stroke-width="' + (isHere ? 3.5 : 1.3) + '"/>'
            + '<text x="' + n.x + '" y="' + (n.y + 3.5) + '" text-anchor="middle" class="wc" fill="' + n.ink + '" data-cnt="' + n.id + '">👥' + (mapN[n.id] || 0) + '</text>'
            + '<text x="' + n.x + '" y="' + (n.y + nh / 2 + 11) + '" text-anchor="middle" class="wn' + (isHere ? ' wn-here' : '') + '">'
            + (isHere ? '📍 ' : '') + (!n.town && minLvOf(n.id) > 1 ? '<tspan class="wlv"' + (minLvOf(n.id) > lv ? ' style="fill:#c0281e"' : '') + '>Lv' + minLvOf(n.id) + '</tspan> ' : '') + n.name + '</text>'
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
        + '<div class="world-wrap"><svg id="mineWorldSvg" class="mine-world world-pan' + (worldVB.w <= WORLD_CNT_W ? ' world-close' : '') + '" viewBox="' + worldVB.x + ' ' + worldVB.y + ' ' + worldVB.w + ' ' + worldVB.h + '" preserveAspectRatio="xMidYMid meet" role="img" aria-label="광산 전체 지도" xmlns:xlink="http://www.w3.org/1999/xlink">'
        + '<defs><pattern id="wSeaPat" width="40" height="24" patternUnits="userSpaceOnUse"><rect width="40" height="24" fill="#a9c4bd"/><path d="M2,14 q5,-5 10,0 t10,0" fill="none" stroke="#8eb0aa" stroke-width="1.1"/><path d="M22,4 q5,-5 10,0" fill="none" stroke="#8eb0aa" stroke-width="1"/></pattern></defs>'
        + '<rect x="' + WG.x0 + '" y="' + WG.y0 + '" width="' + (WG.x1 - WG.x0) + '" height="' + (WG.y1 - WG.y0) + '" fill="url(#wSeaPat)"/>' + bg.vec // [2026-11-20] 옛 전도 -- 바다 무늬, 땅/강/산/신궁/항구
        + (bg.cloud ? '<image href="' + bg.cloud + '" xlink:href="' + bg.cloud + '" x="' + WG.x0 + '" y="' + WG.y0 + '" width="' + (WG.x1 - WG.x0) + '" height="' + (WG.y1 - WG.y0) + '" preserveAspectRatio="none"/>' : '') // 미탐사 구름
        + (bg.top || '') // [2026-11-20] 그림은 구름 위에
        + WORLD_WATERS.map(function (w) { return '<g transform="translate(' + w[1] + ',' + w[2] + ') rotate(' + w[3] + ')"><text text-anchor="middle" class="wt" style="font-size:' + w[4] + 'px' + (w[4] < 12 ? ';letter-spacing:1px' : '') + (w[5] ? ';fill:' + w[5] + ';stroke:#f1e0c0' : '') + '">' + w[0] + '</text></g>'; }).join('') // 강·바다 이름
        + lines + nodes + labels + '</svg></div>'
        + '<div class="mine-smith-say">끌어서 움직이고, 휠·두 손가락·＋－로 확대해요.</div>' + wanderNote();
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
  var worldHere = null, worldNodeXY = {}; // [2026-11-21] 칸 자리(떠돌이 NPC 버튼으로 그 칸에)
  /** [2026-11-21] 지도 아래 -- 떠돌이 NPC 둘이 지금 어느 맵에 있는지(누르면 그 칸으로), 사라져 있으면 돌아오는 시각. */
  function wanderNote() {
    if (!wanderAt) return '';
    var nm = function (id) { var i = WORLD_NODES.filter(function (n) { return n.id === id; })[0]; return (i && i.name) || id; };
    var hh = function (t) { var d = new Date(t); return (d.getHours() < 10 ? '0' : '') + d.getHours() + ':' + (d.getMinutes() < 10 ? '0' : '') + d.getMinutes(); };
    var one = function (icon, who, at, back) {
      if (at) return '<button type="button" class="mine-mini-btn" onclick="TowerMine.worldGo(\'' + at + '\')">' + icon + ' ' + who + ': ' + escAttr(nm(at)) + '</button> ';
      if (back) return '<span style="margin-right:8px">' + icon + ' ' + who + ': 자리를 비움 (' + hh(back) + '쯤 돌아와요)</span>';
      return '';
    };
    var s = one('⚒️', '은둔한 대장장이', wanderAt.h, wanderAt.hb) + one('🛡️', '떠돌이 갑옷장이', wanderAt.a, wanderAt.ab);
    return s ? '<div class="mine-smith-say">' + s + '</div>' : '';
  }
  // [2026-10-30] 접속 인원 -- 위치 알림으로 받는다(이름 없이 숫자만). 맵별 인원은 전체 지도를 가장 크게 확대했을 때만 보인다(.world-close)
  var wanderAt = null, wanderKey = ''; // [2026-11-21] 은둔한 대장장이(h)/떠돌이 갑옷장이(a)가 있는 맵, 사라져 있으면 돌아오는 시각(hb/ab)
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
    var nw = Math.max(WORLD_MIN_W, Math.min(2400, worldVB.w / f)), k = nw / worldVB.w; // [2026-11-20] 구름 덮인 바깥까지 보이게 1400 -> 2400
    worldVB = { x: cx - (cx - worldVB.x) * k, y: cy - (cy - worldVB.y) * k, w: nw, h: worldVB.h * k };
    worldApply();
  }
  function worldZoom(f) { worldZoomAt(f, worldVB.x + worldVB.w / 2, worldVB.y + worldVB.h / 2); }
  function worldGo(id) {
    var c = null;
    if (id === 'me') c = worldHere;
    else if (worldNodeXY[id]) c = worldNodeXY[id]; // [2026-11-21] 떠돌이 NPC가 있는 칸
    else if (WORLD_PLACE[id]) { var R = WORLD_REGIONS.filter(function (r) { return r.id === id; })[0]; c = { x: WORLD_PLACE[id][0] + ((R && R.cx) || 160), y: WORLD_PLACE[id][1] + (R ? R.vh / 2 : 200) }; }
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
    var wl = st && (st.adminMaps || st.ringMaps);
    if (!wl || busy || wMoved) return; // [2026-10-28] 끌다가 놓은 건 누른 게 아니다
    var e = wl.filter(function (m) { return m.id === id; })[0];
    if (!e) { msg(st.adminMaps ? '이동할 수 없는 곳이에요.' : id === 'abyssgate' ? '💍 마계 입구는 마계의 기운이 너무 거세 용의 반지의 힘이 닿지 않아요. 최후의 방어선을 지나 걸어서 가 주세요.' : '💍 용의 반지로는 갈 수 없는 곳이에요.', 3500); return; } // [2026-11-20] 마계 입구는 따로 안내
    if (!st.adminMaps) { // [2026-11-15] 용의 반지 -- 한 시간에 한 번이라 한 번 더 묻는다
      var rleft = (st.ringReadyAt || 0) - nowSrv();
      if (rleft > 0) { msg('💍 용의 반지의 힘이 아직 돌아오지 않았어요. (' + callLeftTxt(rleft) + ' 뒤)'); return; }
      if (map && map.id === id) { msg('이미 그곳에 있어요.'); return; }
      // [2026-11-20] "용의 반지 사용자가 안 된다" -- confirm()은 카카오톡 등 앱 안 브라우저에서 창 없이 '취소'가 되는 일이 있어 게임 확인 창(ask)으로
      ask('💍 용의 반지', '「' + e.name + '」(으)로 갈까요? (한 시간에 한 번)', '이동', function () { setAuto(false); travel('/api/tower-mine-admin-warp?mapId=' + encodeURIComponent(id) + '&x=' + (e.sx != null ? e.sx : 0) + '&y=' + (e.sy != null ? e.sy : 0), '이동하지 못했어요.'); });
      return;
    }
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
        + '<div class="eq-tabs">' + tab('gear', '⚔️ 장비') + tab('look', '👗 치장') + tab('skill', '🌀 스킬') + tab('fish', '🎣 낚시') + tab('etc', '📦 기타') + '</div></div>';
    if (equipTab === 'fish') return h + fishInvHtml(); // [2026-11-19] 낚시(성급별)
    if (equipTab === 'etc') return h + invEtcHtml();
    if (equipTab === 'skill') return h + skillHtml(); // [2026-11-04] 검기
    if (equipTab === 'look') {
      var lk = st.look || {};
      h += '<div class="mine-look-top"><div class="mine-look-prevs">' + ['down', 'right', 'up'].map(function (d) { return '<img class="mine-look-prev" src="' + lookImg(lk, d) + '" alt="">'; }).join('') + '</div>'
          + '<div class="mine-smith-say">가지고 있는 치장만 보여요. 새 치장은 명품상점에서 살 수 있어요.</div></div>';
      ['hat', 'hair', 'top', 'bottom', 'shoes', 'wing'].forEach(function (slot) {
        var cur = lk[slot + 'Code'] || '', list = (st.cosShop || []).filter(function (c) { return c.slot === slot && c.owned; });
        h += '<div class="cos-slot"><div class="cos-slot-h">' + LOOK_SLOT_NAME[slot] + ' <small>' + list.length + '개</small></div><div class="cos-car" data-slot="l-' + slot + '">'
            + '<div class="cos-card' + (cur ? '' : ' worn') + '"><img src="' + lookImgLazy(lookWith(slot, ''), 'down') + '" alt=""><div class="cos-nm">기본</div>'
            + (cur ? '<button type="button" onclick="TowerMine.lookAct(\'wear\', \'\', \'' + slot + '\')">입기</button>' : '<span class="mine-worn">착용 중</span>') + '</div>';
        list.forEach(function (c) {
          h += '<div class="cos-card' + (c.code === cur ? ' worn' : '') + (c.note ? ' gm' : '') + '"><img src="' + lookImgLazy(lookWith(c.slot, c.look), 'down') + '" alt="' + c.name + '"><div class="cos-nm">' + c.name + '</div>'
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
  /** [2026-11-20] "장비창에 낚싯대/찌(있으면) 등 낚시용품도" -- 누르지 않는 칸: 낚싯대 · 찌 · 낚시 내구도. */
  function fishSlotHtml() {
    var g = st && st.fishGear;
    if (!g) return '';
    var fc = ['', '#c8c8c8', '#6ad86a', '#6ab8ff', '#c08aff', '#ffb347'];
    return '<div class="doll-slot on" style="cursor:default"><b>🎣 낚시</b><span><span style="color:' + (fc[g.rodG] || '#fff') + '">' + escAttr(g.rod) + '</span> · <span style="color:' + (fc[g.bobG] || '#fff') + '">' + escAttr(g.bob) + '</span>'
        + (st.fishDur != null ? '<br><small>내구도 ' + st.fishDur + ' / ' + (st.fishDurMax || 100) + '</small>' : '') + '</span></div>';
  }
  function dollHtml() {
    var slot = function (sl) {
      var rows = (st.equip || []).filter(function (e) { return e.slot === sl.id; }), w = rows.filter(function (e) { return e.worn; })[0];
      var en = sl.id === 'weapon' && st.wenh && st.wenh.lv ? ' <b style="color:#ffd27a">+' + st.wenh.lv + '</b>' : sl.id === 'armor' && st.aenh && st.aenh.lv ? ' <b style="color:#ffd27a">+' + st.aenh.lv + '</b>' : ''; // [2026-11-06] 무기 칸 강화, [2026-11-18] 갑옷 칸 강화
      return '<button type="button" class="doll-slot' + (w ? ' on' : '') + '" onclick="TowerMine.eqPop(\'' + sl.id + '\')"><b>' + sl.name + en + '</b><span>' + (w ? gradeName(w.name, w.grade) : '없음') + '</span></button>';
    };
    var by = {}; EQ_SLOTS.forEach(function (sl) { by[sl.id] = sl; });
    var h = '<div class="doll"><div class="doll-col">' + ['weapon', 'armor', 'ring', 'gloves'].map(function (k) { return slot(by[k]); }).join('') + '</div>' // [2026-11-06] 갑옷 칸 -- 좌우 4칸씩
        + '<div class="doll-char"><img src="' + lookImg(st.look || {}, 'down') + '" alt="내 캐릭터"><small>Lv.' + myLv() + '</small></div>'
        + '<div class="doll-col">' + ['necklace', 'boots', 'tool'].map(function (k) { return slot(by[k]); }).join('') + fishSlotHtml() + '</div></div>'
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
      if (sl.id === 'armor') h += aenhHtml(); // [2026-11-18] 갑옷 칸 강화
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
  function aenhHtml(atNpc) { // [2026-11-18] 갑옷 칸 강화 -- atNpc = 방어구 강화사 창(버튼), 아니면 인벤토리 갑옷 칸 팝업(보기만)
    var w = st.aenh || { lv: 0 }, h = '<div class="mine-smith-say" style="margin-top:8px">🛡️ <b>갑옷 칸 강화 +' + (w.lv || 0) + '</b> · 어떤 갑옷을 입어도 적용<br>'
        + '<small>1강마다 받는 피해 감소 +10% · 갑옷 최대 체력 +8% · 5강마다 초당 회복 더 (전설 이상)<br>재료: 미스릴괴 + 악마의 광석 조각 · 실패하면 재료만 사라져요</small></div>';
    if (!w.name) return h + '<div class="mine-eq-empty">갑옷을 입어야 강화할 수 있어요.</div>';
    h += '<div class="mine-smith-row"><span class="nm"><span>' + gradeName(w.name, w.grade) + ' <b style="color:#ffd27a">+' + w.lv + '</b></span><small>' + effLines(w.effect) + '</small>';
    if (w.pct != null && !atNpc) h += '<small style="color:#9fe0ff">다음 +' + (w.lv + 1) + ' · 성공 ' + w.pct + '%<br>왕도 방어구 강화사(무기 강화사 옆)에게서</small></span></div>';
    else if (w.pct != null) h += '<small style="color:#9fe0ff;margin-top:6px">▶ 다음 +' + (w.lv + 1) + ' · 성공 ' + w.pct + '%<br>' + effLines(w.nextEffect) + '</small>' + costHtml(w.costs) + '</span>'
        + '<button type="button" ' + (w.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.enhanceArmor()">강화</button></div>';
    else h += '<small>최고 강화예요</small></span><button type="button" data-lack="1" disabled>완료</button></div>';
    return h;
  }
  function enhanceArmor() { gearAct('/api/tower-mine-enhance?code=armor'); }
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
    if (mercSel == null || !list[mercSel] || (own.length && !list[mercSel].owned)) mercSel = on ? on.k : own.length ? own[0].k : 0; // [2026-11-18] 두 번째 용병 -- 목록 번호 = 기록 번호 k
    var tab = function (id, label) { return '<button type="button" class="' + (mercTabV === id ? 'on' : '') + '" onclick="TowerMine.mercTab(\'' + id + '\')">' + label + '</button>'; };
    var h = '<div class="mine-smith-head inv-head">🛡️ 용병 관리 <span><button type="button" class="mine-close" onclick="TowerMine.closeSmith()">✖ 닫기</button></span>'
        + '<div class="eq-tabs">' + tab('gear', '⚔️ 장비') + tab('skill', '🌀 스킬') + '</div></div>';
    if (own.length <= 1) { // [2026-11-06] "1개만 노출(지금은 1개만)" -- 계약한 용병 한 명만(없으면 안내)
      if (!own.length) return h + '<div class="mine-smith-say">아직 계약한 용병이 없어요.<br><small>왕도 7시 방향 용병소에서 계약할 수 있어요 (Lv.5~30, 불·물·번개 중 무작위 특화)</small></div>';
    } else h += '<div class="eq-tabs merc-sel">' + list.filter(function (x) { return x.owned; }).map(function (x) {
      return '<button type="button" class="' + (x.k === mercSel ? 'on' : '') + '" onclick="TowerMine.mercSel(' + x.k + ')">' + (x.slot === 2 ? '② ' : '① ') + MERC_ICON[x.t] + ' ' + (x.owned && x.nm ? x.nm : x.name) + (x.owned ? ' ' + x.lv : '') + (x.on ? ' ✔' : x.unpaid && x.owned ? ' ⚠️' : '') + '</button>';
    }).join('') + '</div>';
    var m = list[mercSel] || {};
    if (!m.owned) return h + '<div class="mine-smith-say">아직 계약하지 않은 ' + (m.name || '용병') + '예요.<br><small>왕도 7시 방향 용병소에서 계약할 수 있어요 (Lv.5~30, 불·물·번개 중 무작위 특화)</small></div>';
    if (mercTabV === 'skill') { // [2026-11-06] "스킬 설명, 아이콘, 데미지 비율 + 끔/1/2/3 골라 안 쓰게" -- 평타(20레벨마다 1~5) / 2차 스킬(40/60/80에 1~3, 20초마다)
      var ic1 = MERC_SK_ICON[m.t], ic2 = m.t === 0 ? '⚔️' : m.t === 1 ? '🌧️' : m.el === 6 ? '☄️' : m.el === 2 ? '⚡' : '🌪️';
      var nm1 = m.t === 0 ? m.elName + ' 검기' : m.t === 1 ? (m.el === 6 ? '불화살' : m.el === 2 ? '번개 화살' : '물화살') : (m.el === 6 ? '파이어볼' : m.el === 2 ? '라이트닝볼' : '아이스볼');
      var nm2 = m.t === 0 ? '이기어검' : m.t === 1 ? '화살비' : m.el === 6 ? '메테오' : m.el === 2 ? '체인 라이트닝' : '토네이도';
      var selRow = function (which, cur, max, top, lvOf) { // 끔 / 1 / 2 ... (열리지 않은 단계는 자물쇠)
        var r = '<div class="merc-sk-sel">', o = cur === 0;
        r += '<button type="button" class="' + (o ? 'on' : '') + '" onclick="TowerMine.mercSkill(' + m.k + ', ' + which + ', 0)">끔</button>';
        for (var n = 1; n <= top; n++) r += n <= max ? '<button type="button" class="' + (cur === n ? 'on' : '') + '" onclick="TowerMine.mercSkill(' + m.k + ', ' + which + ', ' + n + ')">' + n + '</button>'
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
      if (st.adminMaps && m.lv < 99) h += '<div class="mine-smith-row"><span class="nm">🛠️ 관리자<small>용병 레벨을 99로</small></span><button type="button" onclick="TowerMine.mercLv99(' + m.k + ')">Lv.99</button></div>';
      return h;
    }
    h += mercViewBtns();
    var pct = m.need ? Math.min(100, Math.round(m.exp * 100 / m.need)) : 100, mgb = m.mgb || [0, 0, 0];
    h += '<div class="doll"><div class="doll-col">' + ['W', 'H', 'A'].map(function (k) { return mgSlotBtn(m, k); }).join('') + '</div>' // [2026-11-16] 플레이어 장비 칸과 같은 방식
        + '<div class="doll-char' + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(m.t, m.ni, m.nm) + '<small>' + (m.nm || '') + ' Lv.' + m.lv + '</small></div>'
        + '<div class="doll-col">' + ['N', 'R', 'B'].map(function (k) { return mgSlotBtn(m, k); }).join('') + '</div></div>'
        + '<div class="mine-smith-say">칸을 누르면 그 부위에 장착할 수 있는 장비가 나와요.<br><small>🎒 동료 장비 효과: 공격력 +' + mgb[0] + '% · 치명타 ' + mgb[1] + '% · 상성 피해 +' + mgb[2] + '%</small></div>'
        + '<div class="mine-smith-say">' + (EL_ICON[m.el] || '') + ' ' + m.elName + ' 특화 · 🌀 스킬 ' + m.st + '단계 · ' + (m.weapon || '기본 무기') + '<br>공격력 ' + fmtN(m.atk) + ' · 체력 ' + fmtN(m.max) + ' · 사거리 ' + m.reach + '칸'
        + '<span class="mine-bar" style="margin-top:6px"><i style="display:block;height:100%;width:' + pct + '%;background:#9fe0ff"></i></span>'
        + '<small>경험치 ' + (m.need ? fmtN(m.exp) + ' / ' + fmtN(m.need) : '최고 레벨') + ' · 내가 얻는 경험치의 1%</small></div>'
        + '<div class="mine-smith-say" style="margin-bottom:4px">공격 방식</div><div class="merc-view merc-mode"><button type="button" class="' + (m.mode ? '' : 'on') + '" onclick="TowerMine.mercMode(0, ' + m.k + ')">🤝 협공 모드<small>나와 싸우는 몬스터만</small></button>'
        + '<button type="button" class="' + (m.mode ? 'on' : '') + '" onclick="TowerMine.mercMode(1, ' + m.k + ')">⚔️ 선공 모드<small>사거리 안 몬스터를 먼저</small></button></div>' // [2026-11-06] 선공/협공
        + '<div class="mine-smith-row"><span class="nm">' + (m.on ? '✔ 함께 다니는 중' : '용병소에서 쉬는 중') + '</span><button type="button" onclick="TowerMine.mercUse(' + m.k + ')">' + (m.want || m.on ? '쉬게 하기' : '데리고 다니기') + '</button></div>'
        + mercPayHtml(m);
    return h + (mgPop ? mgPopHtml(m) : '');
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
  function mercMode(md, k) { // [2026-11-06] 용병 선공 / 협공, [2026-11-18] 용병마다(k = 3이면 두 번째 용병)
    if (busy) return;
    busy = true;
    api('/api/tower-mine-merc?action=mode&tier=' + md + '&code=' + (k | 0)).then(function (d) { busy = false; if (d) mercAfter(d); }).catch(function () { busy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
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
  function mercSelGo(c) { mercSel = c; mgPop = null; if (smithOpen) renderSmith(smithNpc); }
  function mercTabGo(t) { mercTabV = t; if (smithOpen) renderSmith(smithNpc); }
  function mercAfter(d) {
    if (d.list) st.mercs = d.list;
    if (d.mercPay) setTimeout(function () { mercPayNote(d.mercPay); }, 3300);
    st.merc = d.merc || null;
    st.merc2 = d.merc2 || null; // [2026-11-18] 두 번째 용병
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
    ['👑 고유 증표 (보스·피라미드)', function (g) { return g === 31 || g === 32 || g === 33 || g === 39 || g === 64 || g === 65 || g === 71 || g === 77 || g === 78 || g === 79 || g === 81 || g === 84; }], // [2026-11-17] + 빛의 심장, [2026-11-18] + 어둠의 심장, 심판의 증표, 물의 심장
    ['🧩 조각', function (g) { return (g >= 60 && g <= 63) || (g >= 66 && g <= 69); }], // [2026-10-29] 무기 조각, [2026-11-06] 갑옷 조각
    ['😈 악마의 땅 · 마계', function (g) { return g >= 73 && g <= 76; }], // [2026-11-17] 인장 둘, 악마의 광석 조각, 종말의 파편
    ['🪵 목재', function (g) { return g === 83; }] // [2026-11-19] 나무(배 수리)
  ];
  // [2026-11-19] "인벤토리 카테고리에 낚시(성급별)" -- 성급(★8 -> ★1)마다 도감 물고기, 낚은 건 수, 안 낚은 건 ???. 서버 어망(action=fishinv)은 30초 동안 다시 쓰고, 낚으면 새로.
  var fishInvD = null, fishInvAt = 0, fishInvBusy = false;
  function fishInvHtml() {
    var t = Date.now();
    if ((!fishInvD || t - fishInvAt > 30000) && !fishInvBusy) {
      fishInvBusy = true;
      api('/api/tower-mine-archi?action=fishinv').then(function (d) {
        fishInvBusy = false;
        if (d && d.ok) { fishInvD = d; fishInvAt = Date.now(); if (smithOpen && smithView === 'equip' && equipTab === 'fish') renderSmith(smithNpc); }
      }).catch(function () { fishInvBusy = false; });
    }
    if (!fishInvD) return '<div class="mine-smith-say">🎣 어망을 여는 중...</div>';
    var d = fishInvD, h = '<div class="mine-smith-say">🎣 ' + d.rod + ' · ' + d.bobber + '<br><small>낚은 물고기 ' + d.kinds + '/' + d.allKinds + '종 · 모두 ' + d.total + '마리 -- 물가에서 물을 바라보거나 배를 타고 🎣 버튼으로 낚아요. 지역마다 그곳에서만 낚이는 보석물고기가 있어요. 물고기를 누르면 설명.</small></div>';
    (d.fishInv || []).forEach(function (g) {
      var got = g.fish.filter(function (f) { return f.qty > 0; }).length;
      h += '<div class="minv-cat"><div class="cos-slot-h">' + g.star + ' <small>' + got + '/' + g.fish.length + '종</small></div><div class="minv-grid">'
          + g.fish.map(function (f) {
            return f.qty > 0 ? '<div class="minv-item minv-fish" style="cursor:pointer" onclick="TowerMine.fishInfo(' + f.id + ')"><span style="font-size:22px;line-height:28px">' + f.emoji + '</span><span class="fnm">' + f.name + '</span><b>' + f.qty + '</b></div>'
              : '<div class="minv-item minv-fish" style="opacity:.4"><span style="font-size:22px;line-height:28px">❔</span><span class="fnm">' + (f.region ? f.region : '???') + '</span><b></b></div>'; // [2026-11-19] 특산 보석물고기는 지역 이름(어디서 낚는지)
          }).join('') + '</div></div>';
    });
    return h;
  }
  function fishInfo(id) {
    ((fishInvD && fishInvD.fishInv) || []).forEach(function (g) { g.fish.forEach(function (f) { if (f.id === id) msg(f.emoji + ' ' + g.star + ' ' + f.name + ' (' + f.qty + '마리)' + (f.desc ? ' -- ' + f.desc : ''), 4200); }); });
  }
  // [2026-11-19] "배 수리할 때 재료를 선택해서 납부 -- 바다 보석이나 나무(하피의 둥지 나무를 곡괭이로), 나무 1개당 5, 보석 20" -- 재료마다 1개 / 가득(필요한 만큼, 가진 만큼까지)
  function boatFixHtml(a) {
    var hp = a.boatHp || 0, mx = a.boatHpMax || 1, pc = Math.floor(hp * 100 / mx);
    var h = '<div class="mine-smith-say">🔨 배 수리 -- ' + (hp > 0 ? '깎인 만큼 고쳐요' : '부서진 배 -- 고쳐야 바다로 나갈 수 있어요') + '<br><small>배 체력 ' + fmtN(hp) + ' / ' + fmtN(mx) + ' (' + pc + '%) · 재료를 골라 내세요: 🪵 나무 1개당 5%, 바다 보석 1개당 20%</small><span class="mine-bar hp" style="margin-top:4px"><i style="width:' + pc + '%"></i></span></div>';
    (a.boatMats || []).forEach(function (m) {
      var full = Math.min(m.need, m.have), dis = m.have > 0 ? '' : 'disabled ';
      h += '<div class="mine-smith-row"><span class="nm"><img src="' + oreIcon(m.grade) + '" alt="" style="width:16px;vertical-align:middle"> ' + m.name + ' <small>보유 ' + fmtN(m.have) + '개 · 1개당 ' + m.pct + '%' + (m.have > 0 || m.code !== 'WOOD' ? '' : ' -- 하피의 둥지 나무를 곡괭이로 쓰러뜨리면 얻어요') + '</small></span>'
        + '<button type="button" ' + dis + 'onclick="TowerMine.boatFix(\'' + m.code + '\', 1)">1개</button>'
        + (full > 1 ? '<button type="button" onclick="TowerMine.boatFix(\'' + m.code + '\', ' + full + ')">' + full + '개' + (full >= m.need ? ' (가득)' : '') + '</button>' : '') + '</div>';
    });
    return h;
  }
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
    HUNT_NECKLACE: '착용시 몬스터를 쓰러뜨릴 때 보석이 나올 확률 증가(1~5단계 1.3/1.6/2.2/3/4배)', // [2026-11-13]
    SAND_NECKLACE: '착용시 캘 때 보석이 나올 확률 증가(1~5단계 1.3/1.6/2.2/3/4배)',
    SWIFT_BOOTS: '이동 속도 증가(1~6단계 +11/25/40/60/89/126%)',
    SWORD: '기본 무기', 
    PARASOL: '공격력 +10, 다도해 햇빛 피해 2단계 감소', 
    BASIC_PICK: '한 칸씩 캐는 기본 곡괭이예요.', 
    COAT: '설산의 한기를 단계만큼 막아 줘요(1~3). 몸이 둔해져 몬스터에게 받는 피해가 2배. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요. 털옷 장인 의뢰로 다음 단계로.',
    COAT1: '설산의 한기를 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    COAT2: '설산의 한기를 크게 막아 주지만 몸이 둔해져 몬스터에게 받는 피해가 2배예요. 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요.',
    GLOVES: '한기 방어 +단계(1~3). 사막·남쪽 섬·다도해에선 더워서 초당 1%씩 체력이 깎여요. 털옷 장인 의뢰로 다음 단계로.',
    DRAGON_RING: '끼고 있는 동안 물 위를 땅처럼 걸을 수 있어요.(물 위에서는 못 빼요).',
    REGEN_GLOVES: '몬스터에게 받은 피해의 50%를 5초에 걸쳐 회복 (2단계: 65% + 처치 시 최대 체력 3%)', // [2026-11-04] 80%/4초 -> 50%/5초, 2단계
    TWIN_GLOVES: '한 번 공격할 때 기본 공격력의 55%로 더블어택(합쳐서 110%). 타격마다 치명타 확률 +5%. 공격할 때 12% 확률로 2초 기절. 초당 최대 체력의 1% 소모', // [2026-11-20] 기절
    BERSERK_GLOVES: '공격력이 50% 올라가는 대신 체력이 차오르는 속도가 20% 느려져요. 불굴: 쓰러질 일격을 체력 1로 버티고 5초 무적(1분에 한 번).', // [2026-11-20]
    ULT_DMG_GLOVES: '필살기(만검진·일섬참) 피해 +30% (2단계 +45%)', ULT_CD_GLOVES: '필살기 쿨타임 -25% (2단계 -35%)' };
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
      if (a.boat) return h + have + '<div class="mine-smith-say">⛵ 이미 1인용 배가 있어요. 뱃사공에게 배 타기를 부탁하세요.</div>'
          + (a.boatFix > 0 ? boatFixHtml(a) : ''); // [2026-11-18] 배 수리는 배 목수가
      return h + have + '<div class="mine-smith-row"><span class="nm">⛵ 1인용 배<small>뱃길로 나가 일곱 섬에 내릴 수 있어요</small>' + costHtml(a.boatCosts) + '</span>'
          + '<button type="button" ' + (a.boatCan ? '' : 'data-lack="1" ') + 'onclick="TowerMine.archiAct(\'boat\')">만들기</button></div>';
    }
    return h + have + '<div class="mine-smith-menu"><button type="button" ' + (a.boat ? '' : 'disabled ') + 'onclick="TowerMine.archiAct(\'board\')">⛵ 배 타기<small>' + (a.boat ? '다도해 뱃길로' : '1인용 배가 있어야 해요') + '</small></button></div>'
        + '<div class="mine-smith-say">☀️ 다도해는 낮(30분)과 밤(30분)이 번갈아 와요. 낮에는 섬마다 햇볕에 초당 3~8%씩 체력이 깎이니(환생할수록 덜), 야자수 그늘(🌴)에서 쉬어 가세요.</div>';
  }
  // [2026-11-18] 배 띄우기 -- 남쪽 바닷가: 다도해 뱃길 / 바다 위(지금은 관리자만), 해상도시: 바다 위. 배 체력도 함께 보여 준다.
  function sailHtml(n) {
    var a = st.archi || {}, adm = !!(st && st.adminMaps), hp = a.boatHp || 0, mx = a.boatHpMax || 1;
    var h = npcHead('⛵ ' + n.name, false) + talkBlock(n, n.line);
    h += '<div class="mine-smith-say">' + (a.boat ? '⛵ 1인용 배 · 체력 ' + fmtN(hp) + ' / ' + fmtN(mx) + '<span class="mine-bar hp" style="margin-top:4px"><i style="width:' + Math.round(hp * 100 / mx) + '%;background:#4fc3f7"></i></span>'
        : '⛵ 1인용 배가 없어요 -- 다도해 본섬 배 목수가 만들어 줘요(본섬은 남쪽 섬에서 큰 배로).') + '</div>';
    var dests = map.id === 'beach' ? [['archisea', '🏝️ 다도해 뱃길', '동쪽 바다 -- 일곱 섬과 본섬', true], ['opensea', '🌊 바다 위', '남쪽 먼 바다 -- 끝에 바다마을 윤슬항', true]] // [2026-11-19] 모두에게(예전: 관리자만)
        : [['opensea', '🌊 바다 위', '북쪽 끝은 남쪽 바닷가', true]];
    if (a.boat && a.boatFix > 0) h += boatFixHtml(a); // [2026-11-19] 재료 골라 수리
    if (adm) h += '<div class="mine-smith-row"><span class="nm">🐙 크라켄 다시 살리기<small>관리자 -- 쓰러진 뒤 1시간을 기다리지 않고(곧 바다 위 배 둘레에서 솟아올라요)</small></span><button type="button" onclick="TowerMine.krkRespawn()">살리기</button></div>'; // [2026-11-19]
    return h + '<div class="mine-smith-menu">' + dests.map(function (d) {
      var can = a.boat && hp > 0 && d[3];
      return '<button type="button" ' + (can ? '' : 'disabled ') + 'onclick="TowerMine.sail(\'' + d[0] + '\')">' + d[1] + '<small>' + (d[3] ? d[2] : '🔒 준비 중') + '</small></button>';
    }).join('') + '</div>';
  }
  // [2026-11-18] 워프 포인트 관리인 -- 워프 포인트 내구도(모두 함께) + 거룡의 심장 조각. [2026-11-20] 남은 횟수는 숨기고 %만
  var wpKeep = null, wpKeepBusy = false;
  function wpKeepGo(act) {
    if (wpKeepBusy) return;
    wpKeepBusy = true;
    api('/api/tower-mine-archi?action=' + encodeURIComponent(act)).then(function (d) {
      wpKeepBusy = false;
      if (!d) return;
      if (d.ores) st.ores = d.ores;
      if (d.wps) wpKeep = d;
      if (d.message) { if (d.fixed) flashT0 = performance.now(); msg(d.message, 3200); }
      if (smithOpen && smithView === 'wpkeep') renderSmith(smithNpc);
    }).catch(function () { wpKeepBusy = false; msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function wpKeepHtml(n) {
    var h = npcHead('🌀 ' + n.name, false) + talkBlock(n, n.line), d = wpKeep;
    if (!d) return h + '<div class="mine-smith-say">워프 포인트를 살펴보는 중...</div>';
    var ic = '<img src="' + oreIcon(82) + '" alt="" style="width:16px;vertical-align:middle"> ';
    h += '<div class="mine-smith-say">' + ic + '거룡의 심장 조각 <b>' + (d.kits | 0) + '</b>개 · 조각 하나에 힘 +' + d.fixPct + '% · 마을 워프 포인트는 닳지 않아요<br><small>워프 포인트는 모두가 함께 써요. 오갈 때마다 출발지와 도착지의 힘이 조금씩 줄고, 0%가 되면 그곳으로 오가지 못해요. 조각은 월드보스 거룡 토벌 1~10위 보상이에요.</small></div>';
    var acts = {};
    d.wps.forEach(function (w) { (acts[w.act] = acts[w.act] || []).push(w); });
    Object.keys(acts).forEach(function (a) {
      h += '<div class="merc-job-h">' + a + '장</div>';
      acts[a].forEach(function (w) {
        var pct = w.pct | 0, low = pct <= 0, need = w.need | 0;
        h += '<div class="mine-smith-row"><span class="nm">' + escAttr(w.name) + '<small>' + (low ? '⛔ 힘이 다함' : '⚡') + ' ' + pct + '%</small><span class="mine-bar hp" style="margin-top:3px"><i style="width:' + pct + '%;background:' + (low ? '#ff5a4a' : pct < 30 ? '#ffb347' : '#7ad8ff') + '"></i></span></span>'
            + (need > 0 ? '<button type="button" ' + ((d.kits | 0) < 1 ? 'data-lack="1" ' : '') + 'onclick="TowerMine.wpFix(\'' + w.id + '\', 1)">+' + d.fixPct + '%</button>' + (need > 1 ? '<button type="button" ' + ((d.kits | 0) < 1 ? 'data-lack="1" ' : '') + 'onclick="TowerMine.wpFix(\'' + w.id + '\', ' + need + ')">가득(' + need + ')</button>' : '') : '<button type="button" data-lack="1" disabled>가득</button>') + '</div>';
      });
    });
    return h;
  }
  function sailGo(to) { closeSmith(); travel('/api/tower-mine-archi?action=sail:' + to, '배를 띄우지 못했어요.'); }
  function archiAct(action) {
    if (action === 'board') { closeSmith(); travel('/api/tower-mine-archi?action=board', '배를 타지 못했어요.'); return; }
    gearAct('/api/tower-mine-archi?action=' + action);
  }
  /** 다도해 낮/밤 -- 서버와 같은 식(리셋 기준 시각부터 dayCycleMs마다 앞 dayMs가 낮). */
  function archiDay(t) { if (map && map.alwaysNight) return false; /* [2026-11-18] 요새 도시는 늘 밤 */ return !map || !map.dayCycleMs || ((t - map.dayAnchor) % map.dayCycleMs + map.dayCycleMs) % map.dayCycleMs < map.dayMs; }
  function archiLeftMin(t) { var p = ((t - map.dayAnchor) % map.dayCycleMs + map.dayCycleMs) % map.dayCycleMs; return Math.max(1, Math.ceil(((p < map.dayMs ? map.dayMs : map.dayCycleMs) - p) / 60000)); }
  function openWorld() {
    if (!opened || !map) return;
    worldVB = null; // [2026-10-28] 열 때마다 지금 있는 곳을 가운데로
    openSmith({ name: '전체 지도', line: '', kind: 'world' }, 'world');
    bossListRefresh();
  }
  // [2026-11-18] "천둥 보스 소환 처치 수가 볼 때 안 바뀐다" -- 보스 목록은 들어올 때 한 번만 받아서 그대로였다 -> 지도/보스 목록을 열 때 새로 받는다
  var bossListAt = 0;
  function bossListRefresh() {
    var t = Date.now();
    if (t - bossListAt < 1500) return;
    bossListAt = t;
    api('/api/tower-mine-wb?action=bosses').then(function (d) {
      if (!d || !d.bosses || !st) return;
      st.bosses = d.bosses;
      if (d.bossRw) st.bossRw = d.bossRw; // [2026-11-20] 보스별 오늘 보상 횟수
      if (smithOpen && smithView === 'world') renderSmith(smithNpc);
    }).catch(function () { });
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
    var rbSub = rb >= RB_MAX ? rbStage(RB_MAX) + ' 완료' : ready ? (rb >= RB_BASE ? '지금 진환생할 수 있어요!' : '지금 환생할 수 있어요!') : 'Lv.100 이상일 때(원할 때만)';
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
  // [2026-11-18] 워프 포인트 창 -- "디아블로 웨이포인트 창 느낌, 지역(액트)별 1 2 3 4 탭, 못 간 곳은 꺼져 있고 직접 가서 만진 곳만 켜진다".
  //  돌판 테두리 + 숫자 탭 + 칸마다 문양 아이콘(켜짐 = 푸른 소용돌이, 꺼짐 = 새긴 X), 지금 서 있는 곳은 흐리게(여기).
  var wpData = null, wpTab = 1;
  function wpOpen(px2, py2) {
    wpData = null;
    openSmith({ name: '워프 포인트', line: '', kind: 'tele', px: px2, py: py2 }, 'tele');
    api('/api/tower-mine-wp?x=' + P.x + '&y=' + P.y + '&px=' + px2 + '&py=' + py2).then(function (d) {
      if (!d) return;
      if (!d.ok) { closeSmith(); if (d.message) msg(d.message, 2600); return; }
      wpData = d; wpTab = d.act || 1;
      if (d.newly) { msg('✨ 워프 포인트가 켜졌어요 -- ' + d.newly, 3600); killFeed('✨ 워프 포인트 켜짐: ' + d.newly, '#9fe0ff'); flashT0 = performance.now(); }
      if (smithOpen && smithView === 'tele') renderSmith(smithNpc);
    }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function wpTabGo(n) { wpTab = n; if (smithOpen && smithView === 'tele') renderSmith(smithNpc); }
  function teleHtml() {
    var d = wpData, h = '<div class="wp-box"><div class="wp-top"><b>🌀 워프 포인트</b><button type="button" class="mine-close" onclick="TowerMine.closeSmith()">✖ 닫기</button></div>';
    if (!d) return h + '<div class="wp-head">문양이 깨어나는 중...</div></div>';
    h += '<div class="wp-tabs">' + d.acts.map(function (a) {
      return '<button type="button" class="wp-tab' + (a.n === wpTab ? ' on' : '') + '" onclick="TowerMine.wpTab(' + a.n + ')">' + a.n + '<small>' + escAttr(a.name) + '</small></button>';
    }).join('') + '</div>';
    var act = d.acts.filter(function (a) { return a.n === wpTab; })[0] || d.acts[0];
    h += '<div class="wp-head">목적지를 고르세요</div><div class="wp-list">';
    var hereDead = d.herePct != null && d.herePct <= 0; // [2026-11-18] 내구도 -- 여기가 다했으면 어디로도 못 간다 // [2026-11-20] %만
    act.list.forEach(function (w) {
      var dead = !w.inf && w.pct != null && w.pct <= 0, can = w.on && !w.here && !dead && !hereDead;
      var cls = w.here ? 'here' : w.on && !dead ? '' : 'off';
      h += '<button type="button" class="wp-row ' + cls + '"' + (can ? ' onclick="TowerMine.teleport(\'wp:' + w.id + '\')"' : ' disabled') + '>'
          + '<span class="wp-ic ' + (w.on && !dead ? 'on' : 'off') + '"></span><span class="wp-nm">' + escAttr(w.name) + (w.here ? ' <small>(여기)</small>' : '')
          + (w.on ? ' <small style="color:' + (w.inf ? '#9fe0ff' : dead ? '#ff6a5a' : w.pct < 30 ? '#ffb347' : '#c8c0a8') + '">' + (w.inf ? '∞' : '⚡' + (w.pct | 0) + '%') + '</small>' : '') + '</span></button>';
    });
    if (hereDead) h += '<div class="wp-foot" style="color:#ff8a7a">⛔ 이 워프 포인트의 힘이 다했어요 -- 왕도 워프 포인트 관리인에게 거룡의 심장 조각으로 채워 달라고 하세요.</div>';
    return h + '</div><div class="wp-foot">' + act.on + ' / ' + act.list.length + ' 켜짐 · 꺼진 곳은 직접 가서 워프 포인트를 만지면 켜져요</div></div>';
  }
  // [2026-10-25] 이동 안내원 -- 안내원이 있는 마을끼리 무료로(왕도는 관리자만 보임)
  function guideHtml(n) {
    var admin = !!(st && st.adminMaps);
    // [2026-10-26] 설산 마을, [2026-10-27] 왕도는 한 번 걸어서 가 본 뒤에만
    var dests = (map.guideTo || []).filter(function (d) { return (!d.dev || admin) && (!d.needVisit || (st && (d.id === 'city' ? st.visitedCity : d.id === 'archi' ? st.visitedArchi : d.id === 'towercity' ? st.visitedTcity : st.visitedSnowtown))); });
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
    barber: { skin: '#f6d0b0', hair: '#f58fb8', cloth: '#f4f0f0', clothD: '#e86aa0' }, // [2026-11-20] 미용사(분홍 머리, 흰 가운)
    priest: { skin: '#f0c8a0', hair: '#b8bec8', cloth: '#24212b', clothD: '#141218' },
    guide: { skin: '#f2c194', hair: '#7a4a26', cloth: '#2f8a5a', clothD: '#1f6342' },
    blackmarket: { skin: '#c8a080', hair: '#1a1322', cloth: '#2a1f33', clothD: '#1a1322' }, // [2026-10-26]
    furrier: { skin: '#f2c194', hair: '#5a3a22', cloth: '#8a5a32', clothD: '#6a4222' },
    daykeeper: { skin: '#e8c09a', hair: '#e8e8e8', cloth: '#5a5560', clothD: '#46424c' }, // [2026-10-28] 요일 동굴 문지기
    refiner: { skin: '#c8906a', hair: '#2a1a12', cloth: '#4a1a1a', clothD: '#2a0e0e' }, // [2026-11-17] 악마 광석 정제사
    wallguard: { skin: '#d8a070', hair: '#3a2a1a', cloth: '#8a6a3a', clothD: '#5a4422' }, // [2026-11-04] 사막 경비병
    swordmaster: { skin: '#e8b48a', hair: '#1a1a22', cloth: '#7a2a2a', clothD: '#4a1818' }, // [2026-11-04] 검기 사범
    basecamp: { skin: '#e8b48a', hair: '#6a4a2a', cloth: '#3a6a9a', clothD: '#2a4a6a' }, // [2026-11-04] 베이스캠프 대장
    forestguard: { skin: '#d8a47a', hair: '#3a2a1a', cloth: '#4a5a2a', clothD: '#2a3a18' }, // [2026-11-05] 마물의 숲 입구 파수꾼
    citymerchant: { skin: '#e8b48a', hair: '#5a3a1a', cloth: '#2a7a4a', clothD: '#1a5030' }, // [2026-11-05] 왕도 상인
    mercenary: { skin: '#d8a070', hair: '#2a1a10', cloth: '#6a4a2a', clothD: '#4a3018' }, // [2026-11-05] 용병소 대장
    enhancer: { skin: '#c8885a', hair: '#3a3a3a', cloth: '#5a3a2a', clothD: '#2a1a12' }, // [2026-11-06] 무기 강화사(가죽 앞치마)
    armorer: { skin: '#d8a070', hair: '#8a8a8a', cloth: '#4a5a6a', clothD: '#2a3440' }, // [2026-11-18] 방어구 강화사(쇠사슬 앞치마)
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
  // [2026-11-20] "용병 고용 창에서 아래로 스크롤해 고르면 확인 창이 위쪽에 떠서 다시 올려야 한다" -- 확인 창(.eq-pop, position:fixed)은 NPC 창(.mine-smith)이
  //  transform으로 가운데 정렬돼 있어 화면이 아니라 그 창의 맨 위(스크롤 0)에 붙었다. 다시 그린 뒤 지금 보이는 자리(scrollTop)로 옮기고, 같은 창을 다시 그릴 땐 스크롤도 그대로.
  var smithRenderedView = null;
  function smithPopFit() {
    if (!el.smith) return;
    Array.prototype.forEach.call(el.smith.querySelectorAll('.eq-pop'), function (p) { p.style.top = el.smith.scrollTop + 'px'; p.style.bottom = 'auto'; p.style.left = '0'; p.style.right = '0'; p.style.height = el.smith.clientHeight + 'px'; });
  }
  function renderSmith(n) {
    var sy = el.smith ? el.smith.scrollTop : 0, sv0 = smithRenderedView;
    renderSmithView(n);
    if (el.smith && sv0 === smithView && el.smith.scrollTop !== sy) el.smith.scrollTop = sy;
    smithRenderedView = smithView;
    smithPopFit();
    startTalk(); // [2026-10-24] NPC 초상화/대사
  }
  function renderSmithView(n) {
    if (el.smith) el.smith.classList.toggle('merc-wide', smithView === 'merc'); // [2026-11-06] "PC는 옆 화면이 넓으니 용병소는 옆으로 넓게"
    if (el.smith) el.smith.setAttribute('data-sv', smithView || ''); // [2026-11-18] PC -- 창 종류마다 알맞은 폭(tower_view.jsp .mine-smith[data-sv])
    if (smithView === 'priest') {
      el.smith.innerHTML = priestHtml(n);
      return;
    }
    if (smithView === 'hermit') { el.smith.innerHTML = hermitHtml(n); return; } // [2026-10-30]
    if (smithView === 'bosscall') { el.smith.innerHTML = bossCallHtml(n); return; } // [2026-11-13]
    if (smithView === 'tower') { el.smith.innerHTML = towerHtml(n); return; } // [2026-11-15]
    if (smithView === 'token') { el.smith.innerHTML = tokenHtml(n); return; } // [2026-11-15]
    if (smithView === 'river') { el.smith.innerHTML = riverHtml(n); return; } // [2026-11-15]
    if (smithView === 'refine') { el.smith.innerHTML = refineHtml(n); return; } // [2026-11-17]
    if (smithView === 'primal') { el.smith.innerHTML = primalHtml(n); return; } // [2026-11-20]
    if (smithView === 'alchemy') { el.smith.innerHTML = alchemyHtml(n); return; } // [2026-11-20]
    if (smithView === 'wbboard') { el.smith.innerHTML = wbBoardHtml(n); return; } // [2026-11-17] 월드보스 게시판
    if (smithView === 'mail') { el.smith.innerHTML = mailHtml(n); return; } // [2026-11-17] 우편함
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
    if (smithView === 'aenh') { el.smith.innerHTML = npcHead('🛡️ ' + n.name, false) + talkBlock(n, n.line) + aenhHtml(true); return; } // [2026-11-18] 방어구 강화사
    if (smithView === 'merc') { // [2026-11-05] 왕도 용병소
      var ms2 = mercShop || {}, mi2 = st.merc || {}, mh = npcHead('🛡️ ' + n.name, false) + talkBlock(n, n.line);
      if (st.merc) mh += '<div class="mine-smith-say">함께하는 용병: ' + mercSumHtml(mi2) + '</div>';
      if (st.merc2) mh += '<div class="mine-smith-say">② 두 번째 용병: ' + mercSumHtml(st.merc2) + '</div>'; // [2026-11-18]
      if (!ms2.slot2Hide) mh += '<div class="mine-smith-say"><small>' + (ms2.slot2Open ? '② 두 번째 용병도 계약할 수 있어요 -- 같은 직업도 돼요 · ' + ((st.mercs || [])[3] && st.mercs[3].owned ? '바꾸면 지금 두 번째 용병의 레벨·경험치를 이어받아요 (Lv.' + ms2.slot2Lv + ')' : '처음 계약은 Lv.' + (ms2.slot2Lv || 70))
          : '🔒 두 번째 용병은 ' + (ms2.slot2Rb || 5) + '환생부터 계약할 수 있어요') + '</small></div>';
      // [2026-11-06] "처음 계약 때 동료 이미지, 속성, 레벨, 이름, 직업 정도 캐러셀로" -- 직업마다 후보 한 명(서버가 뽑아 둠), 계약하면 첫날 급여
      mh += '<div class="mine-smith-say"><small>' + (ms2.curLv > 0 ? '지금 용병과 같은 Lv.' + ms2.curLv + ' 동료들이에요 · 계약하면 지금 용병은 떠나고 레벨·경험치는 그대로 이어받아요' : '처음 계약은 Lv.5~30') // [2026-11-06] 교체 후보 / 급여 구간
        + '<br>' + (ms2.offers && ms2.offers.some(function (o) { return o.free; }) ? '교체는 무료예요 · 급여는 처음 계약할 때 한 번만 냈어요' : '급여는 처음 계약할 때 한 번만 내요(동괴 / 은괴 / 금괴 / 미스릴괴 중 하나, 가끔 세공된 보석) · 그 뒤로는 교체도 무료') + '</small></div>'; // [2026-11-11] 날마다 내던 급여 없앰
      if (ms2.offers) {
        // [2026-11-06] "직업별로 5명씩, 3줄로" -- 전사 / 궁수 / 마법사 줄(모바일은 줄마다 옆으로 넘기기, PC는 넓은 창에 5명이 한 줄로)
        var offCard = function (o) {
          return '<div class="cos-card merc-offer' + (o.owned ? ' worn' : '') + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(o.t, o.ni, o.nm)
              + '<div class="cos-nm">' + o.nm + '</div><small>' + MERC_ICON[o.t] + ' ' + o.job + ' · Lv.' + o.lv + '</small>'
              + '<small class="mo-el mo-el' + o.el + '">' + (EL_ICON[o.el] || '') + ' ' + o.elName + ' 특화</small>'
              + '<small class="mo-sk">' + o.skill + '</small>'
              + (o.owned ? '<span class="mine-worn">계약함</span>' : '<small>' + (o.free ? '🆓 무료' : '첫 급여 ' + o.sal) + '</small><button type="button" ' + (o.can ? '' : 'data-lack="1" ') + 'onclick="TowerMine.mercHire(' + o.i + ', 1)">' + (ms2.slot2Open ? '① 계약' : '계약') + '</button>'
                + (ms2.slot2Open ? '<button type="button" onclick="TowerMine.mercHire(' + o.i + ', 2)">② 계약</button>' : '')) + '</div>'; // [2026-11-18] 두 번째 칸
        };
        mh += mercViewBtns();
        [0, 1, 2].forEach(function (jt) {
          var row = ms2.offers.filter(function (o) { return (o.t | 0) === jt; });
          if (row.length) mh += '<div class="merc-job-h">' + MERC_ICON[jt] + ' ' + ['전사', '궁수', '마법사'][jt] + ' <small>' + row.length + '명</small></div><div class="cos-car merc-offers">' + row.map(offCard).join('') + '</div>';
        });
        var curM = (st.mercs || []).filter(function (x) { return x.owned && x.slot !== 2; })[0], cur2 = (st.mercs || [])[3] && st.mercs[3].owned ? st.mercs[3] : null; // [2026-11-18] 첫 칸 / 두 번째 칸
        if (curM) mh += '<div class="mine-smith-say"><small>다른 용병과 계약하면 지금 함께하는 <b>' + curM.nm + '</b>은(는) 떠나고 새 용병으로 바뀌어요.</small></div>';
        if (mercAskI != null) { // [2026-11-06] "계약할까요 멘트를 인게임 팝업으로"
          var ao = ms2.offers.filter(function (o) { return o.i === mercAskI; })[0];
          var two = mercAskS === 2, aLv = two ? ms2.slot2Lv : ao && ao.lv, aCur = two ? cur2 : curM; // [2026-11-18] 두 번째 칸 계약
          if (ao) mh += '<div class="eq-pop" onclick="if (event.target === this) TowerMine.mercHire(null)"><div class="eq-pop-box merc-ask"><div class="eq-pop-h">🛡️ ' + (two ? '두 번째 용병 계약' : '용병 계약') + '<button type="button" onclick="TowerMine.mercHire(null)">✖</button></div>'
              + '<div class="merc-ask-body' + (mercView === 'portrait' ? ' pt' : '') + '">' + mercFaceImg(ao.t, ao.ni, ao.nm) + '<div><b>' + ao.nm + '</b><br>' + MERC_ICON[ao.t] + ' ' + ao.job + ' · Lv.' + aLv + '<br><span class="mo-el' + ao.el + '">' + (EL_ICON[ao.el] || '') + ' ' + ao.elName + ' 특화</span><br><small>' + ao.skill + '</small></div></div>'
              + '<div class="mine-smith-say">' + ao.nm + '와(과) ' + (two ? '두 번째 용병으로 ' : '') + '계약할까요?<br><small>' + (two || ao.free ? '급여 없이 바로 계약해요.' : '첫 계약 급여 <b>' + ao.sal + '</b>를 바로 줘요. 앞으로 급여는 없어요(교체도 무료).') + '</small>'
              + (aCur ? '<br><small style="color:#ffb04a">⚠️ 지금 ' + (two ? '두 번째 ' : '') + '용병 <b>' + aCur.nm + '</b>(Lv.' + aCur.lv + ')은(는) 떠나요. 레벨·경험치는 새 용병이 이어받아요.</small>' : '') + '</div>'
              + '<div class="mine-smith-menu"><button type="button" onclick="TowerMine.mercHireGo(' + ao.i + ', ' + (two ? 2 : 1) + ')">계약하기</button><button type="button" onclick="TowerMine.mercHire(null)">취소</button></div></div></div>';
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
    if (smithView === 'wpkeep') { el.smith.innerHTML = wpKeepHtml(n); return; } // [2026-11-18] 워프 포인트 관리인
    if (smithView === 'sail') { el.smith.innerHTML = sailHtml(n); return; } // [2026-11-18] 바닷가 뱃사람/해상도시 부두지기
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
    if (smithView === 'barber') { carouselHtml(barberHtml(n)); return; } // [2026-11-20] 미용실
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
    ['gear', 'equip', 'wenh', 'aenh', 'armorRegen', 'gloveCode', 'archiShop', 'archi', 'dragon', 'sevenMax', 'galaxyMax', 'sevenDayNeed', 'deliverDone', 'ores', 'waterWalk', 'stepMs', 'look', 'cosShop', 'genderCosts', 'genderCan', 'durability', 'durabilityMax', 'ringMaps', 'ringReadyAt', 'hairStyles', 'hairDyes', 'hairStyleCur', 'hairDyeCur', 'barber'].forEach(function (k) { if (d[k] != null) st[k] = d[k]; });
    if (d.ringOff) { st.ringMaps = null; st.ringReadyAt = 0; } // [2026-11-20] 용의 반지를 빼면 지도 이동도 끝
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
    return mt && mt.boss ? { fx: h.fx + ((mt.foot || 2) - 1) / 2, fy: h.fy + 0.3 + ((mt.foot || 2) - 2) / 2 } : { fx: h.fx, fy: h.fy - 0.25 }; // [2026-11-15] 거룡 3x3 가운데
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
    var mercPre = !fight && [st.merc, st.merc2].some(function (q) { return q && q.mode === 1 && !(q.rest > nowSrv()); }) && (map.mobs || []).some(function (mb) { // [2026-11-06] 용병 선공 모드 -- 사거리 안 몬스터가 보이면 전투 턴(서버가 하나 골라 먼저 친다)
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
      if (d.bossSay && map && map.mobs && map.mobs.length) { var bsm = map.mobs[map.mobs.length - 1], bsp = bsm._p || bsm; talkBubble = { x: Math.round(bsp.x) + 1, y: Math.round(bsp.y) - 1, text: (d.bossSayName || '') + ': ' + d.bossSay, t0: performance.now() }; } // [2026-11-17] 보로네오 대사
      if (d.summoned) flashT0 = performance.now(); // [2026-10-28] "보스 스킬 쓸 때 가운데 멘트 없애줘, 특히 성당 보스" -- 번쩍임만
      (d.mobs || []).forEach(function (a) { mobSt[a[0]] = mobStOf(a, mobSt[a[0]]); });
      if (d.lava) lavaSet(d.lava);
      if (d.shock) shockSet(d.shock); // [2026-11-05] 천둥 황소 장판
      if (d.knockX != null) setPos(d.knockX, d.knockY); // [2026-11-05] 밀쳐내기
      if (d.slowUntil) slowUntil = d.slowUntil; // [2026-10-28] 여왕개미 독산
      if (d.stunUntil) { stunUntil = d.stunUntil; stunBand = (d.hits || []).some(function (q) { return q.ranged && q.stun; }); } // [2026-11-04] 파라오 미라 붕대 스턴
      if (d.roar) { flashT0 = performance.now(); msg('🐉 거룡의 포효! 잠깐 움직일 수 없어요.', 2000); } // [2026-11-15]
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
        if (h.krk) { krkSwingAdd(h.kx, h.ky, P.x, P.y); floats.push({ x: P.x, y: P.y - 0.3, text: h.dodge ? '💨 회피' : '🐙 -' + h.dmg, color: h.dodge ? '#c8c8ff' : '#ff4a6a', big: true, t0: t + KRK_SWING_MS * KRK_SWING_HIT }); return; } // [2026-11-19] 크라켄 -- 촉수가 내려친 순간에 피해 숫자
        if (h.dodge) { floats.push({ x: P.x, y: P.y - 0.3, text: '💨 회피', color: '#c8c8ff', big: true, t0: t + k * 160 }); return; } // [2026-10-30] 자객의 목걸이 은신
        if (h.zone) { flashT0 = t; floats.push({ x: P.x, y: P.y - 0.3 - 0.35 * (h.zk | 0), text: (['🔥 화염', '☄️ 심연의 비', '🌀 꼬리 휩쓸기 💫', '💥 멸망의 숨결', '☠️ 독 웅덩이', '🪨 낙석', '💢 지면 강타 💫', '☠️ 맹독 숨결', '🔥 지옥불', '✨ 심판의 빛', '👼 천사의 일격', '🐙 촉수 내려찍기', '🌊 물보라'][h.zk | 0] || '🔥') + ' -' + h.dmg, color: ['#ff8a2a', '#c88aff', '#ffe94a', '#ff5a6a'][h.zk | 0] || '#ff8a2a', big: true, t0: t }); return; } // [2026-11-15] 거룡 패턴
        floats.push({ x: P.x, y: P.y - 0.3, text: (h.x2 ? '🗡️ 2배! ' : h.heavy ? '💥 강타! ' : '') + '-' + h.dmg + (h.stun ? ' 💫 기절' : ''), color: h.x2 || h.heavy ? '#ff3a6a' : '#ff4a4a', big: true, t0: t + k * 160 }); // [2026-11-15] 탑 수문장 강타/기절
      }); // [2026-10-28] 은신했던 닌자의 기습
      if (d.merc) mercApply(d.merc); // [2026-11-05] 용병
      if (d.merc2) mercApply(d.merc2, true); // [2026-11-18] 두 번째 용병
      if (d.mercXp) mercXpApply(d.mercXp); // [2026-11-06] 용병 경험치
      if (d.mercXp2) mercXpApply(d.mercXp2);
      if (d.mercs) { st.mercs = d.mercs; if (smithOpen && smithView === 'mercmgr') renderSmith(smithNpc); } // [2026-11-06] 급여 차감/밀림
      if (d.mercPay) { mercPayNote(d.mercPay); if (!d.merc && st) st.merc = null; }
      if (d.levelUp) floats.push({ x: P.x, y: P.y - 1, text: '⬆️ 레벨 업!', color: '#ffe14d', big: true, t0: performance.now() });
      if ('pStealth' in d) pStealth = d.pStealth; // [2026-10-30]
      if (d.rewardMsg) { if (d.ores) st.ores = d.ores; flashT0 = t; msg(d.rewardMsg, 4200); } // [2026-10-28] 함께 싸운 닌자 두목 첫 처치 보상
      if (d.iceBroke && !d.died) { if (iceSt) iceSt.frozen = null; iceStruggleN = 0; flashT0 = t; floats.push({ x: P.x, y: P.y - 0.9, text: '🧊 쨍그랑! -' + d.iceBroke, color: '#bfefff', big: true, t0: t }); msg(d.message || '🧊 얼음이 깨졌어요!', 2800); } // [2026-11-18] 언 채로 맞으면 최대 체력 50% + 얼음이 깨진다
      if (d.combat) setCombat(d.combat);
      else if (st.combat && d.fight === false) { st.combat.fight = false; renderHp(); }
      // (인식 풀림 안내는 띄우지 않는다 -- 위 [2026-10-28])
      gloveFx(d); // [2026-11-20] 광전사 불굴 / 쌍격 기절
      if (d.died && d.wbDown) wbFall(d); // [2026-11-15] 월드보스 -- 그 자리에서 10초
      else if (d.died && d.state) fallDown(d);
      else if (d.boatWreck && d.state) fallDown(d); // [2026-11-18] 배가 부서져 마을로
      if (d.boatHp != null && st.archi) st.archi.boatHp = d.boatHp; // [2026-11-18] 배 체력
      if (d.boatHit && !d.boatWreck) floats.push({ x: P.x, y: P.y - 1.3, text: '⛵ 배 -' + d.boatHit + '%', color: '#9fe0ff', t0: performance.now() + 150 });
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
      gloveFx(d); // [2026-11-20] 광전사 불굴 / 쌍격 기절
      if (d.died && d.wbDown) wbFall(d); // [2026-11-15] 월드보스 -- 그 자리에서 10초
      else if (d.died && d.state) fallDown(d);
    };
  }
  var heatWarned = false;
  // [2026-10-28] 설산 봉우리 -- 얼음 여왕이 18초마다 한 명씩 얼린다(최대 15초). 언 동안 못 움직이고(서버도 막음), 여왕에게 맞으면 즉사([2026-11-18] -> 최대 체력 50% + 얼음이 깨짐).
  //  얼지 않은 동료가 2칸 안에 오면 풀린다(얼음땡). 서버가 환경 턴으로 알려 준다: iceAwake, nextFreezeAt, frozen {x, y, until}, thawed.
  var iceSt = null, iceWarned = 0;
  var stunUntil = 0; // [2026-11-04] 파라오 미라 붕대에 감김(서버 시각까지)
  function stunnedMe() { return stunUntil > nowSrv() || wbDownUntil > nowSrv(); } // [2026-11-15] + 월드보스에 쓰러져 있는 동안
  var stunBand = false; // 붕대에 감긴 기절인지(파라오 미라) -- 아니면 별이 도는 기절(히드라/거룡의 포효)
  // [2026-11-05] 천둥 황소 -- 전기 장판 칸, 번개 줄기(전광석화/밀쳐내기), 전체 낙뢰, 감전 스턴, 마물이 숨은 시각
  var shockCells = {}, zaps = [], stormT0 = 0, stormNext = 0, zapStunUntil = 0, hideNoticeT = 0, ZAP_MS = 450, thunderUntil = 0, thunderKills = 0, thunderNeed = 0, thunderRestAt = 0, ambZapT = 0;
  function shockSet(list) { var n = {}; (list || []).forEach(function (c) { n[(c % map.w) + ',' + Math.floor(c / map.w)] = 1; }); shockCells = n; }
  function zapAdd(x0, y0, x1, y1) { zaps.push({ x0: x0, y0: y0, x1: x1, y1: y1, t0: performance.now(), seed: Math.random() * 1000 }); }
  function stunMsg() { return wbDownUntil > nowSrv() ? '💫 쓰러져 있어요. 곧 다시 일어나요.' : zapStunUntil >= stunUntil ? '⚡ 감전되어 움직일 수 없어요!' : stunBand ? '🩹 붕대에 묶여 움직일 수 없어요!' : '💫 기절해서 움직일 수 없어요!'; }
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
  var merc = { x: null, y: null, lt: 0, dir: 'down', mv: false, atkT: -1e9, tg: null, trail: [], map: '' }, merc2 = { x: null, y: null, lt: 0, dir: 'down', mv: false, atkT: -1e9, tg: null }, mercShop = null, mercSpr = {}, mercImgC = {};
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
    return ty.boss ? [p.x + ((ty.foot || 2) - 1) / 2, p.y + 0.3 + ((ty.foot || 2) - 2) / 2] : [p.x + jx, p.y + jy - 0.15];
  }
  var mercRains = [];
  function mercRainDraw(now, TS, camX, camY) { // 화살비 -- 둘레 r칸 땅에 화살이 계속 꽂힌다(끝날 때까지)
    var tn = nowSrv();
    mercRains = mercRains.filter(function (r) { return r.until > tn; });
    mercRains.forEach(function (r) {
      var C = elc(r.el), s = TS / 16, cx = r.x * TS - camX + TS / 2, cy = r.y * TS - camY + TS / 2, R2 = (r.r + 0.5) * TS, left = Math.min(1, (r.until - tn) / 600);
      if (r.kind) { // [2026-11-18] 남는 토네이도(도는 물살) / 타는 땅(일렁이는 불꽃)
        if (now < r.t0) return;
        var tor = r.kind === 'tornado';
        ctx.save();
        ctx.globalAlpha = 0.18 * left; ctx.fillStyle = tor ? '#5ab4ff' : '#ff6a2a'; ctx.beginPath(); ctx.ellipse(cx, cy, R2, R2 * 0.75, 0, 0, Math.PI * 2); ctx.fill();
        for (var ti = 0; ti < 8 + r.r * 5; ti++) {
          if (tor) { var ta = now / 170 + ti * 0.8, tr = R2 * (0.2 + 0.75 * ((ti * 0.371) % 1)); fxDot(cx + Math.cos(ta) * tr, cy + Math.sin(ta) * tr * 0.75 - (ti % 3) * 2 * s, 1.8 * s, ti % 2 ? '#e8f6ff' : '#7ac8ff', 0.85 * left); }
          else { var bx = Math.sin(ti * 12.9898) * 43758.5453, by = Math.sin(ti * 78.233) * 12345.678; bx = (bx - Math.floor(bx)) * 2 - 1; by = (by - Math.floor(by)) * 2 - 1; var fl = Math.abs(Math.sin(now / 120 + ti)) * 3 * s; fxDot(cx + bx * R2 * 0.85, cy + by * R2 * 0.6 - fl, 2 * s, '#ffb030', left); fxDot(cx + bx * R2 * 0.85, cy + by * R2 * 0.6 - fl - 2.5 * s, 1.3 * s, '#ff4a1a', 0.8 * left); }
        }
        ctx.restore();
        return;
      }
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
  // [2026-11-18] 두 번째 용병은 내 옆(진행 방향 옆 칸)이나 두 걸음 뒤 -- 첫 용병과 겹치지 않게
  function mercSide(x, y, dir) {
    var v = MERC_DV[dir] || MERC_DV.down, a = [x + v[1], y + v[0]], b = [x - v[1], y - v[0]];
    return walk(a[0], a[1]) ? a : walk(b[0], b[1]) ? b : mercBehind(x, y, dir);
  }
  function mercDrawMine(now, V) {
    if (!st || !map || map.boat || (!st.merc && !st.merc2)) return;
    if (merc.map !== map.id) { merc.map = map.id; merc.x = null; merc.trail = []; merc.tg = null; merc2.x = null; merc2.tg = null; }
    var tr = merc.trail, l = tr[tr.length - 1];
    if (!l || l[0] !== P.x || l[1] !== P.y) { tr.push([P.x, P.y]); if (tr.length > 3) tr.shift(); } // [2026-11-07] "더 바짝 붙어서" -- 내가 한 걸음 전에 있던 칸([2026-11-18] 두 번째 용병은 두 걸음 전)
    if (st.merc) mercDrawOne(st.merc, merc, now, V, false);
    if (st.merc2) mercDrawOne(st.merc2, merc2, now, V, true);
  }
  function mercDrawOne(mi, mp, now, V, two) {
    var tr = merc.trail, home = function () { return two ? mercSide(P.x, P.y, P.dir) : mercBehind(P.x, P.y, P.dir); };
    var rest = mi.rest > nowSrv(), fight = !rest && mp.tg && now - mp.tg.t < 1800, g;
    if (rest) g = home();
    else if (fight && (mi.t | 0) > 0) g = mp.x != null && Math.abs(Math.round(mp.x) - P.x) + Math.abs(Math.round(mp.y) - P.y) <= 2 && !(Math.round(mp.x) === P.x && Math.round(mp.y) === P.y) ? [Math.round(mp.x), Math.round(mp.y)] : home(); // [2026-11-06] 궁수/마법사 -- 제자리(내 곁)에서 쏜다
    else if (fight) g = mp.tg.g || (mp.tg.g = mercBeside(mp.tg.x, mp.tg.y, Math.round(mp.x == null ? P.x : mp.x), Math.round(mp.y == null ? P.y : mp.y), P.x, P.y));
    else if (two) g = tr.length >= 3 ? tr[tr.length - 3] : home();
    else g = tr.length >= 2 ? tr[tr.length - 2] : home();
    if (g[0] === P.x && g[1] === P.y && !rest) g = home();
    mercMove(mp, g[0], g[1], now);
    if (fight && !mp.mv) mercFace(mp, mp.tg.x, mp.tg.y);
    mercDraw(mp, mi.t | 0, rest, mi.max ? mi.hp / mi.max : null, mp.atkT, now, V, mi.ni);
  }
  function mercDrawOther(ou, op, now, V, two) { // [2026-11-18] two = 두 번째 용병(ou.m2 = {mc, me, ms, ml, mr, mt})
    var q = two ? ou.m2 : ou, mk = two ? 'mp2' : 'mp', home = two ? mercSide : mercBehind;
    if (!q) return;
    var mp = ou[mk] || (ou[mk] = { x: null, y: null, dir: 'down' }), ox = Math.round(op.x), oy = Math.round(op.y), g;
    var mc = q.mc | 0;
    if (q.mt && !q.mr && mc > 0) { mp.gk = null; g = mp.x != null && Math.abs(Math.round(mp.x) - ox) + Math.abs(Math.round(mp.y) - oy) <= 2 && !(Math.round(mp.x) === ox && Math.round(mp.y) === oy) ? [Math.round(mp.x), Math.round(mp.y)] : home(ox, oy, ou.dir || 'down'); } // [2026-11-06] 원거리 직업
    else if (q.mt && !q.mr) {
      var gk = q.mt[0] + ',' + q.mt[1];
      if (mp.gk !== gk) { mp.gk = gk; mp.g = mercBeside(q.mt[0], q.mt[1], mp.x == null ? ox : Math.round(mp.x), mp.y == null ? oy : Math.round(mp.y), ox, oy); }
      g = mp.g;
    } else { mp.gk = null; g = home(ox, oy, ou.dir || 'down'); }
    mercMove(mp, g[0], g[1], now);
    if (q.mt && !q.mr && !mp.mv) mercFace(mp, q.mt[0], q.mt[1]);
    var at2 = q.mt && !q.mr ? now - now % 1000 : -1e9;
    if (at2 > 0 && mp.fxT !== at2 && mp.x != null) { mp.fxT = at2; mercShot(mc, q.me, q.ms, mp.x, mp.y, q.mt[0], q.mt[1], null, now); } // [2026-11-06] 다른 유저의 용병 이펙트(1초마다)
    mercDraw(mp, mc, q.mr, null, at2, now, V, q.ml);
  }
  /** 전투 턴 응답의 용병 소식 -- 체력/쉬기, 때린 몬스터(전사는 그 옆으로 달려감), 속성 이펙트, 피해 숫자, 드랍. [2026-11-06] 경험치는 내 것(문구에 용병 표시 없음). */
  function mercApply(m, two) { // [2026-11-18] two = 두 번째 용병
    if (!st) return;
    var mp = two ? merc2 : merc;
    if (two) st.merc2 = m; else st.merc = m;
    var t = performance.now(), mt = m.t | 0, f0 = { c: mt, el: m.el, s: m.st, k: mt === 2 ? 'ball' : '' }, fly = mercFly(f0), ei = EL_ICON[m.el] || '';
    var rowsAll = (m.row ? [m.row] : []).concat(m.rows || []), applyRows = function () { rowsAll.forEach(function (r) { mobSt[r[0]] = mobStOf(r, mobSt[r[0]]); }); }; // 둘레/체인 몬스터
    if (rowsAll.length) { var hitDelay = !m.sk ? fly + (mt ? 0 : 110) : m.sk.k === 'meteor' ? 600 : m.sk.k === 'tornado' ? 580 : m.sk.k === 'sword' ? 520 : Math.max(fly, 300); if (hitDelay > 30) setTimeout(applyRows, hitDelay); else applyRows(); } // [2026-11-06] 화살/구슬이 닿기 전에 몬스터가 먼저 쓰러져 사라지면 허공에 쏘는 것처럼 보였다
    if (m.tx != null) {
      if (!mp.tg || mp.tg.x !== m.tx || mp.tg.y !== m.ty) mp.tg = { x: m.tx, y: m.ty, t: t };
      else mp.tg.t = t;
      mp.atkT = t;
      mercShot(mt, m.el, m.st, mp.x == null ? P.x : mp.x, mp.y == null ? P.y : mp.y, m.tx, m.ty, m.sp, t + (mt ? 0 : 110), null, m.tg);
      if (mt === 0 && m.ms) mp.tg.g = [m.ms[0], m.ms[1]]; // [2026-11-06] 검사 용병이 서는 칸 = 서버가 검기를 내는 칸
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
      var sk = m.sk, mx0 = mp.x == null ? P.x : mp.x, my0 = mp.y == null ? P.y : mp.y;
      if (sk.k === 'rain') mercRains.push({ x: sk.x, y: sk.y, r: sk.r, until: sk.until, el: m.el, t0: t });
      else if (sk.k === 'sword') mercShot(0, m.el, sk.s, mx0, my0, sk.x, sk.y, sk.pts, t + 120, 'sword');
      else if (sk.k === 'meteor') { mercShot(2, 6, Math.max(3, sk.r), mx0, my0, sk.x, sk.y, null, t + 120, 'skill', m.tg); if (sk.until) mercRains.push({ x: sk.x, y: sk.y, r: sk.r, until: sk.until, el: 6, t0: t + 600, kind: 'burn' }); } // [2026-11-18] 타는 땅
      else if (sk.k === 'chain') mercShot(2, 2, sk.s, mx0, my0, sk.x, sk.y, sk.pts, t + 120, 'skill', m.tg);
      else if (sk.k === 'tornado') { mercShot(2, 3, sk.r * 2 - 1, mx0, my0, sk.x, sk.y, null, t + 120, 'skill', m.tg); if (sk.until) mercRains.push({ x: sk.x, y: sk.y, r: sk.r, until: sk.until, el: 3, t0: t + 580, kind: 'tornado' }); } // [2026-11-18] 4초 남는 토네이도
      floats.push({ x: mx0, y: my0 - 1, text: '✨ ' + sk.nm, color: '#ffe9a8', big: true, t0: t });
    }
    if (m.bossMsg) { msg(m.bossMsg, 5000); killFeed(m.bossMsg, '#ffe14d'); } // [2026-11-07] 용병이 쓰러뜨린 보스
    if (m.shock) shockSet([]);
    if (m.got && mp.x != null) floats.push({ x: mp.x, y: mp.y - 0.3, text: '-' + m.got, color: '#ff9a9a', t0: t + 200 });
    if (m.down) { mp.tg = null; killFeed('💤 ' + (m.nm || m.name) + '이(가) 지쳐서 뒤에서 쉬어요', '#c9c3d6'); }
    if (m.kexp) killFeed('내 경험치 +' + m.kexp, '#9fe0ff'); // [2026-11-08] kexp = 이번 턴 용병 처치 경험치(exp는 용병이 모은 경험치라 처치 없이도 늘 있었다) // [2026-11-06] "동료가 벌어 준 경험치는 표기할 필요 없고, 동료가 획득한 / 내가 획득한 경험치로" -- 용병이 잡은 몬스터 경험치도 내 것
    if (m.ores) st.ores = m.ores;
    if (m.drops) lootDrops(m.drops, m.tx, m.ty);
    if (m.dropGrade != null || m.dropShard) weaponLoot(m, m.tx, m.ty);
  }
  /** [2026-11-06] 용병 경험치(내 경험치의 1%) -- 용병 관리 칸, 레벨 업 알림. */
  function mercXpApply(x) {
    if (!st || !x) return;
    var two = x.slot === 2, cur = two ? st.merc2 : st.merc, mp = two ? merc2 : merc; // [2026-11-18] 두 번째 용병
    if (st.mercs) st.mercs[x.k != null ? x.k : x.t] = x;
    if (cur && (cur.k != null ? cur.k === x.k : cur.t === x.t)) { for (var p in x) if (p !== 'gain' && p !== 'up') cur[p] = x[p]; }
    if (x.gain) killFeed('🐱 동료 경험치 +' + x.gain, '#d8a8ff'); // [2026-11-06] 동료가 획득한 경험치
    if (x.gain && mp.x != null) floats.push({ x: mp.x, y: mp.y - 0.6, text: '+' + x.gain, color: '#9fe0ff', t0: performance.now() + 150 }); // [2026-11-06] 용병 경험치(처치마다 최소 1)
    if (x.up) { killFeed('🎉 ' + (x.nm || x.name) + ' Lv.' + x.lv + '!', '#ffe14d'); if (mp.x != null) floats.push({ x: mp.x, y: mp.y - 1, text: '⬆️ Lv.' + x.lv, color: '#ffe14d', big: true, t0: performance.now() }); }
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
  // [2026-11-18] 1인용 배 새 그림(시안 승인) -- 뒤(돛대·붉은 띠 돛·깃발·뒷전)는 캐릭터 앞에, 앞(금빛 뱃전·청록 선체·둥근 창·솟은 뱃머리·물보라)은 캐릭터 뒤에 그려
  //  배 안에 서 있는 모습. 왼쪽을 보면 좌우를 뒤집는다(뱃머리가 보는 쪽). (x, y) = 캐릭터 그림 왼쪽 위.
  function boatFlip(x, TS, left, fn) {
    if (!left) { fn(); return; }
    ctx.save(); ctx.translate(2 * x + TS, 0); ctx.scale(-1, 1); fn(); ctx.restore();
  }
  function drawBoat(x, y, now, left) {
    var TS = T * scale, u = TS / 16, bob = Math.round(Math.sin(now / 300) * u), R = function (c, a, b, w, h) { ctx.fillStyle = c; ctx.fillRect(x + a * u, y + b * u + bob, w * u, h * u); };
    boatFlip(x, TS, left, function () {
      R('#4a2c14', 6, -13, 2, 25); R('#7a4a22', 6, -13, 1, 25); R('#2e1a0a', 5, -2, 4, 1); // 돛대
      var sw = Math.sin(now / 420) * 0.6;
      ctx.fillStyle = '#f3ead2';
      ctx.beginPath(); ctx.moveTo(x + 8 * u, y - 12 * u + bob); ctx.quadraticCurveTo(x + (19 + sw) * u, y - 3 * u + bob, x + 22 * u, y + 7 * u + bob); ctx.lineTo(x + 8 * u, y + 8 * u + bob); ctx.closePath(); ctx.fill();
      ctx.save(); ctx.clip();
      R('#c8432f', 8, -4, 16, 2); R('#c8432f', 8, 2, 16, 2); R('rgba(120,90,60,.18)', 8, 5, 16, 3); R('rgba(255,255,255,.35)', 9, -10, 2, 16);
      ctx.restore();
      ctx.strokeStyle = '#c9a050'; ctx.lineWidth = Math.max(1, u * 0.7);
      ctx.beginPath(); ctx.moveTo(x + 8 * u, y - 12 * u + bob); ctx.quadraticCurveTo(x + (19 + sw) * u, y - 3 * u + bob, x + 22 * u, y + 7 * u + bob); ctx.stroke();
      var fl = Math.sin(now / 160); // 깃발
      ctx.fillStyle = '#e8463a'; ctx.beginPath(); ctx.moveTo(x + 6 * u, y - 16 * u + bob); ctx.lineTo(x + (1.5 - fl * 0.8) * u, y - (14.5 + fl * 0.6) * u + bob); ctx.lineTo(x + 6 * u, y - 13 * u + bob); ctx.closePath(); ctx.fill();
      R('#ffd76a', 6, -17, 2, 1);
      R('#5a3418', -3, 9, 23, 2); R('#b07a3c', -3, 9, 23, 1); // 뒷전
    });
  }
  // [2026-11-19] "용병이 바다 위에서 원거리 공격, 배 뒤에 타 있는 것처럼" -- 배 맵(boat)에선 용병이 걸어 따라오지 않고 내 배 고물(뒤로 늘인 칸)에 탄다.
  //  늘인 고물 = 용병 하나당 12px(16px 칸 기준). 뒤(난간)는 캐릭터보다 먼저, 앞(뱃전 띠)은 캐릭터 뒤에 그려 다리를 가린다. 좌우는 배와 같이 뒤집는다.
  var CREW_GAP = 12;
  function boatStern(x, y, now, left, n, front) {
    if (!n) return;
    var TS = T * scale, u = TS / 16, bob = Math.round(Math.sin(now / 300) * u), L = CREW_GAP * n + 2, R = function (c, a, b, w, h) { ctx.fillStyle = c; ctx.fillRect(x + a * u, y + b * u + bob, w * u, h * u); };
    boatFlip(x, TS, left, function () {
      if (!front) { R('#5a3418', -4 - L, 9, L + 1, 2); R('#b07a3c', -4 - L, 9, L + 1, 1); R('#3a2210', -6 - L, 5, 2, 10); R('#ffd76a', -7 - L, 4, 2, 2); return; } // 난간 + 고물 기둥 + 등
      R('#2f7f9a', -5 - L, 12, L + 2, 2); R('#4fa8c4', -5 - L, 12, L + 2, 1);
      R('#6b3f1e', -4 - L, 14, L + 1, 1); R('#4a2a12', -3 - L, 15, L, 1);
      R('#e0b050', -6 - L, 11, L + 2, 1);
      for (var i = 0; i < n; i++) R('#ffd76a', -4 - CREW_GAP * (i + 1) + 5, 12, 1, 2); // 둥근 창
      var ph = (now / 600) % 1; ctx.fillStyle = 'rgba(255,255,255,' + (0.45 * (1 - ph)).toFixed(2) + ')'; ctx.fillRect(x + (-8 - L - ph * 4) * u, y + 16 * u, (L + 4) * u, Math.max(1, u * 0.8)); // 물결
    });
  }
  /** 배에 탄 용병 {정보, 화면 자리} -- 내 것(st.merc/st.merc2) 또는 다른 사람(ou.mc/ou.m2). */
  function boatCrewMine() { var l = []; if (st && st.merc) l.push([st.merc, merc]); if (st && st.merc2) l.push([st.merc2, merc2]); return l; }
  function boatCrewDraw(crew, px, py, left, now, V, other) {
    crew.forEach(function (q, i) {
      var mi = q[0], mp = q[1], off = (CREW_GAP * (i + 1) + 1) / 16;
      mp.x = px + (left ? off : -off); mp.y = py; mp.mv = false;
      var rest = other ? !!mi.mr : mi.rest > nowSrv(), tgt = other ? (mi.mt && !mi.mr ? { x: mi.mt[0], y: mi.mt[1] } : null) : (mp.tg && now - mp.tg.t < 1800 ? mp.tg : null);
      if (tgt && !rest) mercFace(mp, tgt.x, tgt.y); else mp.dir = left ? 'left' : 'right';
      var t = other ? mi.mc | 0 : mi.t | 0, atkT = mp.atkT;
      if (other) { atkT = tgt && !rest ? now - now % 1000 : -1e9; if (atkT > 0 && mp.fxT !== atkT) { mp.fxT = atkT; mercShot(t, mi.me, mi.ms, mp.x, mp.y, tgt.x, tgt.y, null, now); } }
      mercDraw(mp, t, rest, other || !mi.max ? null : mi.hp / mi.max, atkT, now, V, other ? mi.ml : mi.ni);
    });
  }
  function drawBoatFront(x, y, now, left) {
    var TS = T * scale, u = TS / 16, bob = Math.round(Math.sin(now / 300) * u), R = function (c, a, b, w, h) { ctx.fillStyle = c; ctx.fillRect(x + a * u, y + b * u + bob, w * u, h * u); };
    var ph = (now / 600) % 1;
    ctx.fillStyle = 'rgba(255,255,255,' + (0.45 * (1 - ph)).toFixed(2) + ')';
    ctx.fillRect(x - (5 + ph * 4) * u, y + 16 * u, (26 + ph * 8) * u, Math.max(1, u * 0.8));
    boatFlip(x, TS, left, function () {
      R('#3a2210', -4, 10, 2, 5);
      R('#2f7f9a', -3, 12, 23, 2); R('#4fa8c4', -3, 12, 23, 1);
      R('#6b3f1e', -2, 14, 21, 1); R('#4a2a12', -1, 15, 18, 1);
      R('#2f7f9a', 20, 12, 1, 1); R('#6b3f1e', 19, 14, 1, 1);
      R('#e0b050', -4, 11, 24, 1); R('#fff0b0', -4, 11, 8, 1);
      R('#e0b050', 19, 10, 2, 1); R('#e0b050', 20, 9, 2, 1); R('#e0b050', 21, 8, 1, 1); R('#3a2210', 20, 10, 1, 2);
      R('#ffd76a', 14, 12, 2, 2); R('#1d4a5a', 15, 13, 1, 1);
      R('#ffd76a', 3, 12, 1, 2); R('#ffd76a', 9, 12, 1, 2);
      var s1 = Math.floor(now / 120) % 3;
      ctx.fillStyle = 'rgba(255,255,255,.8)';
      ctx.fillRect(x + (20 + s1) * u, y + (14 - s1 * 0.5) * u + bob, u, u);
      ctx.fillRect(x + (-6 - s1) * u, y + 15 * u + bob, 2 * u, u);
      ctx.fillRect(x + (-9 - s1 * 2) * u, y + 16 * u, u, u);
    });
  }
  /** 내 배 체력(배 밑 하늘색 막대, 30% 아래면 빨강). */
  function drawBoatHp(x, y) {
    var a = st && st.archi; if (!a || !a.boatHpMax) return;
    var u = T * scale / 16, w = 20 * u, f = Math.max(0, Math.min(1, (a.boatHp || 0) / a.boatHpMax));
    ctx.fillStyle = 'rgba(0,0,0,.55)'; ctx.fillRect(x - 2 * u, y + 18 * u, w, 2 * u);
    ctx.fillStyle = f > 0.3 ? '#4fc3f7' : '#ff6b5a'; ctx.fillRect(x - 2 * u, y + 18 * u, w * f, 2 * u);
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
    apiFast('/api/tower-mine-tick?mapId=' + encodeURIComponent(mid) + '&x=' + P.x + '&y=' + P.y + '&d=' + P.dir + verQ() + (tc ? '&c=1' : '') + (te ? '&e=1' : '') + (tm ? '&m=1' : '') + (st && ((st.merc && st.merc.mode === 1) || (st.merc2 && st.merc2.mode === 1)) ? '&vw=' + viewHalf()[0] + '&vh=' + viewHalf()[1] : '') + '&cid=' + mineCid + (netAvg >= 0 ? '&pg=' + netAvg + '&dv=' + NET_DV + '&nw=' + (pws ? 1 : 0) + '&fg=' + netGap + '&bg=' + (document.hidden ? 1 : 0) + '&wv=' + (pws ? wsVia : 0) : '')).then(function (r) {
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
      unityShow(d && d.unity); // [2026-11-17] 우리는 하나
      if (d && d.villageOut) { // [2026-11-17] 마계 마을 -- 3초 뒤 안내 없이 요새 도시로, 그다음 한 줄
        var vmsg = d.villageOut; busy = true; if (wbDownUntil) { wbDownUntil = 0; wbDownShow(); } // [2026-11-19] 바다 위에 쓰러져 있다 배가 부서져 마을로 -- 회색 화면도 끝
        api('/api/tower-mine').then(enterState).then(function () { fadeIn(null); flashT0 = performance.now(); msg(vmsg, 4500); }).catch(function () { busy = false; fadeIn(null); });
        return;
      }
      if (d && d.online != null) { onlineN = d.online; mapN = d.mapN || {}; regenText(); worldCounts(); } // [2026-10-30] 접속 인원
      if (d && d.twLeft != null && st && st.twLeft !== d.twLeft) { st.twLeft = d.twLeft; twRender(); } // [2026-11-19] 수문장 반복 보상 남은 수
      if (d && d.wn) { var wnk = JSON.stringify(d.wn); if (wnk !== wanderKey) { wanderKey = wnk; wanderAt = d.wn; if (smithOpen && smithView === 'world') renderSmith(smithNpc); } } // [2026-11-21] 떠돌이 NPC 자리(전체 지도)
      if (!map || map.id !== mid || !d) return;
      hermit = d.hermit ? { m: mid, x: d.hermit.x, y: d.hermit.y, until: d.hermit.until } : null; // [2026-10-30]
      armorer = d.armorer ? { m: mid, x: d.armorer.x, y: d.armorer.y, until: d.armorer.until } : null; // [2026-11-20] 떠돌이 갑옷장이
      if (d.armorerAdmin && d.armorerAdmin !== armorerAdminSaid) { armorerAdminSaid = d.armorerAdmin; msg('🕵️ 관리자 안내: 떠돌이 갑옷장이 ' + d.armorerAdmin, 5000); }
      if (d.boatHp != null && st && st.archi) { // [2026-11-18] 망망대해 -- 초당 2.4%([2026-11-19] 예전 5%), 줄어든 만큼 배 위에 띄운다
        var bh0 = st.archi.boatHp, bmx = st.archi.boatHpMax || 1;
        if (bh0 != null && d.boatHp < bh0) floats.push({ x: P.x, y: P.y - 1.4, text: '⛵ -' + Math.max(1, Math.round((bh0 - d.boatHp) * 100 / bmx)) + '%', color: d.boatHp * 10 < bmx * 3 ? '#ff6a5a' : '#9fe0ff', big: true, t0: performance.now() });
        st.archi.boatHp = d.boatHp;
      }
      seaSt = d.sea || null; if (krkShown() !== krkShownWas) { krkShownWas = krkShown(); if (krkShownWas) { flashT0 = performance.now(); msg('🐙 바닷속에서 크라켄이 솟아올랐어요!', 2600); } } if (seaSt && seaSt.msg && seaSt.msgAt !== seaMsgSeen) { seaMsgSeen = seaSt.msgAt; flashT0 = performance.now(); msg(seaSt.msg, 3200); } // [2026-11-18] 크라켄 해역 소식
      mapZones = d.zones || []; wbInfo = d.wb || null; wbRender(); wbNotice(); // [2026-11-15] 월드보스, [2026-11-16] 등장 알림
      if (d.wbGone && d.wbGone !== wbGoneSeen) { wbGoneSeen = d.wbGone; if (map && map.id === 'castle' && nowSrv() - d.wbGone < 6000) { wbLeave = { t0: performance.now() }; msg('🐉 심연의 거룡이 날개를 펴고 하늘로 날아올랐어요...', 2800); } } // [2026-11-16] 퇴장 연출
      twRender(); // [2026-11-15] 탑
      if (d.bossFx && d.bossFx.at !== bossFxSeen) { bossFxSeen = d.bossFx.at; bossLoot(d.bossFx); } // [2026-11-16] 보스가 막 쓰러졌다 -- 전리품이 쏟아진다
      bossNpc = d.bossCall ? { m: mid, x: d.bossCall.x, y: d.bossCall.y, at: d.bossCall.at || (bossNpc && bossNpc.m === mid && bossNpc.at > nowSrv() ? bossNpc.at : 0) } : null; // [2026-11-13] 보스 소환사(at = 카운트가 끝나 보스가 나오는 시각)
      if (d.hermitAdmin && d.hermitAdmin !== hermitAdminSaid) { hermitAdminSaid = d.hermitAdmin; msg('🕵️ 관리자 안내: 은둔한 대장장이 ' + d.hermitAdmin, 5000); }
      var now = performance.now(), seen = {};
      (d.others || []).forEach(function (o) {
        seen[o.id] = 1;
        var cur = others[o.id];
        if (!cur) others[o.id] = cur = { x: o.x, y: o.y, fx: o.x, fy: o.y, t0: now };
        else if (cur.x !== o.x || cur.y !== o.y) { var pp = otherPos(cur, now); cur.fx = pp.x; cur.fy = pp.y; cur.x = o.x; cur.y = o.y; cur.t0 = now; }
        cur.dir = o.d || 'down'; cur.n = o.n || ''; cur.look = o.look || {}; cur.bubble = o.bubble || ''; cur.frozen = !!o.frozen; cur.stealth = !!o.stealth; cur.mc = o.mc; cur.mr = !!o.mr; cur.mt = o.mt || null; cur.me = o.me; cur.ms = o.ms; cur.ml = o.ml; cur.m2 = o.m2 || null; /* [2026-11-18] 두 번째 용병, 속성/단계/모습 */ // [2026-11-05] 용병 // [2026-10-28] 얼음 여왕 빙결
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
    ctx.drawImage(w.c, x - w.ext * hs, y - w.top * hs, w.c.width * hs / (w.R || 1), w.c.height * hs / (w.R || 1)); // [2026-11-20] 64x64면 날개 그림이 2배(R)
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
    var anc = 0; (dmgSum ? [] : d.aura || []).forEach(function (h) { if (h.length > 3 && h[3] + 1 > anc) anc = h[3] + 1; }); // [2026-11-15] 보스가 검기에 걸친 칸 수 -- 칸마다 숫자를 옆으로 벌려 따로
    (dmgSum ? [] : d.aura || []).forEach(function (h, i) { // [2026-11-20] 합산이면 맞은 몬스터 숫자는 공격 쪽에서 하나로
      var q = h.length > 3 ? h[4] : i, ox = h.length > 3 ? (h[3] - (anc - 1) / 2) * 0.9 : 0;
      floats.push({ x: fxp + ox + (q % 2 ? 0.3 : -0.3) * (anc > 1 ? 0.5 : 1), y: fyp - 0.32 * (q + 1), text: ELEM_ICON[h[0]] + h[1] + (h[2] ? ' x1.5' : ''), color: AURA_COL[h[0]], big: !!h[2], t0: t + 70 * (q + 1) + (h.length > 3 ? 45 * h[3] : 0) });
    });
    var anyKill = false;
    (d.auraMobs || []).forEach(function (am, j) {
      var mm = map.mobs && map.mobs[am.id], p = mm && mm._p ? { x: mm._p.x, y: mm._p.y } : { x: am.row[3], y: am.row[4] };
      mobSt[am.id] = mobStOf(am.row, mobSt[am.id]);
      mobHitAt[am.id] = performance.now();
      var dl = 60 + j * 40;
      if (dmgSum && (am.hits || []).length) { var st2 = 0, x15 = false; am.hits.forEach(function (h) { st2 += h[1] | 0; if (h[2]) x15 = true; }); floats.push({ x: p.x, y: p.y - 0.2, text: '⚔️' + st2.toLocaleString() + (x15 ? ' x1.5' : ''), color: x15 ? '#ff8a3a' : '#ffe9a8', big: x15, t0: t + dl }); } // [2026-11-20] 합산
      else (am.hits || []).forEach(function (h, i) {
        var cc = am.cells && h.length > 3 ? am.cells[h[3]] : null, q = cc ? h[4] : i, bx = cc ? am.row[3] + cc[0] : p.x, by = cc ? am.row[4] + cc[1] : p.y; // [2026-11-15] 보스가 걸친 칸마다 그 칸 위에 따로
        floats.push({ x: bx + (q % 2 ? 0.3 : -0.3) * (cc ? 0.5 : 1), y: by - 0.32 * q, text: ELEM_ICON[h[0]] + h[1] + (h[2] ? ' x1.5' : ''), color: AURA_COL[h[0]], big: !!h[2], t0: t + dl + 70 * q + (cc ? 40 * h[3] : 0) });
      });
      if (am.killed) { anyKill = true; if (!d.expFull) floats.push({ x: p.x, y: p.y + 0.4, text: '+' + am.exp + ' EXP', color: '#9fe0ff', t0: t + dl + 100 }); } // [2026-11-07] 경험치가 가득 찬(안 오르는) 처치는 EXP 숫자를 안 띄운다
    });
    lootDrops(d.auraDrops, fxp, fyp); // [2026-11-04] 화면 구석 카드
    if (!d.killed && d.dropGrade != null) { flashT0 = t; weaponLoot(d, fxp, fyp); }
    if (anyKill && d.ores) st.ores = d.ores;
    // [2026-11-05] "쓰러뜨렸어요 문구 빼고 경험치 몇 올랐는지만, 검은 상자 말고 왼쪽 빈 화면에 글씨만 띄웠다 서서히 사라지게" -- 일반 공격 처치 + 검기 처치를 합쳐 한 줄
    if (d.mercXp) mercXpApply(d.mercXp); // [2026-11-06] 용병 경험치(내 경험치의 1%)
    if (d.mercXp2) mercXpApply(d.mercXp2); // [2026-11-18] 두 번째 용병
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
  var AURA_BOSS = { 1: [39, '피라미드(파라오 미라)', 57], 4: [33, '설산(얼음 여왕)', 55], 6: [64, '화산(용암 거신)', 56], 7: [78, '성당(타락한 대주교)', 59] /* [2026-11-18] 어둠의 심장 */, 2: [65, '마물의 숲(천둥 황소)', 58], 5: [71, '하피의 둥지(하피 여왕)', 55], 8: [77, '최후의 방어선(심판자 보로네오)', 55], 3: [81, '바다 위(심해의 크라켄)', 55] /* [2026-11-19] 물 = 물의 심장 + 세공 진주(바다 위를 모두에게 열어 넣음) */ }, /* [2026-11-17] 빛 = 빛의 심장 + 세공 진주 */ /* [2026-11-11] 바람 = 바람의 심장 + 세공 진주 */ MOON_G = 20; // [2026-11-04] 세 번째 = 세공 바다 보석 등급
  function oreHave(g) { var q = 0; (st.ores || []).forEach(function (o) { if (o.grade === g) q = o.qty; }); return q; }
  function auraNpcHtml(n) {
    var au = (st && st.aura) || { l: [], a: [] }, moon = oreHave(MOON_G);
    var h = npcHead('🌀 ' + (n ? n.name : '검기 사범'), false) + talkBlock(n, n ? n.line : '')
        + '<div class="mine-smith-say">단계마다 공격력의 5% 속성 피해(최대 25%), 사거리 +1칸(최대 5칸). n단계 = 보스 드랍템 (2n-2, 1단계는 1) + 달빛석 (2n-2, 1단계는 1) + 세공된 바다 보석 4개 · 달빛석 보유 ' + moon + '</div>';
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
      var c = Math.max(1, 2 * (L + 1) - 2), hb = oreHave(b[0]), hs = oreHave(b[2]), ok = hb >= c && moon >= c && hs >= 4; // [2026-11-17] 비용 낮춤(서버 mineAuraCost)
      h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>' + (L + 1) + '단계: ' + ORE_NAME[b[0]] + ' ' + c + ' (보유 ' + hb + ') + 달빛석 ' + c + ' + ' + ORE_NAME[b[2]] + ' 4 (보유 ' + hs + ') · ' + b[1] + '</small></span>'
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
    ask('🎁 2환생 특전', '한 번만 받을 수 있어요. ' + ELEM_NAME[e] + ' 검기 1단계를 받을까요?', '받기', function () { auraGiftGo(e); }); // [2026-11-20] confirm() 대신
  }
  function auraGiftGo(e) {
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
      var pU = p;
      (am.hits || []).forEach(function (h, hi0) { var cc = am.cells && h.length > 3 ? am.cells[h[3]] : null, hi = cc ? h[4] : hi0, p = cc ? { x: am.row[3] + cc[0], y: am.row[4] + cc[1] } : pU; var hd = k === 0 ? hi * 250 + (cc ? 60 * h[3] : 0) : 0; /* [2026-11-15] 보스가 걸친 칸마다 그 칸 위에 따로 */ novaFx.push({ t0: t + dl + hd, mx: p.x, my: p.y, col: k === 1 ? '#8a6aff' : '#ffb02a' }); floats.push({ x: p.x + (k === 0 ? (hi % 2 ? 0.25 : -0.25) : 0), y: p.y - 0.2, text: (h[2] ? '💥' : '') + h[1], color: h[2] ? '#ffd23f' : k === 1 ? '#e8dcff' : '#ffe9a8', big: k === 1 || !!h[2], t0: t + dl + 60 + hd }); });
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
    if (d.mercXp2) mercXpApply(d.mercXp2); // [2026-11-18]
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
    ask('🎁 3환생 특전', '한 번만 받을 수 있어요. ' + ULT_NAME[k] + ' 1레벨을 받을까요?', '받기', function () { ultGiftGo(k); }); // [2026-11-20] confirm() 대신
  }
  function ultGiftGo(k) {
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
    h += '<div class="mine-smith-say">공격하면 내 둘레 8칸을 한꺼번에 베어요. 단계마다 공격력의 5% 속성 피해(최대 25%). 8속성 모두 배울 수 있어요. n단계 = 보스 드랍템 (2n-2, 1단계는 1) + 달빛석 (2n-2, 1단계는 1) + 세공된 바다 보석 4개(보스가 없는 속성은 별빛석 (2n-1) + 달빛석 (2n-1)) · 달빛석 보유 ' + moon + '</div>';
    for (var e = 1; e <= 8; e++) {
      var L = (wl.l && wl.l[e]) || 0, b = AURA_BOSS[e] || [6, '보스가 없는 속성 -- 별빛석으로', 0]; // [2026-11-08] "돌개는 모든 속성" -- 보스가 없는 속성은 별빛석 + 달빛석
      var head = '<span class="nm"><span style="color:' + AURA_COL[e] + '">' + ELEM_ICON[e] + ' ' + ELEM_NAME[e] + ' 돌개베기</span> ' + L + '/5';
      if (L >= 5) { h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>끝까지 익혔어요</small></span><button type="button" data-lack="1" disabled>완료</button></div>'; continue; }
      var c = Math.max(1, 2 * (L + 1) - 2), hb = oreHave(b[0]), hs = b[2] ? oreHave(b[2]) : 99, ok = hb >= c && moon >= c && hs >= 4; // [2026-11-17] 비용 낮춤
      h += '<div class="mine-smith-row"><img src="' + oreIcon(b[0]) + '" alt="">' + head + '<small>' + (L + 1) + '단계: ' + ORE_NAME[b[0]] + ' ' + c + ' (보유 ' + hb + ') + 달빛석 ' + c + (b[2] ? ' + ' + ORE_NAME[b[2]] + ' 4 (보유 ' + hs + ')' : '') + ' · ' + b[1] + '</small></span>'
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
    if (!a && !P.moving && wpHere() && wpOpenHere()) { if (wpStand) wpStand.done = true; return; } // [2026-11-18] 워프 포인트 위에서 공격 버튼 = 창
    if (!a && !P.moving && lhNear()) { lhBell(lhNear()); return; } // [2026-11-18] 등대 섬 위에서 공격 버튼 = 종
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
      if (d.twinStun) floats.push({ x: fxp, y: fyp - 1.0, text: '💫 기절!', color: '#ffe14d', t0: t + 200 }); // [2026-11-20] 쌍격의 장갑
      if (d.evade) floats.push({ x: fxp, y: fyp, text: d.stealthLeft ? '💨 은신! (' + d.stealthLeft + ')' : '💨 은신!', color: '#c8c8ff', big: true, t0: t }); // [2026-10-28] 닌자 두목 -- 남은 공격 수
      else if (d.clone) floats.push({ x: fxp, y: fyp, text: '💨 펑! 분신', color: '#d8c8ff', big: true, t0: t });
      else if (dmgSum) { // [2026-11-20] "합산 데미지를 한 번으로 보여 주는 옵션" -- 일반 공격(쌍격 두 번 포함) + 검기 속성 피해를 더해 숫자 하나
        var tot = d.hits ? d.hits.reduce(function (a2, b2) { return a2 + b2; }, 0) : d.dmg | 0, el15 = !!d.elHit;
        (d.aura || []).forEach(function (h) { tot += h[1] | 0; if (h[2]) el15 = true; });
        floats.push({ x: fxp, y: fyp, text: (d.assassinHit ? '🗡️ 2배! ' : '') + (d.crit ? '💥' : '') + atkEl + tot.toLocaleString() + (el15 ? ' x1.5' : ''), color: d.assassinHit ? '#ff3a6a' : d.crit ? '#ffd23f' : el15 ? '#ff8a3a' : '#ffffff', big: !!(d.crit || d.assassinHit), t0: t });
      }
      else if (d.hits) d.hits.forEach(function (hv, hi) { var hc = d.hitCrits && d.hitCrits[hi]; floats.push({ x: fxp + (hi ? 0.35 : -0.25), y: fyp - hi * 0.35, text: (d.assassinHit ? '🗡️' : '') + (hc ? '💥' : '') + atkEl + hv + (d.elHit ? ' x1.5' : ''), color: d.elHit ? '#7fe8ff' : hc ? '#ffdd33' : '#ff5a4a', big: !!hc, t0: t + hi * 140 }); }); // [2026-10-30] 쌍격의 장갑 -- 숫자 두 개
      else floats.push({ x: fxp, y: fyp, text: (d.assassinHit ? '🗡️ 2배! ' : '') + (d.crit ? '💥' : '') + atkEl + d.dmg + (d.elHit ? ' x1.5' : ''), color: d.assassinHit ? '#ff3a6a' : d.elHit ? '#7fe8ff' : d.crit ? '#ffdd33' : '#ff5a4a', big: !!(d.crit || d.elHit || d.assassinHit), t0: t }); // [2026-10-29] 속성 상성 x1.5 / [2026-10-30] 자객의 목걸이 2배
      auraShow(d, fxp, fyp, t); // [2026-11-04] 검기 -- 속성 숫자 + 너머 몬스터
      if (d.simDown) msg('🎯 ' + ((map.mobTypes[m.k] || {}).name || '보스 허수아비') + '의 체력이 0이 됐어요! 3초 뒤 다 찬 채로 다시 일어나요.', 2400); // [2026-11-07] 보스 허수아비
      if ('pStealth' in d) pStealth = d.pStealth; // [2026-10-30] 자객의 목걸이
      if (d.assassinOn) floats.push({ x: P.x, y: P.y - 0.6, text: '🥷 은신!', color: '#c8c8ff', big: true, t0: t + 80 });
      if (d.dodged) floats.push({ x: P.x, y: P.y - 0.3, text: '💨 회피', color: '#c8c8ff', big: true, t0: t + 180 });
      if (d.enchLeft != null && st.ench) { st.ench.left = d.enchLeft; if (!d.enchLeft) st.ench.el = 0; updEnch(); }
      if (d.counter) floats.push({ x: P.x, y: P.y - 0.3, text: (d.stealthHit ? '🗡️ 2배! ' : d.heavy ? '💥 강타! ' : '') + '-' + d.counter + (d.stunHit ? ' 💫 기절' : ''), color: d.stealthHit || d.heavy ? '#ff3a6a' : '#ff9a3a', big: !!(d.stealthHit || d.heavy), t0: t + 180 }); // [2026-11-15] 탑 수문장 강타/기절
      if (d.stunHit && d.stunUntil) { stunUntil = d.stunUntil; stunBand = false; }
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
      // [2026-10-25] 최대 체력 잠금 -- 새 전투마다 10%(최대 50%), 레벨업하면 풀림
      if (d.hpLockUp) floats.push({ x: P.x, y: P.y - 0.9, text: '🔒 최대 체력 -' + d.hpLockUp + '%', color: '#c9c3d6', t0: t + 300 });
      if (d.hpLockFree) msg('🔓 레벨 업! 잠겼던 최대 체력이 풀렸어요.', 2600);
      gloveFx(d); // [2026-11-20] 광전사 불굴 / 쌍격 기절
      if (d.died && d.wbDown) wbFall(d); // [2026-11-15] 월드보스 -- 그 자리에서 10초
      else if (d.died && d.state) {
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
  function mercHire(t, s) { // [2026-11-05] 왕도 용병소 -- 더 센 용병으로 바꿔 고용
    if (busy) return;
    mercAskI = t; // [2026-11-06] 브라우저 확인창 대신 게임 안 팝업(t = 후보 칸, null = 닫기)
    mercAskS = s === 2 ? 2 : 1; // [2026-11-18] 2 = 두 번째 칸
    if (smithOpen) renderSmith(smithNpc);
  }
  var mercAskI = null, mercAskS = 1;
  function mercHireGo(t, s) {
    if (busy) return;
    mercAskI = null;
    busy = true;
    api('/api/tower-mine-merc?action=' + (s === 2 ? 'hire2' : 'hire') + '&tier=' + t).then(function (d) {
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
    smithRenderedView = null; // [2026-11-20] 다음에 열면 맨 위부터
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
    return x < wx && P.x >= wx && myLv() < ANT_GATE_LV && !(st && st.combat && (st.combat.rebirth | 0) > 0); // [2026-11-18] "사막 입장 레벨 -- 환생자는 제한 없이"
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
        return enterState(d.state).then(function () { twRoomEnter(d); var twr = map.tower && st.twRun && st.twRun.total ? st.twRun : null; fadeIn(twr && TW_ROOM[twr.type] ? TW_ROOM[twr.type] : twr && twr.step > 0 ? '🚪 빈 방' : '🗺️ ' + map.name); /* [2026-11-16] 탑 방은 무슨 방인지 크게 */ msg(map.wind ? '🌪️ 모래바람이 불어요! 가만히 있어도 옆으로 밀려나요.' : (d.message || ''), 2600); });
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
    if (p.kind === 'X') { // [2026-10-24] 왕도 텔레포트 게이트 -- 목적지 고르기, [2026-11-18] 워프 포인트(만지면 켜진다)
      var tk = 'tele' + px2 + ',' + py2;
      if (lastBump === tk && now - lastBumpT < 1200) return;
      lastBump = tk; lastBumpT = now;
      setAuto(false);
      wpOpen(px2, py2);
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
    var W = el.cv.width, H = el.cv.height, pos = curPos(now), cam = krkView(now, W, H, pos), TS = T * scale;
    var camX = Math.round(cam.x * TS + TS / 2 - W / 2), camY = Math.round(cam.y * TS + TS / 2 - H / 2);
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
          if (np && !tiles.npc[nk]) tiles.npc[nk] = npcSprite(nk); // [2026-11-18] "새 NPC를 만들 때마다 안 보이는 문제" -- 그림이 따로 없는 종류는 기본 NPC 그림으로
          if (np && tiles.npc[nk] && nk !== 'lighthouse') ctx.drawImage(tiles.npc[nk], sx, sy - 3 * scale, TS, TS); // [2026-11-18] 등대는 seaDrawBack이 크게
        }
      }
    }
    if (map.abyss) { try { abyssDraw(now, TS, camX, camY); } catch (e) { } } // [2026-11-17] 마계 입구 포털
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
    // [2026-11-15] 거룡 패턴 장판 -- 예고(칸이 안에서부터 차오름) -> 터짐(밝게 번쩍). zones = [x, y, 터지는 시각, 반경, 종류(0 화염 / 1 심연의 비 / 2 꼬리 휩쓸기 / 3 멸망의 숨결), 예고 길이]
    var wzAll = (wbInfo && wbInfo.zones && map.id === (wbInfo.map || 'castle') ? wbInfo.zones : []).concat(mapZones || []); // [2026-11-16] 탑 수문장 / 악마의 장판(위치 알림 zones)도 같은 그림
    if (wzAll.length) {
      var wtn = nowSrv(), WZC = [[255, 70, 20], [170, 80, 255], [255, 230, 120], [255, 30, 60], [90, 220, 90], [200, 160, 110], [255, 230, 120], [120, 230, 80], [255, 90, 20], [255, 240, 170], [200, 225, 255], [150, 40, 90], [150, 210, 255]]; // [2026-11-18] 11 촉수 내려찍기 / 12 물보라
      for (var wzi = 0; wzi < wzAll.length; wzi++) {
        var wz = wzAll[wzi], wzr = wz[3] == null ? 2 : wz[3], wzk = wz[4] | 0, wzw = wz[5] || 3000, wc = WZC[wzk] || WZC[0];
        var wzx = (wz[0] - wzr) * TS - camX, wzy = (wz[1] - wzr) * TS - camY, wzs = (wzr * 2 + 1) * TS, wleft = wz[2] - wtn;
        if (wzx > W || wzy > H || wzx + wzs < 0 || wzy + wzs < 0) continue;
        if (wleft > 0) {
          var wp = Math.max(0, Math.min(1, 1 - wleft / wzw)), wg = wzs * wp, big3 = wzk === 3 || wzk === 7;
          ctx.fillStyle = 'rgba(' + wc[0] + ',' + wc[1] + ',' + wc[2] + ',' + (big3 ? 0.08 + 0.14 * wp : 0.14 + 0.2 * wp) + ')'; ctx.fillRect(wzx, wzy, wzs, wzs);
          ctx.fillStyle = 'rgba(' + wc[0] + ',' + Math.min(255, wc[1] + 60) + ',' + wc[2] + ',' + (big3 ? 0.16 : 0.36) + ')'; ctx.fillRect(wzx + (wzs - wg) / 2, wzy + (wzs - wg) / 2, wg, wg);
          ctx.strokeStyle = 'rgba(255,220,120,' + (0.55 + 0.4 * Math.sin(now / (big3 ? 50 : 80))) + ')'; ctx.lineWidth = (big3 ? 4 : 2) * dpr; ctx.strokeRect(wzx + dpr, wzy + dpr, wzs - 2 * dpr, wzs - 2 * dpr);
          if (big3) drawTextImg(textImg((wzk === 7 ? '☠️ 맹독 숨결 ' : '💥 멸망의 숨결 ') + Math.ceil(wleft / 1000) + ' -- 테두리 밖으로!', 'bold ' + Math.round(13 * dpr) + 'px sans-serif', '#ffd0d0', 3 * dpr, true), wzx + wzs / 2, wzy + wzs / 2 - TS * 2.6);
        } else if (wleft > -600) { ctx.fillStyle = 'rgba(255,240,170,' + ((wzk === 3 || wzk === 7 ? 0.6 : 0.85) * (1 + wleft / 600)) + ')'; ctx.fillRect(wzx, wzy, wzs, wzs); }
        if (wzk === 9 || wzk === 10) zoneHolyDraw(wzk, wzx, wzy, wzs, wleft, wzw, TS, now); // [2026-11-17] 보로네오 -- 빛기둥 / 천사
      }
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
    try { seaDrawBack(now, camX, camY); } catch (e) { } // [2026-11-18] 화약통
    try { twRoomDraw(now, TS, camX, camY); } catch (e) { } // [2026-11-16] 탑 방 -- 가시 띠 / 상자 / 제단 / 샘
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
        var tyC = map.mobTypes[mb.k] || {}, mgC = tyC.boss || tyC.clone ? ((bossT && tyC.clone ? bossT.foot : tyC.foot) || 2) + 2 : tyC.chaos ? 4 : 2; // [2026-11-19] "보스가 화면에 일부 가려지면 통째로 안 보인다" -- 왼쪽 위 칸만 보고 잘랐다. 그림 크기만큼 여유
        if (mx < -mgC * TS || my < -mgC * TS || mx > W + TS || my > H + mgC * TS) continue;
        var ty = map.mobTypes[mb.k] || {}, sk = mb.k, bid = mb.id, pose = ty.name === '심판자 보로네오' && Math.floor(nowSrv() / 12000) % 2 === 1; // [2026-11-17] 보로네오 -- 망치를 든 차례
        if (ty.clone && bossT && bossT.boss) { ty = bossT; sk = bossM.k; bid = bossM.id; } else if (splitOn && mb === bossM) pose = true;
        if (ty.name === '심해의 크라켄' && !krkShown()) { if (!krkAdminSee()) continue; ctx.globalAlpha = 0.45; } // [2026-11-19] 그냥 지나갈 땐 안 보인다(관리자는 반투명으로)
        if (ty.dive && seaDiving(ty, mb.id, nowSrv())) { seaRipple(mx, my, ty, now); continue; } // [2026-11-18] 물속(물결만)
        if (ty.name === '심해의 크라켄') pose = !!(mobSt[mb.id] && mobSt[mb.id].su > nowSrv()); // [2026-11-18] 바위에 박혀 기절
        var seaA = ty.dive ? seaAlpha(ty, mb.id, nowSrv()) : 1;
        var hq = mobHitAt[mb.id] ? now - mobHitAt[mb.id] : 999;
        if (hq < 200) mx += Math.round(Math.sin(hq / 16) * 2.5 * scale * (1 - hq / 200)); // [2026-11-03] 맞은 몬스터 흔들림
        var bob = ty.kind === 'flyer' ? Math.sin(now / 260 + bid) * 2 * scale : Math.abs(Math.sin(now / 420 + bid)) * scale;
        var ms = mobSt[mb.id];
        if (ms && ms.fu > nowSrv() && !ty.boss) { mx += ((mb.id % 5) - 2) * scale; my += (((mb.id / 5) | 0) % 3 - 1) * scale; } // [2026-11-05] 쫓아오며 겹친 몬스터 -- 조금씩 비껴 그려 여러 마리인 게 보이게
        if (ms && ms.sl) ctx.globalAlpha = 0.3 + 0.15 * Math.sin(now / 90); // [2026-10-28] 은신한 닌자 -- 반투명하게 깜빡
        if (seaA < 1) ctx.globalAlpha = seaA; // [2026-11-18] 잠수 앞뒤
        if (!ty.sea) { ctx.fillStyle = 'rgba(0,0,0,.25)'; ctx.fillRect(mx + 3 * scale, my + 13 * scale, 10 * scale, 2 * scale); }
        if (ty.sim != null) { var sw2 = TS * 2; ctx.drawImage(mobSprite(ty.sim, false, Math.floor(now / 480 + bid)), Math.round(mx + TS / 2 - sw2 / 2), Math.round(my + TS - sw2 - bob), sw2, sw2); } // [2026-11-07] 보스 허수아비 -- 그 보스 그림(2칸 크기)
        else if (ty.boss) { // [2026-10-24] 보스 -- 1.8배 크기, 붉은 기운
          var bf = ty.foot || 2; // [2026-11-15] 거룡은 3x3칸
          var fo = 0; // [2026-11-16] 거룡 등장 -- 하늘에서 날아오는 중이면 1 -> 0
          if (bf === 3 && wbInfo && wbInfo.on) {
            var fa = nowSrv() - wbInfo.at;
            if (fa >= 0 && fa < WB_FLY_MS) { fo = 1 - fa / WB_FLY_MS; fo = fo * fo; }
            else if (wbLandAt !== wbInfo.at) { wbLandAt = wbInfo.at; if (fa >= WB_FLY_MS && fa < WB_FLY_MS + 1500) { flashT0 = performance.now(); msg('🐉 심연의 거룡이 내성에 내려앉았어요!', 2600); } }
          }
          var hopL = 0; // [2026-11-16] 자리를 옮기는 중(날아올랐다 내려앉는다) 0 -> 1 -> 0
          if (!fo && ms && ms.hop && ms.ox != null) { var hq3 = (nowSrv() - ms.mt) / (ms.md || 950); if (hq3 >= 0 && hq3 < 1) hopL = Math.sin(hq3 * Math.PI); }
          if (!fo && !hopL && !ty.sea) { ctx.fillStyle = 'rgba(255,40,40,' + (0.18 + 0.1 * Math.sin(now / 300)) + ')'; ctx.beginPath(); ctx.arc(mx + TS * bf / 2, my + TS * bf / 2, TS * 0.75 * bf, 0, Math.PI * 2); ctx.fill(); }
          else { var shq = fo || hopL * 0.55; ctx.fillStyle = 'rgba(0,0,0,' + (0.32 * (1 - shq)) + ')'; ctx.beginPath(); ctx.ellipse(mx + TS * bf / 2, my + TS * bf - TS * 0.2, TS * bf * 0.5 * (1 - 0.6 * shq), TS * 0.36 * (1 - 0.6 * shq), 0, 0, Math.PI * 2); ctx.fill(); } // 내려앉을 자리의 그림자가 점점 커진다
          var bw = TS * 1.2 * bf; // [2026-10-29] 보스 2x2칸 -- 그림은 칸들 가운데, 발은 맨 아래 칸
          ctx.drawImage(mobSprite(sk, pose, Math.floor(now / (fo || hopL ? 150 : 480) + bid)), Math.round(mx + TS * bf / 2 - bw / 2 + fo * TS * 8), Math.round(my + bf * TS - bw - bob - fo * TS * 14 - hopL * TS * 2.6), bw, bw);
        } else if (ty.sea) { var sw3 = ty.kind === 'whale' ? TS * 2 : TS; ctx.drawImage(mobSprite(sk, false, Math.floor(now / 420 + bid)), Math.round(mx + TS / 2 - sw3 / 2), Math.round(my + TS - sw3 - bob * 0.5), sw3, sw3); if (ty.kind === 'whale') my -= Math.round(TS * 0.7); } // [2026-11-18] 바다 몬스터
        else if (BOSS_ART[ty.name]) { var qw = TS * 1.5; ctx.drawImage(mobSprite(sk, false, Math.floor(now / 480 + bid)), Math.round(mx + TS / 2 - qw / 2), Math.round(my + TS - qw - bob), qw, qw); } // 여왕개미(보스급)
        else if (ty.chaos) { // [2026-11-17] 악마화된 몬스터 -- 원래 그림을 크게(보스였으면 2.2배, 아니면 1.8배), 발밑 검붉은 기운 + 보랏빛 일렁임
          var chw = Math.round(TS * (ty.cboss ? (ty.src === '심연의 거룡' ? 3.6 : 3.0) : 2.0)), chp = 0.5 + 0.5 * Math.sin(now / 240 + mb.id);
          ctx.fillStyle = 'rgba(120,0,40,' + (0.28 + 0.12 * chp) + ')'; ctx.beginPath(); ctx.ellipse(mx + TS / 2, my + TS * 0.85, chw * 0.48, TS * 0.32, 0, 0, Math.PI * 2); ctx.fill();
          ctx.drawImage(mobSprite(sk, false, Math.floor(now / 480 + mb.id)), Math.round(mx + TS / 2 - chw / 2), Math.round(my + TS - chw - bob), chw, chw);
          for (var ci = 0; ci < 5; ci++) fxDot(mx + TS / 2 + Math.cos(now / 300 + ci * 1.3) * chw * 0.42, my + TS - chw * (0.2 + 0.15 * ci) - bob, 1.6 * scale, ci % 2 ? '#c82aff' : '#ff2a5a', 0.4 + 0.4 * chp);
          my -= Math.round(chw - TS) + 2 * scale;
        }
        else if (ty.demon) { var dmw = Math.round(TS * 1.5); ctx.drawImage(mobSprite(sk), Math.round(mx + TS / 2 - dmw / 2), Math.round(my + TS - dmw - bob), dmw, dmw); my -= 7 * scale; } // [2026-11-16] 악마 -- 1.5배
        else {
          ctx.drawImage(mobSprite(sk), Math.round(mx), Math.round(my - 2 * scale - bob), TS, TS);
          if (wildNight && EYE_PX[ty.kind]) EYE_PX[ty.kind].forEach(function (e) { redEyes.push([mx + (e[0] + 0.5) * scale, my - 2 * scale - bob + (e[1] + 0.5) * scale]); });
        }
        ctx.globalAlpha = 1;
        if (ms && ms.vu > nowSrv() && ms.vp > 0) drawTextImg(textImg('🔻+' + ms.vp + '%', 'bold ' + Math.round(10 * dpr) + 'px monospace', '#ff8a6a', 3 * dpr, true), mx + (ty.boss ? TS * (ty.foot || 2) / 2 : TS / 2), my - (ty.boss ? TS * 1.3 : 8 * scale)); // [2026-11-08] 만검진 받는 피해 증가
        if (ms && ms.su > nowSrv()) { try { drawZapStun(ty.boss ? mx + TS * ((ty.foot || 2) - 1) / 2 : mx, (ty.boss ? my + TS : my) - 3 * scale, now); } catch (e) { } } // [2026-11-07] 일섬참 기절(5초) -- 감전과 같은 불꽃
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
            var bhx = mx + TS * (ty.foot || 2) / 2; // [2026-11-15] 거룡 3x3 -- 가운데 맞춤
            ctx.fillStyle = '#140d0a'; ctx.fillRect(bhx - 15 * scale, my - 9 * scale, 30 * scale, 4 * scale);
            ctx.fillStyle = '#e8433a'; ctx.fillRect(bhx - 14 * scale, my - 8 * scale, Math.max(1, 28 * scale * ms.hp / hpMx), 2 * scale);
          } else {
          ctx.fillStyle = '#140d0a'; ctx.fillRect(mx + 1 * scale, my - 4 * scale, 14 * scale, 3 * scale);
          ctx.fillStyle = '#e8433a'; ctx.fillRect(mx + 2 * scale, my - 3 * scale, Math.max(1, 12 * scale * ms.hp / hpMx), scale);
          }
        }
        if (Math.abs(mp.cx - P.x) + Math.abs(mp.cy - P.y) <= 4 && !tagCells[mp.cx + ',' + mp.cy]) { // [2026-11-06] 렉 -- 겹친 칸은 이름 하나만, 이름표는 그림으로 저장해 다시 쓴다
          tagCells[mp.cx + ',' + mp.cy] = 1;
          var nm = ty.dummy ? '🎯 ' + (ELEM_ICON[ty.el] || '') + ty.name + (ty.sim != null && ty.lv ? ' Lv.' + ty.lv : '') : (ty.champ ? '🏅 챔피언 ' : ty.boss ? '👑 보스 ' : ty.demon ? '😈 ' : '') + (mb.r === 2 ? '⭐' : '') + (ELEM_ICON[ty.el] || '') + ty.name + ' Lv.' + Math.max(1, (ty.lv || (1 + 4 * (ty.tier - 1))) + RANK_LV[mb.r == null ? 1 : mb.r]); // [2026-10-28] 얼음 여왕은 서버가 준 레벨(95), [2026-10-29] 속성 아이콘
          drawTextImg(textImg(nm, 'bold ' + Math.round(11 * dpr) + 'px sans-serif', ty.boss ? '#ff4a4a' : ty.sea && ty.first ? '#ff5a5a' : mb.r === 2 ? '#ffb347' : mb.r === 0 ? '#c8c8c8' : ty.tier >= 7 ? '#ff8a8a' : ty.tier >= 4 ? '#ffd27a' : '#ffffff', 3 * dpr, true), mx + (ty.boss ? TS * (ty.foot || 2) / 2 : TS / 2), my - (ty.boss ? 11 : 6) * scale); // [2026-10-28] 보스 이름이 두 줄로 보이던 문제 -- 테두리도 글자와 같은 높이
        }
      }
    }
    try { seaDrawFront(now, camX, camY); } catch (e) { } // [2026-11-18] 부표 불꽃 / 지원 함선
    try { krkSwingDraw(now, camX, camY); } catch (e) { } // [2026-11-19] 크라켄 다리 휘두르기
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
      if (ou.m2 && !map.boat) mercDrawOther(ou, op, now, { TS: TS, camX: camX, camY: camY, scale: scale, W: W, H: H }, true); // [2026-11-18] 두 번째 용병
      ctx.fillStyle = "rgba(0,0,0,.22)"; ctx.fillRect(ox + 3 * scale, oy + 13 * scale, 10 * scale, 3 * scale);
      if (ou.look && ou.look.gm) { try { gmAura(ox, oy - 3 * scale, now); } catch (e) { } } // 치장 효과가 화면 그리기를 멈추지 않게
      var oBoat = boatAt(op.x, op.y); // [2026-11-18] 물 위에서만(등대 섬/바닷가 모래에선 내린다)
      if (oBoat) drawBoat(ox, oy - 3 * scale, now, ou.dir === 'left'); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
      var oCrew = []; // [2026-11-19] 배 맵 -- 그 사람 용병도 그 배 고물에
      if (oBoat && map.boat) { if (ou.mc != null) oCrew.push([ou, ou.mp || (ou.mp = { x: null, y: null, dir: 'down' })]); if (ou.m2) oCrew.push([ou.m2, ou.mp2 || (ou.mp2 = { x: null, y: null, dir: 'down' })]); }
      if (oCrew.length) { boatStern(ox, oy - 3 * scale, now, ou.dir === 'left', oCrew.length, false); boatCrewDraw(oCrew, op.x, op.y, ou.dir === 'left', now, { TS: TS, camX: camX, camY: camY, scale: scale, W: W, H: H }, true); }
      if (ou.look && ou.look.rebirth) { try { drawWings(ox, oy - 3 * scale, ou.look.rebirth, ou.dir || "down", now, +oid || 1, ou.look.wing); } catch (e) { } } // [2026-10-28] 환생 날개
      if (ou.stealth) ctx.globalAlpha = 0.35; // [2026-10-30] 자객의 목걸이 은신
      var osw = ou.swT0 && now >= ou.swT0 && now - ou.swT0 < 380 ? ((now - ou.swT0) < 190 ? 3 : 4) : 0; // [2026-11-07] 다른 사람 공격 -- 무기 휘두르기
      if (auraFx.length) { var oA = ctx.globalAlpha; ctx.globalAlpha = 1; whirlDraw(0, ox, oy - 3 * scale, now, oid); ctx.globalAlpha = oA; } // [2026-11-07] 관리자 돌개바람(뒤)
      if (unity) unityRing(ox, oy, now); // [2026-11-17] 함께 싸우는 사람도
      ctx.drawImage(otherSprite(ou.look, osw ? ou.swDir : ou.dir || "down", osw ? 0 : op.moving ? 1 + Math.floor(now / 160) % 2 : 0, osw), ox, oy - 3 * scale, TS, TS);
      ctx.globalAlpha = 1;
      if (oBoat) { boatStern(ox, oy - 3 * scale, now, ou.dir === 'left', oCrew.length, true); drawBoatFront(ox, oy - 3 * scale, now, ou.dir === 'left'); } // [2026-11-18]
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
    if (armorerOn()) { // [2026-11-20] 떠돌이 갑옷장이
      var axp = Math.round(armorer.x * TS - camX), ayp = Math.round(armorer.y * TS - camY);
      if (!tiles.npc.armorer) tiles.npc.armorer = armorerSprite();
      ctx.drawImage(tiles.npc.armorer, axp, ayp - 3 * scale, TS, TS);
      if (Math.abs(armorer.x - P.x) + Math.abs(armorer.y - P.y) <= 8) {
        ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center';
        ctx.lineWidth = 4 * dpr; ctx.strokeStyle = '#000'; ctx.strokeText('🛡️ 떠돌이 갑옷장이', axp + TS / 2, ayp - 6 * dpr);
        ctx.fillStyle = '#ffb07a'; ctx.fillText('🛡️ 떠돌이 갑옷장이', axp + TS / 2, ayp - 6 * dpr);
      }
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
    if (map.board) { // [2026-11-15] 탑 보드 -- 칸마다 종류 아이콘
      ctx.save(); ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.font = Math.round(TS * 0.45) + 'px sans-serif';
      map.board.forEach(function (b) {
        var bx = Math.round(b[0] * TS - camX), by2 = Math.round(b[1] * TS - camY);
        if (bx < -TS || by2 < -TS || bx > W + TS || by2 > H + TS) return;
        ctx.globalAlpha = 0.28; ctx.fillStyle = b[2] === 85 ? '#7cf08a' : b[2] === 67 ? '#ff6a6a' : b[2] === 88 ? '#ffb03a' : '#ffe9a8';
        ctx.fillRect(bx + 2, by2 + 2, TS - 4, TS - 4);
        ctx.globalAlpha = 0.85; ctx.fillText(TW_ICON[String.fromCharCode(b[2])] || '', bx + TS / 2, by2 + TS / 2);
      });
      ctx.restore();
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
    boatLandCheck(pos); // [2026-11-18] 배에서 내림/오름
    var meBoat = boatAt(pos.x, pos.y);
    if (meBoat) drawBoat(cx, cy - 3 * scale, now, P.dir === 'left'); // [2026-10-28] 다도해 뱃길 -- 배 탄 모습
    var myCrew = meBoat && map.boat ? boatCrewMine() : []; // [2026-11-19] 배 맵 -- 용병이 내 배 고물에
    if (myCrew.length) { boatStern(cx, cy - 3 * scale, now, P.dir === 'left', myCrew.length, false); boatCrewDraw(myCrew, pos.x, pos.y, P.dir === 'left', now, { TS: TS, camX: camX, camY: camY, scale: scale, W: W, H: H }, false); }
    var myRb = st && st.combat ? st.combat.rebirth | 0 : 0; // [2026-10-28] 환생 날개(내 것은 전투 정보의 환생 횟수로)
    var hid = pStealthOn(); // [2026-10-30] 자객의 목걸이 은신 -- 반투명하게(살짝 일렁임)
    if (hid) ctx.globalAlpha = 0.42 + 0.08 * Math.sin(now / 160);
    if (myRb) { try { drawWings(cx, cy - 3 * scale, myRb, P.dir, now, 0, st && st.look && st.look.wing); } catch (e) { } }
    if (hid) ctx.globalAlpha = 0.42 + 0.08 * Math.sin(now / 160);
    if (auraFx.length) { var hidA = ctx.globalAlpha; ctx.globalAlpha = 1; whirlDraw(0, cx, cy - 3 * scale, now); ctx.globalAlpha = hidA; } // [2026-11-07] 관리자 돌개바람(뒤쪽 반)
    if (unity) unityRing(cx, cy, now); // [2026-11-17] 우리는 하나 -- 발밑 금빛 고리
    ctx.drawImage(sprites[P.dir + (P.moving ? P.step : 0) + dk], cx, cy - 3 * scale, TS, TS);
    ctx.globalAlpha = 1;
    if (meBoat) { boatStern(cx, cy - 3 * scale, now, P.dir === 'left', myCrew.length, true); drawBoatFront(cx, cy - 3 * scale, now, P.dir === 'left'); drawBoatHp(cx, cy - 3 * scale); try { drawFishing(cx, cy - 3 * scale, now); } catch (e) { } } else if (fishFx) { try { drawFishing(cx, cy - 3 * scale, now); } catch (e) { } } // [2026-11-19] 물가 낚시
    if (berserkInvUntil) { try { berserkShieldDraw(cx, cy - 3 * scale, now); } catch (e) { } } // [2026-11-20] 광전사 불굴 보호막 // [2026-11-18] 뱃전이 다리를 가린다 + 배 체력
    if (auraFx.length) whirlDraw(1, cx, cy - 3 * scale, now); // [2026-11-07] 관리자 돌개바람(앞쪽 반)
    if (meGm) { try { gmSparkle(cx, cy - 3 * scale, now, 0); } catch (e) { } }
    if (frozenMe()) drawIce(cx, cy - 3 * scale, now); // [2026-10-28] 얼음 여왕 빙결
    if (stunUntil > nowSrv()) { // [2026-11-04] 붕대 스턴, [2026-11-05] 감전, [2026-11-15] 그 밖의 기절(머리 위 별)
      if (zapStunUntil >= stunUntil) drawZapStun(cx, cy - 3 * scale, now);
      else if (stunBand) drawBandage(cx, cy - 3 * scale, now);
      else { ctx.font = (11 * dpr) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.fillText('💫', cx + TS / 2 + Math.cos(now / 160) * TS * 0.25, cy - 4 * scale + Math.sin(now / 160) * TS * 0.08); }
    }
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
    // [2026-11-18] 워프 포인트(3x3) -- 네 귀퉁이 푸른 불꽃, 룬 빛(사람 위에 그려 뒤에 선 사람은 가려진다)
    (map.portals || []).forEach(function (p) {
      if (p.kind !== 'X' || cellAt(p.x - 1, p.y) === 'X' || cellAt(p.x, p.y - 1) === 'X') return;
      var wcx = (p.x + 1.5) * TS - camX, wcy = (p.y + 1.5) * TS - camY;
      if (wcx < -TS * 3 || wcy < -TS * 3 || wcx > W + TS * 3 || wcy > H + TS * 3) return;
      try { wpDraw(ctx, wcx, wcy, TS, now); } catch (e) { }
    });
    // 가까운 포탈 이름
    (map.portals || []).forEach(function (p) {
      if (p.kind === 'X' && (cellAt(p.x - 1, p.y) === 'X' || cellAt(p.x, p.y - 1) === 'X')) return; // [2026-11-18] 3x3 워프 포인트는 이름 하나(왼위 칸 것)
      var zSeen = p.kind === 'Z' && st && st.dragon && st.dragon.has; // [2026-10-28] 용왕의 반지를 얻으면 바닷길 입구 이름도
      if ((p.kind === 'Z' && !zSeen) || Math.abs(p.x - P.x) + Math.abs(p.y - P.y) > 4) return; // [2026-10-24] 숨은 입구(Z)는 이름을 안 보인다
      var tx = p.x * TS - camX + TS / 2, ty = p.y * TS - camY - 4 * dpr;
      if (p.kind === 'X' && cellAt(p.x + 1, p.y) === 'X') { tx += TS; ty -= TS * 0.8; } // [2026-11-18] 워프 포인트 가운데 위
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
    if (wbLeave && map && map.id === 'castle') { // [2026-11-16] 거룡 퇴장 -- 날개를 치며 왼쪽 위 하늘로 멀어진다
      var wlp = (now - wbLeave.t0) / WB_FLY_MS;
      if (wlp >= 1) wbLeave = null;
      else {
        var wlm = null;
        for (var wli = 0; wli < map.mobs.length; wli++) if ((map.mobTypes[map.mobs[wli].k] || {}).foot === 3) wlm = map.mobs[wli];
        if (wlm) {
          var wlo = wlp * wlp, wlx = wlm.x * TS - camX, wly = wlm.y * TS - camY, wlw = TS * 3.6;
          ctx.fillStyle = 'rgba(0,0,0,' + (0.32 * (1 - wlo)) + ')'; ctx.beginPath(); ctx.ellipse(wlx + TS * 1.5, wly + TS * 2.8, TS * 1.5 * (1 - 0.6 * wlo), TS * 0.36 * (1 - 0.6 * wlo), 0, 0, Math.PI * 2); ctx.fill();
          ctx.globalAlpha = 1 - 0.55 * wlo;
          ctx.drawImage(mobSprite(wlm.k, false, Math.floor(now / 150)), Math.round(wlx + TS * 1.5 - wlw / 2 - wlo * TS * 8), Math.round(wly + 3 * TS - wlw - wlo * TS * 14), wlw, wlw);
          ctx.globalAlpha = 1;
        }
      }
    }
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
    var auraWide = !!(st && (st.adminMaps || (st.aura && st.aura.wide))); // [2026-11-20] 태초의 보석을 쓴 사람도 3칸 폭
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
    try { lootDraw(now, TS, camX, camY); } catch (e) { } // [2026-11-16] 전리품(쏟아졌다가 빨려 들어간다)
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
    if ((map.boat || (map.sail && boatAt(P.x, P.y))) && st && st.archi && st.archi.boatHpMax) { // [2026-11-18] "배 체력 항상 잘 보이게" -- 화면 위 가운데 큰 막대(30% 아래면 빨갛게 깜빡)
      var bhp = Math.max(0, st.archi.boatHp || 0), bmx2 = st.archi.boatHpMax, bf = Math.max(0, Math.min(1, bhp / bmx2)), bw2 = Math.min(W * 0.6, 340 * dpr), bh2 = 16 * dpr, bx2 = (W - bw2) / 2, by2 = 52 * dpr;
      // [2026-11-18] "배 체력을 플레이어 체력바 위로" -- 화면 아래 체력바(#mineHp)와 같은 폭으로 바로 위에(체력바가 숨으면 예전처럼 위 가운데)
      var hr = el.hp && el.hp.offsetWidth ? el.hp.getBoundingClientRect() : null, cr = hr ? el.cv.getBoundingClientRect() : null;
      if (hr && cr && cr.width) { var ks = W / cr.width; bh2 = 14 * dpr; bw2 = Math.max(120 * dpr, hr.width * ks); bx2 = (hr.left - cr.left) * ks; by2 = (hr.top - cr.top) * ks - bh2 - 7 * dpr; }
      var low = bf < 0.3, blink = low && Math.floor(now / 300) % 2 === 0;
      ctx.fillStyle = 'rgba(8,16,30,.78)'; ctx.fillRect(bx2 - 3 * dpr, by2 - 3 * dpr, bw2 + 6 * dpr, bh2 + 6 * dpr);
      ctx.fillStyle = low ? (blink ? '#ff8a6a' : '#e8432a') : '#4fc3f7'; ctx.fillRect(bx2, by2, bw2 * bf, bh2);
      ctx.fillStyle = 'rgba(255,255,255,.25)'; ctx.fillRect(bx2, by2, bw2 * bf, bh2 / 3);
      ctx.font = 'bold ' + Math.round(12 * dpr) + 'px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.lineWidth = 3 * dpr; ctx.strokeStyle = '#000';
      var btx = bw2 < 260 * dpr ? '⛵ 배 ' + Math.round(bf * 100) + '%' + (map.id === 'vastsea' ? ' · 초당 -5%' : '') // [2026-11-18] 좁으면(폰) 줄여서
        : '⛵ 배 체력 ' + bhp.toLocaleString() + ' / ' + bmx2.toLocaleString() + ' (' + Math.round(bf * 100) + '%)' + (map.id === 'vastsea' ? ' · 망망대해: 초당 -5%' : '');
      ctx.font = 'bold ' + Math.round((bw2 < 260 * dpr ? 10 : 12) * dpr) + 'px sans-serif';
      ctx.strokeText(btx, bx2 + bw2 / 2, by2 + bh2 / 2); ctx.fillStyle = '#ffffff'; ctx.fillText(btx, bx2 + bw2 / 2, by2 + bh2 / 2); ctx.textBaseline = 'alphabetic'; ctx.textAlign = 'left';
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
    for (var g = 1; g < ORE_NAME.length; g++) { try { if (ORE_NAME[g]) oreIcon(g); } catch (e) { } } // [2026-11-20] 65까지만 -> 모든 아이템(나무/태초의 보석 등)
    var ids = Object.keys(vers || {}).filter(function (id) { return !(mapMem[id] && mapMem[id].version === vers[id]); });
    if (!ids.length) return preloadExtra();
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
    return Promise.all(ws6).then(preloadExtra); // [2026-11-20] 그림들까지
  }
  /** 서버 상태(맵/위치/캔 칸)로 화면을 다시 맞춘다 -- 처음 들어올 때, 포탈을 탄 뒤, 다른 곳에서 위치가 바뀌었을 때. */
  function enterState(s) {
    st = s;
    STEP_MS = s.stepMs || 140; // [2026-10-24] 신속의 장화
    if (s.mercPay) setTimeout(function () { mercPayNote(s.mercPay); }, 1500); // [2026-11-06] 접속하지 않은 동안의 용병 급여
    if (s.weaponOffMsg) setTimeout(function () { msg(s.weaponOffMsg, 3600); }, 900); // [2026-10-29] 무기 장착 레벨이 모자라 칼로 바꿔 듦
    if (s.wsHost && s.wsHost !== wsHostMem) { wsHostMem = s.wsHost; try { localStorage.setItem('tw-mine-wshost', s.wsHost); } catch (e) { } } // [2026-11-06] 다음 입장 때 소켓을 먼저
    mapZones = []; twDoorWas = false; twFx = null; spikePend = 0; spikeTotal = 0; spikeHitAt = {}; loot = []; lootPops = []; buildSprites(s.look); setCombat(s.combat); mobSt = {}; mobDeathReady = false; mobDeaths = []; noOre = {}; lavaCells = {}; scan = null; scanKey = ""; bossWarned = false; cold = null; lastBz = 0; raid = null; heatWarned = false; iceSt = null; /* [2026-10-26] 한기, [2026-10-28] 대주교, 더위, 얼음 여왕 */
    if (s.mailN > 0 && !mailNoticeShown) { mailNoticeShown = true; setTimeout(function () { msg('📮 받지 않은 우편이 ' + s.mailN + '통 있어요! 마을 우체부에게 가 보세요.', 4200); }, 1800); } // [2026-11-17] 우편함
    if (s.bossRwHere) setTimeout(function () { var h = s.bossRwHere; msg('👑 ' + h.name + ' -- 오늘 보상 ' + h.left + '/' + h.max + '번 남았어요' + (h.left ? '' : ' (내일 0시에 다시)'), 3600); }, 1400); // [2026-11-20] 보스룸 -- 보스 보상 하루 5번
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
  // [2026-10-28] 설산 동굴 -- 땅은 못 파고, 바라보는(없으면 상하좌우) 얼음 광맥(I)을 연속 채광. [2026-11-19] 5번이면(예전 20) 빙석, 몬스터에게 맞으면 처음부터.
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
    msg('💥 박쥐에게 맞아 연속 채광이 끊겼어요! 처음부터 다시 캐야 해요.', 2400); // [2026-11-19] 횟수는 서버(MINE_VEIN_NEED, 지금 5번)
  }
  // [2026-11-11] 하피의 둥지 -- "곡괭이로 채광할 수도, 10회 정도면 쓰러져 밑동만" -- 바라보는 칸(없으면 둘레 8칸)의 나무를 찍는다
  var chopBusy = false;
  function chopTree() {
    if (!opened || !map || chopBusy || P.moving) return;
    var dv = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }[P.dir] || [0, 1];
    var tx = P.x + dv[0], ty = P.y + dv[1];
    // [2026-11-17] 밑동(E)뿐 아니라 수관(나무 위 두 줄)도 나무 -- 바라보는 칸, 서 있는 칸(수관 밑), 둘레 8칸 순으로
    var inTree = function (x, y) { return (map.trees || []).some(function (tp, i) { var q = treeSt[i] || [i, 0]; return (q[1] | 0) === 0 && x >= tp[0] && x <= tp[0] + 2 && y >= tp[1] - 2 && y <= tp[1] + 1; }); };
    if (!inTree(tx, ty)) { tx = -1; if (inTree(P.x, P.y)) { tx = P.x; ty = P.y; } for (var oy = -1; oy <= 1 && tx < 0; oy++) for (var ox = -1; ox <= 1; ox++) if (inTree(P.x + ox, P.y + oy)) { tx = P.x + ox; ty = P.y + oy; break; } }
    if (tx < 0) { msg('🪓 나무 바로 옆에서 캐면 곡괭이로 나무를 찍을 수 있어요.', 2200); return; }
    chopBusy = true; P.digT0 = performance.now(); P.swing = false;
    apiFast('/api/tower-mine-chop?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&tx=' + tx + '&ty=' + ty + verQ()).then(function (d) {
      chopBusy = false;
      if (!d || d.tooFast) return;
      if (d.reload) { reload(); return; }
      if (!d.ok) { if (d.message) msg(d.message, 2200); return; }
      floats.push({ x: tx, y: ty - 0.3, text: '🪓 ' + d.chop + '/' + d.need, color: '#ffe0a0', t0: performance.now() });
      if (d.wood) floats.push({ x: P.x, y: P.y - 0.9, text: '+' + d.wood + ' 🪵 나무', color: '#c8945a', t0: performance.now() + 200 }); // [2026-11-19] 배 수리 재료
      if (d.felled) { flashT0 = performance.now(); msg(d.message, 2600); treeFallAt[d.tree] = performance.now(); treeSt.forEach(function (q) { if (q[0] === d.tree) q[1] = 3; }); }
    }).catch(function () { chopBusy = false; });
  }
  // [2026-11-18] 바다 낚시 -- 배를 탄 맵에서 채광 버튼 = 낚시(시즌4 낚싯대/찌). 던지기 -> 찌 -> 입질(결과) -> 물고기. [2026-11-20] 곡괭이 대신 낚시 내구도(하루 100, 한 번에 1)
  var fishFx = null, FISH_COL = ['#ffffff', '#ffffff', '#8ee08e', '#6ab8ff', '#c08aff', '#ffb347', '#ff6a8a', '#ff5a3a', '#ffd84a'];
  function fishCast() {
    if (!opened || busy || !map || P.moving) return;
    busy = true; el.dig.disabled = true;
    var ff = fishFx = { t0: performance.now(), dir: P.dir };
    apiFast('/api/tower-mine-dig?mapId=' + encodeURIComponent(map.id) + '&x=' + P.x + '&y=' + P.y + '&dir=' + P.dir + verQ()).then(function (d) {
      if (d.durability != null) { st.durability = d.durability; st.durabilityMax = d.durabilityMax; }
      if (d.fishDur != null) { st.fishDur = d.fishDur; st.fishDurMax = d.fishDurMax; } // [2026-11-20] 낚시 내구도
      if (!d.ok) { busy = false; if (fishFx === ff) fishFx = null; showStamina(); if (d.message) msg(d.message, 2200); return; }
      setTimeout(function () {
        busy = false;
        ff.bite = performance.now();
        var f = d.fish || {}, col = FISH_COL[f.grade] || '#ffffff', big = f.grade >= 5;
        fishInvD = null; // [2026-11-19] 인벤토리 낚시 탭을 새로
        floats.push({ x: P.x, y: P.y - 1.0, text: (f.emoji || '🐟') + ' ' + (f.star || '') + ' ' + (f.name || ''), color: col, big: big, t0: performance.now() });
        try { killFeed('🎣 ' + (f.star || '') + ' ' + (f.name || ''), col); } catch (e) { }
        if (big) { flashT0 = performance.now(); banner = { text: '🎣 ' + f.star + ' ' + f.name + '!', t0: performance.now() }; }
        if (d.ach) msg(d.ach, 4200);
        smallExpFx(d, 0.4);
        showStamina();
      }, Math.max(0, 1300 - (performance.now() - ff.t0)));
    }).catch(function () { busy = false; if (fishFx === ff) fishFx = null; showStamina(); msg('요청 실패 -- 다시 시도해주세요.'); });
  }
  function drawFishing(cx, cy, now) { // (cx, cy) = 캐릭터 그림 왼쪽 위
    if (!fishFx) return;
    var pn = performance.now(), tt = pn - fishFx.t0;
    if (tt > 6000 || (fishFx.bite && pn - fishFx.bite > 1400)) { fishFx = null; return; }
    var TS = T * scale, u = TS / 16, dv = fishFx.dir === 'left' ? [-1, 0] : fishFx.dir === 'up' ? [0, -1] : fishFx.dir === 'down' ? [0, 1] : [1, 0];
    var hx = cx + TS / 2 + dv[0] * 3 * u, hy = cy + 9 * u, rx = cx + TS / 2 + dv[0] * 9 * u, ry = cy - 2 * u + dv[1] * 4 * u;
    var fly = Math.min(1, tt / 450), bx = cx + TS / 2 + dv[0] * TS * 2.2 * fly, by = cy + TS * 0.95 + dv[1] * TS * 2.2 * fly - Math.sin(fly * Math.PI) * TS * 0.8;
    var bit = fishFx.bite ? Math.min(1, (pn - fishFx.bite) / 180) : 0;
    if (fly >= 1) by += bit ? bit * 4 * u : Math.sin(now / 250) * u;
    ctx.strokeStyle = '#5a3a1e'; ctx.lineWidth = 2 * u; ctx.beginPath(); ctx.moveTo(hx, hy); ctx.lineTo(rx, ry); ctx.stroke(); // 낚싯대
    ctx.strokeStyle = 'rgba(240,240,240,.85)'; ctx.lineWidth = Math.max(1, 0.6 * u); ctx.beginPath(); ctx.moveTo(rx, ry); ctx.quadraticCurveTo((rx + bx) / 2, Math.max(ry, by) + (bit ? 0 : TS * 0.3), bx, by); ctx.stroke(); // 낚싯줄
    ctx.fillStyle = '#e8463a'; ctx.fillRect(bx - 1.5 * u, by - 2 * u, 3 * u, 2 * u); ctx.fillStyle = '#ffffff'; ctx.fillRect(bx - 1.5 * u, by, 3 * u, 1.5 * u); // 찌
    if (bit) { ctx.strokeStyle = 'rgba(230,245,255,' + (0.8 * (1 - (pn - fishFx.bite) / 1400)).toFixed(2) + ')'; ctx.lineWidth = 1.5 * dpr; ctx.beginPath(); ctx.ellipse(bx, by + u, TS * 0.2 + (pn - fishFx.bite) / 1400 * TS * 0.6, TS * 0.1 + (pn - fishFx.bite) / 1400 * TS * 0.25, 0, 0, Math.PI * 2); ctx.stroke(); }
  }
  /** [2026-11-20] 채광/낚시 경험치(소량) -- 머리 위 작은 글씨 + 레벨 업. */
  function smallExpFx(d, dy) {
    if (d.combat) setCombat(d.combat);
    if (d.smallExp > 0) floats.push({ x: P.x, y: P.y - 1.2 - (dy || 0), text: '+' + d.smallExp + ' 경험치', color: '#b8f0a0', t0: performance.now() + 250 });
    if (d.levelUp && d.combat) { flashT0 = performance.now(); banner = { text: '🎉 레벨 업! Lv.' + d.combat.lv, t0: performance.now() }; }
  }
  // [2026-11-19] "물 필드라면 전체 바다낚시" -- 서버 mineFishSpot/mineFishRegion과 같은 규칙: 배를 탄 물 위, 또는 낚시 지역 맵에서 바라보는 칸이 물(~)(북쪽 끝 설원은 아래쪽 바다만).
  var FISH_MAPS = { field: 1, desert: 1, beach: 1, island: 1, opensea: 1, vastsea: 1, seacity: 1, archi: 1, archisea: 1, isle1: 1, isle2: 1, isle3: 1, isle4: 1, isle5: 1, isle6: 1, isle7: 1, north: 1, towercity: 1, towergate: 1, harpyferry: 1 };
  function fishWater(x, y) { return !!(map && FISH_MAPS[map.id] && cellAt(x, y) === '~' && !(map.id === 'north' && y < map.h - 20)); }
  function fishHere() {
    if (!map) return false;
    if (map.boat || (map.sail && boatAt(P.x, P.y))) return true;
    var dv = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }[P.dir];
    return !!(dv && fishWater(P.x + dv[0], P.y + dv[1]));
  }
  function dig(auto) { // [2026-11-20] auto = 자동채광 -- "자동채광 시 채광만(갑자기 낚시를 한대)": 물을 바라봐도 낚시하지 않고, 서버에도 낚시 말라고(dir 끝 !)
    if (el.menu && el.menu.classList.contains('open')) setMenu(false); // [2026-11-05]
    if (map && !auto && fishHere()) { fishCast(); return; } // [2026-11-18] 바다에선 채광 대신 낚시, [2026-11-19] 모두에게, 같은 날 물가에서 물을 바라보면 어디서나
    if (map && map.trees) { chopTree(); return; } // [2026-11-11] 하피의 둥지
    if (map && map.noDig && FISH_MAPS[map.id]) { msg('🎣 물을 바라보고 누르면 낚싯대를 던져요.', 2000); return; } // [2026-11-20] 캘 광석이 없는 낚시 지역(나루터/요새 도시/탑 입구)
    if (map && map.vein) { veinDig(); return; } // [2026-10-28] 설산 동굴
    if (map && map.id === 'demonvillage') { msg('😈 마계 마을의 땅은 파낼 수 없어요. (정제된 마석은 들어올 때 1개)', 2200); return; } // [2026-11-20] 채광 금지
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
    apiFast('/api/tower-mine-dig?mapId=' + encodeURIComponent(map.id) + '&x=' + x + '&y=' + y + '&dir=' + P.dir + (auto ? '!' : '') + verQ()).then(function (d) { // [2026-10-25] dir = 쌍날 곡괭이 앞뒤
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
        smallExpFx(d, 0); // [2026-11-20] 채광 경험치
        // [2026-10-24] 광역 곡괭이 -- 주변 칸도 함께 캔 결과
        (d.wide || []).forEach(function (w, i) {
          dug[w.x + ',' + w.y] = w.grade;
          recent.unshift({ mapName: map.name, x: w.x, y: w.y, grade: w.grade, at: Date.now() });
          var wi = new Image(); wi.src = oreIcon(w.grade);
          floats.push({ x: w.x, y: w.y, text: '+' + (w.gain || 1) + ' ' + w.oreName, color: (ORE_COL[w.grade] || ORE_COL[1])[1], icon: wi, t0: performance.now() + 60 * (i + 1) });
          if (w.gemGrade) { // [2026-11-13] 함께 캔 칸에서 나온 보석도(예전엔 가운데 칸 것만 보였다)
            var wgi = new Image(), wgc = (ORE_COL[w.gemGrade] || ORE_COL[1])[1]; wgi.src = oreIcon(w.gemGrade);
            floats.push({ x: w.x, y: w.y - 0.6, text: '+1 ' + w.gemName, color: wgc, big: !!RARE_GEM[w.gemGrade], icon: wgi, t0: performance.now() + 250 + 60 * (i + 1) });
            killFeed('+1 ' + w.gemName, wgc, wgi.src);
            if (RARE_GEM[w.gemGrade]) { flashT0 = performance.now(); msg('💎 ' + w.gemName + '! ' + map.name, 3600); }
          }
          if (w.cutGrade) {
            var wci = new Image(); wci.src = oreIcon(w.cutGrade);
            floats.push({ x: w.x, y: w.y - 1.0, text: '+1 ' + w.cutName, color: '#fff4b0', big: true, icon: wci, t0: performance.now() + 350 });
            flashT0 = performance.now(); rainbowGo(w.x, w.y); killFeed('+1 ' + w.cutName, '#fff4b0', wci.src);
          }
        });
        if (wide5On()) lastDig = { m: map.id, x: x, y: y }; // [2026-11-13] 대광역 -- 자동채광은 5칸 떨어진 곳에서 다음
        if (d.ores) st.ores = d.ores;
        if (d.manaStone) { // [2026-11-18] 마계 마을 -- 캘 때마다 정제된 마석
          var mimg = new Image(); mimg.src = oreIcon(80);
          floats.push({ x: x, y: y - 1.0, text: '+' + (d.manaStone | 0 || 1) + ' 정제된 마석', color: '#ff9ae8', icon: mimg, t0: performance.now() + 350 });
        }
        if (d.demonOre) { // [2026-11-17] 악마의 땅 -- 캘 때마다 악마의 광석 조각
          var dimg = new Image(); dimg.src = oreIcon(75);
          floats.push({ x: x, y: y - 1.0, text: '+' + (d.demonOre | 0 || 1) + ' 악마의 광석 조각', color: '#ff8a7a', icon: dimg, t0: performance.now() + 350 });
        }
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
        if (d.seaGrade) { // [2026-11-17] 땅 보석과 바다 보석이 함께
          var simg = new Image(); simg.src = oreIcon(d.seaGrade);
          killFeed('+1 ' + d.seaName, (ORE_COL[d.seaGrade] || ORE_COL[1])[1], simg.src);
          floats.push({ x: x, y: y - 1.2, text: '+1 ' + d.seaName, color: (ORE_COL[d.seaGrade] || ORE_COL[1])[1], big: !!RARE_GEM[d.seaGrade], icon: simg, t0: performance.now() + 450 });
        }
        if (d.gemGrade) { // [2026-10-24] 보석이 함께 나옴
          (st.ores || []).forEach(function (o) { if (o.grade === d.gemGrade) o.qty = d.gemQty != null ? d.gemQty : (o.qty | 0) + 1; }); // [2026-11-18] 서버가 gemQty를 안 보내 개수가 비던 것
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
    // [2026-11-20] "전체 지도 글씨가 하얗게, 그림도 음영이 져서 잘 안 보여" -- 폰 다크 모드(카카오톡 등 앱 안 브라우저/크롬의 자동 어둡게)가 밝은 지도 색을 강제로 뒤집었다
    //  (어두운 글씨 -> 흰 글씨, 양피지 땅 -> 어두운 회색). 광산은 원래 어두운 화면이라 광산이 열려 있는 동안 "이 화면은 다크 테마를 직접 지원"(root color-scheme: dark)으로 알려 강제 어둡게를 끈다. 닫으면 되돌린다.
    try { minePrevScheme = document.documentElement.style.colorScheme || ''; document.documentElement.style.colorScheme = 'dark'; } catch (e) { }
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
    try { document.documentElement.style.colorScheme = minePrevScheme || ''; } catch (e) { } // [2026-11-20] 강제 어둡게 끈 것 되돌리기
    if (cfg && cfg.onClose) cfg.onClose();
  }
  // [2026-10-23] 마을로 이동 -- 어디에 있든 중앙 평원 마을(시작점)으로
  function goVillage() {
    if (!opened || busy || !map) return;
    var city = !!(st && st.respawnCity); // [2026-10-25] 부활 지점(성당에서 기도했으면 왕도 성당)으로
    if (!city && map.id === 'field' && Math.abs(P.x - map.spawnX) + Math.abs(P.y - map.spawnY) <= 6) { msg('이미 마을이에요.'); return; }
    // [2026-11-17] "마을로 가기 기능을 언제나" -- 싸우는 중/쓰러져 있어도 이동
    ask('🏠 마을로', (city ? '부활 지점인 <b>왕도 아스테리아 광장</b>' : '<b>중앙 평원 마을</b>') + '(으)로 이동할까요?', '이동', function () {
      wbDownUntil = 0; wbDownShow(); // [2026-11-17] 쓰러져 있던 회색 화면도 끝
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

  return { fishInfo: fishInfo, primal: primalGo, alchemy: function (f, n) { alchemyGo('alchemy', f, n); }, alchemyBack: function (f, n) { alchemyGo('alchemyBack', f, n); }, barber: function (a, i) { tryLook = null; lookAct(a, String(i)); }, barberTry: barberTry, /* [2026-11-20] 미용실 */ dmgSum: function (on) { dmgSumGo(on); }, /* [2026-11-20] 데미지 합산 */ charRes: function (hd) { charResGo(hd); }, /* [2026-11-20] 캐릭터 그림 16/64 */ /* [2026-11-20] 연금술사 복원 */ /* [2026-11-20] 연금술사 */ /* [2026-11-20] 태초의 보석 */ krkRespawn: function () { api('/api/tower-mine-archi?action=krkrespawn').then(function (d) { msg((d && d.message) || '요청 실패', 3500); }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); }); }, /* [2026-11-19] 관리자 */ wbTest: function (a) { api('/api/tower-mine-wb?action=' + a).then(function (d) { if (d && d.message) msg(d.message, 3000); if (a === 'stop') { wbInfo = null; wbRender(); } }).catch(function () { msg('요청 실패 -- 다시 시도해주세요.'); }); }, tkGem: function (c, n) { tokenGo('gem', c, n); }, demonRefine: function (n) { refineGo('refine', n); }, /* [2026-11-18] 예전 이름 refine이 구리 정련(refine: refine)과 겹쳐 정제사 버튼이 구리 정련을 불렀다 */ manaSwap: function (n) { refineGo('mana', n); }, mailGet: function (id) { mailGo('mailGet', id); }, tkTab: function (t) { tokenTab = t; if (smithOpen) renderSmith(smithNpc); }, mgPull: function (k, t, n) { tokenGo(n === 10 ? 'pull10' : 'pull', k, t); }, mgOn: function (c, m) { tokenGo('equip', c, m); }, mgOff: function (p, m) { tokenGo('unequip', p, m); }, mgPop: mgPopGo, twRoll: twRoll, twFloors: twFloors, twGo: twGo, twExit: twExit, twFerry: twFerry, wbJoin: wbJoin, bossCall: bossCallGo, worldBoss: function () { worldBossOpen = !worldBossOpen; if (smithOpen && smithView === 'world') renderSmith(smithNpc); if (worldBossOpen) bossListRefresh(); }, hermitGo: hermitGo, open: open, close: close, eqPop: eqPopGo, archiBuy: archiBuy, cityBuy: cityBuy, mercHire: mercHire, wpTab: wpTabGo, mercHireGo: mercHireGo, mercUse: mercUse, mercSkill: mercSkill, mercMode: mercMode, mercView: mercViewGo, mercLv99: mercLv99, mercPay: mercPay, mercAuto: mercAuto, mercSel: mercSelGo, mercTab: mercTabGo, talkNext: talkNext, smelt: smelt, gemCut: gemCut, artisan: artisan, digBtn: digBtn, atkBtn: atkBtn, ult: ultBtn, ultReset: ultReset, ultLearn: ultLearn, ultGift: ultGift, whirlSet: whirlSet, whirlLearn: whirlLearn, toggleMenu: toggleMenu, menuGo: menuGo, zoom: zoomGo, openEquip: openEquip, eqInfo: eqInfo, equipTab: equipTabGo, auraSet: auraSet, auraLearn: auraLearn, auraGift: auraGift, archiAct: archiAct, sail: sailGo, wpFix: function (id, n) { wpKeepGo('wpfix:' + id + ':' + n); }, boatFix: function (code, n) { gearAct('/api/tower-mine-archi?action=' + encodeURIComponent('boatfix:' + (code || 'PEARL') + ':' + (n || 1))); }, dig: dig, goVillage: goVillage, toggleMini: toggleMini, toggleLog: toggleLog, oreIcon: oreIcon, pickUpgrade: pickUpgrade, helmetUpgrade: helmetUpgrade, closeSmith: closeSmith, smithGo: smithGo, craft: craft, equipRing: equipRing, enhance: enhance, enhanceArmor: enhanceArmor, synthGalaxy: synthGalaxy, synth: synth, captain: captain,
           act: act, toggleAuto: toggleAuto, greet: greet, sail: sail, lookAct: lookAct, openWarp: openWarp, openWarpForm: openWarpForm, worldWarp: worldWarp, worldTab: worldTabGo, worldZoom: worldZoom, worldGo: worldGo, adminWarp: adminWarp, openWorld: openWorld, tryOn: tryOn, priestAct: priestAct, teleport: teleport, guideGo: guideGo, askYes: askYes, coatAct: coatAct, refine: refine, deliver: deliver, noticeOk: noticeOk, dayGo: dayGo, ORE_NAME: ORE_NAME, digTime: digTime };
})();
</script>
