<%@ page pageEncoding="UTF-8" %><%--
  [2026-10-25] 광산 캐릭터 그림(16x16 도트) -- 광산 화면(tower_view.jsp, TowerMine.charSprite)과 로그 뷰어(s5_battle_log_view.jsp)가 같이 쓴다.
  "로그에 광산 로그 보여주는 곳에 캐릭터 이미지도" 요청으로 TowerMine 안에 있던 그리는 코드를 그대로 옮겼다.
  쓰는 곳에서 <%@ include file="_mine_char.jsp" %> 로 포함 -> window.MineChar.sprite(dir, step, dig, look) = canvas, MineChar.dataUrl(look) = 정면 그림 data URL.
  look = { gender 0 남/1 여, hat '색|모양', hair '색', top '색|모양', bottom '색', shoes '색' 또는 '색|모양', gm(운영자 장비 착용), rebirth(환생 횟수 -- 날개, [2026-10-28]) } (빈 값 = 기본) -- 서버 mineLookOf와 같은 모양.
--%>
<script>
window.MineChar = (function () {
  var T = 16, urlCache = {};
  function mk(w, h) { var c = document.createElement('canvas'); c.width = w; c.height = h; return c; }
  function px(g, col, x, y, w, h) { g.fillStyle = col; g.fillRect(x, y, w || 1, h || 1); }
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
  /** 색을 밝게(f > 0)/어둡게(f < 0). */
  function shade(hex, f) {
    var n = parseInt(hex.slice(1), 16), r = n >> 16, gg = (n >> 8) & 255, b = n & 255;
    var m = function (v) { return Math.max(0, Math.min(255, Math.round(f < 0 ? v * (1 + f) : v + (255 - v) * f))); };
    return 'rgb(' + m(r) + ',' + m(gg) + ',' + m(b) + ')';
  }
  // [2026-10-24] 명품상점 외형 -- lk = { gender 0 남/1 여, hat '색|모양', hair '색', top '색|모양', bottom '색', shoes '색' } (빈 값 = 기본)
  function lookPart(v) { var p = (v || '').split('|'); return { c: p[0] || '', s: p[1] || '' }; }
  // 캐릭터: 방향(down/up/right/left) x 걸음(0/1/2) x 캐기(0 없음, 1 들기, 2 내려치기). 왼쪽은 오른쪽을 뒤집어서.
  function charSprite(dir, step, dig, lk) {
    lk = lk || {};
    if (dir === 'left') {
      var src = charSprite('right', step, dig, lk), c2 = mk(T, T), g2 = c2.getContext('2d');
      g2.translate(T, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
      return c2;
    }
    var c = mk(T, T), g = c.getContext('2d');
    var top = lookPart(lk.top), hat = lookPart(lk.hat), fem = lk.gender === 1;
    var skin = '#f6c9a0', hair = lk.hair || '#2b1d16', robe = top.c || '#3f5fd1', robeD = top.c ? shade(top.c, -0.28) : '#2c45a3';
    var shoe = lookPart(lk.shoes); // [2026-10-28] 신발도 '색|모양'(바람의 신 = wing)
    var belt = '#7a4a1f', pants = lk.bottom || '#3a2b22', boot = shoe.c || '#5b3a1e';
    var side = dir === 'right';
    var lx = side ? 6 : 5, rx = 9;
    var lh = step === 1 ? 2 : 3, rh = step === 2 ? 2 : 3;
    if (top.s === 'cape' && dir === 'up') px(g, robeD, 3, 7, 10, 7); // 기사 망토는 뒤에서 보면 등을 덮는다
    px(g, pants, lx, 12, 2, lh); px(g, boot, lx, 12 + lh, 2, 1);
    px(g, pants, rx, 12, 2, rh); px(g, boot, rx, 12 + rh, 2, 1);
    if (shoe.s === 'wing') { px(g, '#ffffff', lx - 1, 11 + lh); px(g, '#ffffff', rx + 2, 11 + rh); } // 발목의 작은 날개
    if (shoe.s === 'flame') { px(g, '#ffcf30', lx - 1, 11 + lh); px(g, '#ff6a2a', lx - 1, 12 + lh); px(g, '#ffcf30', rx + 2, 11 + rh); px(g, '#ff6a2a', rx + 2, 12 + rh); } // [2026-10-28] 불꽃 신
    px(g, robe, 4, 7, 8, 5); px(g, robeD, 4, 7, 1, 5); px(g, robeD, 11, 7, 1, 5);
    if (top.s === 'armor') { px(g, shade(robe, 0.35), 5, 7, 6, 1); px(g, shade(robe, 0.2), 5, 9, 6, 1); px(g, '#d9d0b8', 4, 10, 8, 1); }
    else if (top.s === 'hanbok') { px(g, '#c0392b', 6, 7, 4, 1); px(g, '#c0392b', 7, 8, 2, 2); px(g, '#2e5aa8', 4, 10, 8, 1); }
    // [2026-10-28] 치장 추가: 기사 망토 / 눈꽃 털옷 / 별빛 로브 / 운영자 제복
    else if (top.s === 'cape') { if (dir !== 'up') { px(g, robeD, 3, 11, 1, 2); px(g, robeD, 12, 11, 1, 2); } px(g, '#ffd84a', 7, 7, 2, 1); px(g, belt, 4, 10, 8, 1); }
    else if (top.s === 'fur') { px(g, '#d8c8a8', 4, 7, 8, 1); px(g, '#d8c8a8', 4, 11, 8, 1); px(g, '#9fd8f0', 7, 9, 2, 1); }
    else if (top.s === 'star') { px(g, '#ffe14d', 5, 8); px(g, '#ffffff', 9, 8); px(g, '#ffe14d', 8, 10); px(g, '#9fd8f0', 6, 11); px(g, '#ffe14d', 10, 11); }
    else if (top.s === 'gm') { px(g, '#ffd84a', 4, 7, 8, 1); px(g, '#ffd84a', 7, 8, 2, 3); px(g, '#ffd84a', 4, 11, 8, 1); if (dir !== 'up') px(g, '#ff3b6b', 9, 8); }
    // [2026-10-28] 고급 치장: 연미복 / 후드티 / 세일러복 / 천사·악마 날개옷 / 무지개 로브
    else if (top.s === 'tux') { if (dir !== 'up') { px(g, '#ffffff', 7, 7, 2, 4); px(g, '#d4104f', 7, 7, 2, 1); } px(g, robeD, 4, 11, 8, 1); }
    else if (top.s === 'hoodie') { px(g, robeD, 5, 6, 6, 1); if (dir !== 'up') { px(g, '#ffffff', 7, 8); px(g, '#ffffff', 8, 8); px(g, robeD, 6, 10, 4, 1); } else px(g, robeD, 5, 7, 6, 2); }
    else if (top.s === 'sailor') { px(g, '#2a3a8a', 4, 7, 8, 1); px(g, '#2a3a8a', 5, 8, 6, 1); if (dir !== 'up') px(g, '#d4104f', 7, 8, 2, 2); px(g, '#2a3a8a', 4, 11, 8, 1); }
    else if (top.s === 'wings' || top.s === 'bat') {
      var wc = top.s === 'wings' ? '#ffffff' : '#2a0a1a', wd = top.s === 'wings' ? '#d8e8ff' : '#5a1030';
      if (side) { px(g, wc, 2, 7, 3, 3); px(g, wd, 1, 8, 1, 2); }
      else { px(g, wc, 1, 7, 3, 3); px(g, wc, 12, 7, 3, 3); px(g, wd, 0, 8, 1, 2); px(g, wd, 15, 8, 1, 2); }
      if (top.s === 'bat') { px(g, wd, 2, 10); px(g, wd, 13, 10); }
      px(g, top.s === 'wings' ? '#ffe97a' : '#c0283a', 4, 10, 8, 1);
    }
    else if (top.s === 'rainbow') { ['#e8433a', '#f08a30', '#ffcf30', '#4fbf6a', '#3a8ae8'].forEach(function (rc, i) { px(g, rc, 4, 7 + i, 8, 1); }); }
    else px(g, belt, 4, 10, 8, 1);
    if (fem) { px(g, robe, 3, 11, 10, 2); px(g, robeD, 3, 12, 10, 1); } // 치마처럼 퍼지는 아랫단
    if (side) { px(g, robeD, 7, 8, 2, 3); px(g, skin, 8, 11); }
    else { px(g, robe, 3, 8, 1, 3); px(g, robe, 12, 8, 1, 3); px(g, skin, 3, 11); px(g, skin, 12, 11); }
    px(g, skin, 4, 1, 8, 6);
    if (dir === 'up') { px(g, hair, 4, 0, 8, 6); px(g, shade(hair, 0.15), 6, 1, 4, 1); if (fem) px(g, hair, 4, 6, 8, 3); }
    else if (side) { px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 3, fem ? 6 : 3); px(g, '#1a1a1a', 10, 4); px(g, '#f29b8a', 10, 5); }
    else {
      px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 1, fem ? 6 : 2); px(g, hair, 11, 3, 1, fem ? 6 : 2);
      px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4); px(g, '#f29b8a', 5, 5); px(g, '#f29b8a', 10, 5);
      if (fem) { px(g, '#1a1a1a', 5, 3); px(g, '#1a1a1a', 10, 3); px(g, '#d9536f', 7, 6, 2, 1); } // 속눈썹 + 입술
    }
    if (hat.s) { // 모자는 머리 위에 덮어 그린다
      var hc = hat.c, hd = shade(hc, -0.3);
      if (hat.s === 'straw') { px(g, hc, 2, 2, 12, 1); px(g, hc, 5, 0, 6, 2); px(g, '#b5251f', 5, 1, 6, 1); }
      else if (hat.s === 'helmet') { px(g, hc, 4, 0, 8, 2); px(g, hd, 3, 2, 10, 1); px(g, '#ffe14d', 7, 0, 2, 1); }
      else if (hat.s === 'pirate') { px(g, hc, 3, 1, 10, 2); px(g, hc, 5, 0, 6, 1); px(g, '#ffffff', 7, 1, 2, 1); }
      else if (hat.s === 'wizard') { px(g, hc, 7, 0, 2, 1); px(g, hc, 6, 1, 4, 1); px(g, hd, 3, 2, 10, 1); px(g, '#ffe14d', 8, 1); }
      else if (hat.s === 'crown') { px(g, hc, 4, 1, 8, 1); px(g, hc, 4, 0); px(g, hc, 7, 0, 2, 1); px(g, hc, 11, 0); px(g, '#d4104f', 7, 1, 2, 1); }
      // [2026-10-28] 치장 추가
      else if (hat.s === 'cat') { px(g, hc, 4, 0, 2, 2); px(g, hc, 10, 0, 2, 2); px(g, '#f29bb0', 5, 1); px(g, '#f29bb0', 10, 1); }
      else if (hat.s === 'flower') { px(g, '#3a9a4a', 4, 1, 8, 1); px(g, hc, 4, 0); px(g, hc, 7, 0, 2, 1); px(g, hc, 11, 0); px(g, '#ffe14d', 5, 1); px(g, '#ffe14d', 10, 1); }
      else if (hat.s === 'santa') { px(g, hc, 5, 0, 6, 2); px(g, hc, 11, 1); px(g, '#ffffff', 12, 1); px(g, '#ffffff', 4, 2, 8, 1); }
      else if (hat.s === 'horn') { px(g, hc, 4, 0, 1, 2); px(g, hc, 11, 0, 1, 2); px(g, '#ffd84a', 4, 0); px(g, '#ffd84a', 11, 0); }
      // [2026-10-28] 고급 치장 모자
      else if (hat.s === 'rabbit') { px(g, hc, 4, 2, 8, 1); px(g, hc, 5, 0, 1, 2); px(g, hc, 10, 0, 1, 2); px(g, '#f29bb0', 5, 1); px(g, '#f29bb0', 10, 1); }
      else if (hat.s === 'halo') { px(g, hc, 4, 0, 8, 1); px(g, '#ffffff', 6, 0, 2, 1); }
      else if (hat.s === 'beret') { px(g, hc, 3, 1, 9, 2); px(g, hc, 5, 0, 6, 1); px(g, hd, 11, 1); px(g, hd, 7, 0); }
      else if (hat.s === 'ribbon') { px(g, hc, 9, 0, 4, 2); px(g, hd, 10, 0, 2, 2); px(g, '#ffffff', 9, 0); }
      else if (hat.s === 'tiara') { px(g, hc, 5, 1, 6, 1); px(g, hc, 7, 0, 2, 1); px(g, '#ff8ad8', 7, 1, 2, 1); px(g, '#ffffff', 5, 0); px(g, '#ffffff', 10, 0); }
      else if (hat.s === 'gmcrown') { // 운영자 왕관 -- 뾰족한 큰 왕관 + 보석
        px(g, hc, 3, 1, 10, 2); px(g, hc, 3, 0); px(g, hc, 6, 0); px(g, hc, 9, 0); px(g, hc, 12, 0);
        px(g, '#3ad1ff', 5, 2); px(g, '#ff3b6b', 7, 1, 2, 1); px(g, '#3ad1ff', 10, 2); px(g, '#ffffff', 8, 2);
      }
    }
    var wood = '#8a5a2b', iron = '#c3cad1';
    if (!dig) { // [2026-10-28] "무기 아이템(기본은 칼) 장착을 보여주고" -- 캐지 않을 때 손에 든 무기(lk.weapon: sword 기본 / parasol 양산)
      if (lk.weapon === 'parasol') { // 머리 위로 펼친 분홍 양산 + 손잡이
        px(g, '#ff8ab8', 3, 0, 10, 1); px(g, '#ff8ab8', 1, 1, 14, 1); px(g, '#e05a90', 1, 1); px(g, '#e05a90', 14, 1); px(g, '#ffffff', 5, 0, 1, 2); px(g, '#ffffff', 10, 0, 1, 2);
        if (side) px(g, wood, 9, 2, 1, 9); else px(g, wood, 12, 2, 1, 9);
      } else if (side) { px(g, wood, 9, 11); px(g, '#ffd23f', 10, 10, 1, 3); px(g, iron, 11, 11, 4, 1); px(g, '#ffffff', 14, 11); }
      else if (dir === 'up') { px(g, wood, 12, 9, 1, 2); px(g, '#ffd23f', 11, 8, 3, 1); px(g, iron, 12, 4, 1, 4); }
      else { px(g, wood, 12, 12); px(g, '#ffd23f', 11, 11, 3, 1); px(g, iron, 12, 6, 1, 5); px(g, '#ffffff', 12, 6); }
    }
    if (dig === 1) { // 들어 올림
      if (side) { px(g, wood, 9, 1, 1, 7); px(g, iron, 7, 0, 5, 1); px(g, iron, 7, 1); px(g, iron, 11, 1); }
      else { px(g, wood, 12, 1, 1, 8); px(g, iron, 10, 0, 5, 1); px(g, iron, 10, 1); px(g, iron, 14, 1); }
    } else if (dig === 2) { // 내려치기
      if (side) { px(g, wood, 10, 10, 5, 1); px(g, iron, 15, 8, 1, 5); }
      else if (dir === 'up') { px(g, wood, 12, 2, 1, 6); px(g, iron, 10, 1, 5, 1); }
      else { px(g, wood, 12, 11, 1, 4); px(g, iron, 10, 15, 5, 1); }
    }
    return outline(c, '#140d0a');
  }
  // [2026-10-28] 환생 날개(1~5단계) -- "환생하면 캐릭터에 작은 날개, 환생 단계가 오를수록 날개가 커지게". 사용자가 고른 시안 그대로.
  //  wingSprite(stage) = { c: 캔버스(폭 16 + 2*ext, 높이 18), ext } -- 날개만 그린다. 캐릭터는 그 위 (ext, 2)에 겹쳐 그린다(어깨 = 캔버스 y 9).
  //  side = true: 옆모습(left/right) -- 등 쪽 날개 하나만, 조금 작게(바깥 길이 60%), 어깨 대신 등(캐릭터 x 3~4)에 붙인다. 오른쪽을 보는 기준(날개는 왼쪽), 왼쪽은 뒤집는다.
  var WING_PAL = [null,
    { base: '#ffffff', edge: '#c8d6ea', line: '#e4ecf6', tip: '#ffffff' },   // 1 하얀 아기 날개
    { base: '#eef8ff', edge: '#8cc4ee', line: '#cfe8fa', tip: '#ffffff' },   // 2 하늘빛
    { base: '#fffaf0', edge: '#e8b830', line: '#f6dc8a', tip: '#ffffff' },   // 3 금빛 테두리
    { base: '#ffe27a', edge: '#d08a18', line: '#f2c240', tip: '#fff6c8' },   // 4 황금 날개
    { base: '#f2e6ff', edge: '#9a6cf0', line: '#d6c0ff', tip: '#7ff0ff' }];  // 5 별빛(보라 + 하늘빛 끝)
  var wingCache = {};
  function wingSprite(stage, side) {
    stage = Math.max(1, Math.min(5, stage | 0));
    var FULL = [0, 4, 6, 8, 10, 12][stage], EXT = side ? Math.max(3, Math.round(FULL * 0.6)) : FULL, W = 16 + EXT * 2, H = 18, SY = 9, SHIFT = side ? 3 : 0;
    var c = mk(W, H), g = c.getContext('2d');
    var p = WING_PAL[stage], rise = 0.45 + stage * 0.06, drop = 0.55;
    function wpx(x, y, col) { if (x >= 0 && y >= 0 && x < W && y < H) { g.fillStyle = col; g.fillRect(x, y, 1, 1); } }
    // 어깨에서 바깥으로 한 칸씩(i): 위 끝은 바깥일수록 높이 솟고, 아래 끝은 바깥일수록 짧아지며 깃털처럼 들쭉날쭉
    function wing(sd) {
      for (var i = 0; i < EXT + 2; i++) {
        var x = sd < 0 ? EXT + 1 - i + SHIFT : EXT + 14 + i; // 어깨 쪽 2칸은 몸 뒤에 겹친다
        var top = SY - Math.round(Math.max(0, i - 1) * rise);
        var bot = SY + Math.round((EXT + 2 - i) * drop) - (i % 2 === 1 && i > 1 ? 1 : 0);
        if (bot < top + 1) bot = top + 1;
        for (var y = top; y <= bot; y++) {
          var col = y === top ? (i >= EXT ? p.tip : p.edge) : y === bot ? p.edge : (stage >= 3 && (y - top) % 3 === 2 ? p.line : p.base);
          wpx(x, y, col);
        }
      }
      if (stage >= 4) { var tx = sd < 0 ? SHIFT : W - 1, ty = SY - Math.round((EXT) * rise); wpx(tx, ty - 1, p.tip); } // 높이 솟은 깃 끝
      if (stage === 5) { var sx = sd < 0 ? 1 + SHIFT : W - 2, sy2 = SY - Math.round((EXT - 1) * rise) - 3; wpx(sx, sy2, '#ffffff'); wpx(sx + (sd < 0 ? 1 : -1), sy2 + 1, p.tip); } // 별빛 반짝이
    }
    wing(-1);
    if (!side) wing(1);
    outline(c, '#140d0a');
    return { c: c, ext: EXT };
  }
  /** 방향에 맞는 날개(캐시). dir = down/up(양쪽 날개 다) / right(등 쪽 = 왼쪽 날개 하나) / left(뒤집기). */
  function wings(stage, dir) {
    stage = Math.max(1, Math.min(5, stage | 0));
    var key = stage + (dir === 'left' || dir === 'right' ? dir : 'full');
    if (wingCache[key]) return wingCache[key];
    var w = wingSprite(stage, dir === 'left' || dir === 'right');
    if (dir === 'left') { // 왼쪽을 보면 등이 오른쪽 -- 뒤집는다(캔버스 폭이 16 + 2ext로 좌우 대칭이라 ext는 그대로)
      var f = mk(w.c.width, w.c.height), fg = f.getContext('2d');
      fg.translate(f.width, 0); fg.scale(-1, 1); fg.drawImage(w.c, 0, 0);
      w = { c: f, ext: w.ext };
    }
    wingCache[key] = w;
    return w;
  }
  /** 날개 + 캐릭터를 한 장으로(폭 16 + 2ext, 높이 18 -- 16x16보다 크다). 환생 안 했으면 그냥 16x16 캐릭터. */
  function withWings(dir, step, dig, lk) {
    var rb = lk ? lk.rebirth | 0 : 0, ch = charSprite(dir, step, dig, lk);
    if (rb < 1) return ch;
    var w = wings(rb, dir), c = mk(w.c.width, w.c.height), g = c.getContext('2d');
    g.drawImage(w.c, 0, 0);
    g.drawImage(ch, w.ext, 2);
    return c;
  }
  /** 정면(서 있는 모습) 그림 data URL -- 외형이 같으면 한 번만 그린다.
   *  [2026-10-28] wings = true면 환생 날개까지 그린 그림(환생했으면 16x16보다 넓다 -- 쓰는 곳이 가로세로 비율을 지켜야 함, wide(lk)로 확인). */
  function dataUrl(lk, wingsOn) {
    var key = (wingsOn ? 'w' : '') + JSON.stringify(lk || {});
    if (!urlCache[key]) urlCache[key] = (wingsOn ? withWings('down', 0, 0, lk) : charSprite('down', 0, 0, lk)).toDataURL();
    return urlCache[key];
  }
  /** 날개 때문에 그림이 16x16보다 넓은지(환생 1 이상). */
  function wide(lk) { return !!(lk && (lk.rebirth | 0) > 0); }
  return { sprite: charSprite, dataUrl: dataUrl, wings: wings, withWings: withWings, wide: wide };
})();
</script>
