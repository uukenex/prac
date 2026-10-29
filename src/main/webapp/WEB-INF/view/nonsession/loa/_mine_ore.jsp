<%@ page pageEncoding="UTF-8" %><%--
  [2026-10-28] "로그 페이지 / 광산 캐릭터 현황을 인게임과 비슷하게" -- 광석·보석 이름/색/인벤토리 분류(기타 탭과 같은 순서).
  광산 화면(_tower_mine.jsp의 ORE_NAME/ORE_COL/INV_CATS)과 같은 값을 관리자 페이지용으로 옮겨 둔 것(광산 화면은 이 파일을 안 쓴다 -- 색을 바꾸면 둘 다).
  쓰는 곳에서 <%@ include file="_mine_ore.jsp" %> -> window.MineOre.name[g], col[g] = [진한색, 밝은색], cat(g) = INV_CATS 번호, byName(이름) = 등급,
  swatch(g) = 작은 보석 모양 HTML(.ore-sw, 색은 인라인).
--%>
<script>
window.MineOre = (function () {
  var name = ['', '구리 광석', '철광석', '은광석', '금광석', '미스릴 원석', '별빛석'], col = [null, ['#b8652e', '#f0a066'], ['#7d8790', '#c9d1d8'], ['#cfd9e3', '#ffffff'], ['#f2b705', '#fff1a1'], ['#20c9b8', '#b9fff6'], ['#f23fd0', '#ffffff']];
  ['월요석', '화요석', '수요석', '목요석', '금요석', '토요석', '일요석', '칠요석'].forEach(function (n, i) { name[11 + i] = n; });
  [['#b9c6ff', '#ffffff'], ['#ff4b2e', '#ffc0a8'], ['#2f8cff', '#b8dcff'], ['#33b34a', '#c4f5c8'], ['#ffcf26', '#fff6b8'], ['#a8693a', '#e8c09a'], ['#ff8c12', '#ffe0a0'], ['#9b5cff', '#ffffff']].forEach(function (c, i) { col[11 + i] = c; });
  name[19] = '빙석'; col[19] = ['#7fd0f0', '#eafaff']; name[20] = '달빛석'; col[20] = ['#8fa8e8', '#f4f8ff'];
  ['재스퍼', '루비', '스타 사파이어', '아쿠아마린', '토파즈', '가넷', '스피넬', '에메랄드', '다이아몬드'].forEach(function (n, i) { name[21 + i] = n; });
  [['#a8452f', '#e8907a'], ['#d4104f', '#ff9ab8'], ['#2448b8', '#bcd0ff'], ['#4fc8d8', '#d4fbff'], ['#f0a530', '#ffe2a8'],
   ['#7d1426', '#e0697c'], ['#e0508e', '#ffc4dc'], ['#159a4c', '#a8f0c4'], ['#cfefff', '#ffffff']].forEach(function (c, i) { col[21 + i] = c; });
  name[31] = '타락한 대주교의 인장'; col[31] = ['#7a2a8a', '#f0c8ff']; name[32] = '닌자의 비급서'; col[32] = ['#3a2a1a', '#e8d8a8'];
  name[33] = '얼음의 심장'; col[33] = ['#4fc8f0', '#f0fcff'];
  ['진주', '산호석', '자수정', '오팔', '흑진주'].forEach(function (n, i) { name[34 + i] = n; });
  [['#f4eee4', '#ffffff'], ['#ff7a8a', '#ffd0d8'], ['#9a5ad8', '#e0c8ff'], ['#8ad8d0', '#fff0f8'], ['#3a3a4a', '#b8b8d0']].forEach(function (c, i) { col[34 + i] = c; });
  name[39] = '사막의 심장'; col[39] = ['#e8a23a', '#fff0c0'];
  name[60] = '닌자의 검 조각'; col[60] = ['#4a4f5c', '#d8dee8']; // [2026-10-29]
  ['동괴', '철괴', '은괴', '금괴', '미스릴괴'].forEach(function (n, i) { name[40 + i] = n; col[40 + i] = col[1 + i]; });
  for (var i = 0; i < 9; i++) { name[45 + i] = '세공된 ' + name[21 + i]; col[45 + i] = col[21 + i]; }
  name[54] = '세공된 빙석'; col[54] = col[19];
  for (var j = 0; j < 5; j++) { name[55 + j] = '세공된 ' + name[34 + j]; col[55 + j] = col[34 + j]; }
  // 광산 화면 인벤토리 기타 탭(INV_CATS)과 같은 분류 -- [아이콘 이름, 조건]
  var CATS = [
    ['⛏️ 광석', function (g) { return (g >= 1 && g <= 6) || g === 19 || g === 20; }],
    ['💎 보석', function (g) { return (g >= 21 && g <= 29) || (g >= 34 && g <= 38); }],
    ['🔥 괴', function (g) { return g >= 40 && g <= 44; }],
    ['✨ 세공', function (g) { return g >= 45 && g <= 59; }],
    ['📅 요일석', function (g) { return g >= 11 && g <= 18; }],
    ['👑 고유 증표', function (g) { return g === 31 || g === 32 || g === 33 || g === 39; }],
    ['🧩 조각', function (g) { return g === 60; }]
  ];
  var byNameMap = {};
  name.forEach(function (n, g) { if (n) byNameMap[n] = g; });
  function cat(g) { for (var k = 0; k < CATS.length; k++) if (CATS[k][1](g)) return k; return CATS.length; }
  function byName(n) { return byNameMap[String(n || '').trim()] || 0; }
  function swatch(g) {
    var c = col[g] || ['#8a8070', '#d8d0c0'], cut = g >= 45 && g <= 59;
    return '<i class="ore-sw' + (cut ? ' cut' : '') + '" style="background:linear-gradient(135deg,' + c[1] + ' 0 35%,' + c[0] + ' 36%)"></i>';
  }
  return { name: name, col: col, cats: CATS, cat: cat, byName: byName, swatch: swatch };
})();
</script>
