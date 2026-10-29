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
  // ---- [2026-10-29] "무기, 곡괭이만 32x32" -- 몸(16x16)은 두 배로 키우고 무기/곡괭이/양산만 32 격자로 그린다(gear32). 화면은 같은 칸 크기에 그린다.
  //  무기: 손잡이(감은 끈) + 코등이 + 칼날(hi 밝은 날 / dk 칼등), 벨 때 궤적(trail). 비철도 = 넓적하고 이 빠진 날, 카타나 = 길고 휜 날·둥근 코등이·붉은 술,
  //  닌자의 검 = 가는 강철 날을 휘감는 푸른 번개(벨 때 큰 초승달), 백화검(白火) = 칼날이 흰 심지의 불꽃(걸음마다 일렁임).
  var W32 = {
    sword:   { bl: '#c3cad1', hi: '#ffffff', dk: '#7f8892', hg: '#ffd23f', hgd: '#b8860b', hd: '#8a5a2b', hdd: '#5a3a1a', bw: 2, n: 12, trail: '#ffffff' },
    bichul:  { bl: '#8f969c', hi: '#c8ced4', dk: '#555c62', hg: '#6b4a2a', hgd: '#3a2410', hd: '#5a3a1e', hdd: '#2e1c0c', bw: 4, n: 11, chip: 1, trail: '#c8ced4' },
    katana:  { bl: '#e8eef4', hi: '#ffffff', dk: '#9aa8b8', hg: '#1c1c22', hgd: '#d8b040', hd: '#b01c2c', hdd: '#141418', bw: 2, n: 15, curve: 1, tassel: '#e8303a', trail: '#dff0ff' },
    ninja:   { bl: '#5a6070', hi: '#d8e4f0', dk: '#2a2e38', hg: '#3a3f4a', hgd: '#1a1c22', hd: '#141418', hdd: '#3a4a7a', bw: 2, n: 14, bolt: 1, trail: '#a8dcff' },
    baekhwa: { bl: '#ffe060', hi: '#ffffff', dk: '#ff8a1a', hg: '#5a1a10', hgd: '#2a0a06', hd: '#2a1a14', hdd: '#7a2a10', bw: 3, n: 13, fire: 1, trail: '#ffb030' },
    parasol: { bl: '#ff8ab8', hi: '#ffd0e4', dk: '#e05a90', hg: '#8a5a2b', hgd: '#5a3a1a', hd: '#8a5a2b', hdd: '#5a3a1a', bw: 2, n: 11, trail: '#ffc8dc' }
  };
  function P(g, c, x, y) { if (c) { g.fillStyle = c; g.fillRect(x, y, 1, 1); } else g.clearRect(x, y, 1, 1); }
  // 빛(번개/바깥 불꽃/궤적)은 검은 테두리 없이 따로 겹친다(FX) -- 테두리가 붙으면 울타리처럼 보였다
  var FX = null;
  /** 칼 하나 -- (hx, hy) = 코등이 바로 아래 손잡이 칸, (dx, dy) = 칼끝 방향, f = 프레임(불꽃/번개가 일렁임). 칼날 폭은 방향의 오른쪽(-dy, dx)으로. */
  function blade32(g, W, hx, hy, dx, dy, f) {
    var nx = -dy, ny = dx, bw = W.bw, n = W.n;
    var at = function (i, j, c) { P(g, c, hx + dx * i + nx * j, hy + dy * i + ny * j); };
    var af = function (i, j, c) { P(FX || g, c, hx + dx * i + nx * j, hy + dy * i + ny * j); };
    for (var i = -4; i <= 0; i++) for (var j = 0; j < 2; j++) at(i, j, (i + j) % 2 ? W.hd : W.hdd); // 손잡이 + 감은 끈
    at(-5, 0, W.hgd); at(-5, 1, W.hgd);
    if (W.tassel) { var tx = hx - dx * 5, ty = hy - dy * 5; for (var k = 1; k <= 5; k++) P(g, k % 2 ? W.tassel : '#ff7a7a', tx + (k > 3 ? 1 : 0), ty + k); }
    if (W.curve) { for (var a = -1; a <= 2; a++) { at(1, a, W.hg); at(2, a, W.hg); } at(1, -1, W.hgd); at(2, 2, W.hgd); } // 둥근 코등이 + 금테
    else if (W.fire) { for (var b = -2; b <= bw + 1; b++) at(1, b, W.hg); at(1, -2, '#ff6a1a'); at(1, bw + 1, '#ff6a1a'); }
    else if (W.bolt) { for (var b2 = -1; b2 <= bw; b2++) at(1, b2, W.hg); at(1, -1, '#7ab8ff'); at(1, bw, '#7ab8ff'); }
    else { for (var b3 = -2; b3 <= bw + 1; b3++) at(1, b3, W.hg); at(1, -2, W.hgd); at(1, bw + 1, W.hgd); }
    var b0 = W.curve ? 3 : 2;
    for (var s = 0; s < n; s++) {
      var ii = b0 + s, w = s >= n - 2 ? Math.max(1, bw - (s - (n - 3))) : bw, sh = W.curve && s >= n - 5 ? -Math.ceil((s - (n - 6)) / 2) : 0;
      if (W.fire) { // 불꽃 칼날: 흰 심지 -> 노랑 -> 주황 -> 빨강, 톱니처럼 들쭉날쭉(f로 일렁임)
        var ext = [2, 1, 0, 1][(s + f) % 4], half = s >= n - 3 ? 0 : 1;
        for (var j2 = -ext - half; j2 < bw + ext + half; j2++) {
          var d = j2 < 0 ? -j2 : j2 >= bw ? j2 - bw + 1 : 0;
          (d === 0 ? at : af)(ii, j2, d === 0 ? (j2 === 1 ? '#ffffff' : '#ffe060') : d === 1 ? '#ff9a20' : '#e8401a');
        }
        if ((s + f) % 5 === 0) af(ii + 1, -ext - 2, '#ffb030');
        if ((s + f) % 5 === 2) af(ii + 1, bw + ext + 1, '#ffb030');
        continue;
      }
      for (var j3 = 0; j3 < w; j3++) at(ii, j3 + sh, j3 === 0 ? W.hi : j3 === w - 1 ? W.dk : W.bl);
      if (W.curve && s % 3 === 1) at(ii, sh + 1, '#ffffff'); // 물결 무늬(하몬)
      if (W.chip && (s === 3 || s === 7)) at(ii, bw - 1, null); // 이 빠진 날
      if (W.bolt && s % 5 !== 4) { // 칼날을 휘감는 푸른 번개
        var zl = [-2, -3, -4, -3][(s + f) % 4], zr = bw + [1, 2, 3, 2][(s + f + 2) % 4];
        af(ii, zl, s % 2 ? '#a8dcff' : '#ffffff'); af(ii, zr, s % 2 ? '#ffffff' : '#7ab8ff'); if (s % 3 === 0) af(ii + 1, zl + 1, '#7ab8ff');
      }
    }
    var te = b0 + n;
    if (W.fire) { at(te, 1, '#ffe060'); af(te + 1, f % 2, '#ff9a20'); af(te + 2, 1 - f % 2, '#e8401a'); af(te + 1, 2 - f % 2, '#ffb030'); af(te + 3, f % 2 ? 0 : 2, '#ffd040'); } // 칼끝 불꽃
    if (W.bolt) { af(te, -1, '#ffffff'); af(te + 1, 0, '#a8dcff'); af(te, 2, '#7ab8ff'); af(te + 2, 1, '#ffffff'); } // 칼끝 번개
  }
  /** 벨 때 궤적 -- (cx, cy) 중심, 반지름 r, 각도 a0 -> a1. 닌자의 검은 두껍고 큰 초승달. */
  function trail32(g, W, cx, cy, r, a0, a1) {
    var big = !!W.bolt, steps = 18;
    g = FX || g;
    for (var i = 0; i <= steps; i++) {
      var t = a0 + (a1 - a0) * i / steps, x = Math.round(cx + Math.cos(t) * r), y = Math.round(cy + Math.sin(t) * r);
      var th = big ? (i > 2 && i < steps - 2 ? 3 : 1) : (i > 3 && i < steps - 3 ? 2 : 1);
      for (var k = 0; k < th; k++) P(g, k === 0 ? (big ? '#ffffff' : W.trail) : W.trail, Math.round(cx + Math.cos(t) * (r - k)), Math.round(cy + Math.sin(t) * (r - k)));
      if (W.fire && i % 3 === 0) P(g, '#ff6a1a', x, y - 1);
    }
  }
  /** 곡괭이(1 = 들어 올림, 2 = 내려침). */
  function pick32(g, dir, side, dig) {
    var wd = '#8a5a2b', wdd = '#5a3a1a', ir = '#c3cad1', irh = '#ffffff', ird = '#7f8892';
    var stick = function (x0, y0, w, h) { for (var y = y0; y < y0 + h; y++) for (var x = x0; x < x0 + w; x++) P(g, (x + y) % 3 ? wd : wdd, x, y); };
    if (dig === 1) {
      var hx = side ? 18 : 24;
      stick(hx, 3, 2, 14);
      for (var x = hx - 6; x <= hx + 7; x++) P(g, ir, x, 1); for (var x2 = hx - 4; x2 <= hx + 5; x2++) P(g, irh, x2, 0);
      P(g, ird, hx - 7, 2); P(g, ird, hx - 7, 3); P(g, ird, hx + 8, 2); P(g, ird, hx + 8, 3); P(g, ird, hx, 2); P(g, ird, hx + 1, 2);
    } else if (side) {
      stick(19, 20, 10, 2);
      for (var y = 15; y <= 26; y++) { P(g, ir, 29, y); P(g, y < 21 ? irh : ird, 30, y); }
      P(g, ird, 30, 14); P(g, ird, 30, 27); P(g, ird, 28, 20); P(g, ird, 28, 21);
    } else {
      var up = dir === 'up', hy = up ? 2 : 29;
      stick(24, up ? 5 : 21, 2, up ? 12 : 8);
      for (var x3 = 19; x3 <= 30; x3++) { P(g, ir, x3, hy); P(g, up ? irh : ird, x3, hy + 1); }
      P(g, ird, 18, up ? 4 : 27); P(g, ird, 31, up ? 4 : 27);
    }
  }
  /** 손에 든 것(32 격자) -- 무기(가만히/휘두르기)나 곡괭이. 그린 게 있으면 true. */
  function gear32(g, dir, step, dig, lk) {
    var side = dir === 'right', wk = lk.weapon || 'sword', W = W32[wk] || W32.sword, f = step | 0;
    if (dig === 1 || dig === 2) { pick32(g, dir, side, dig); return true; }
    if (!dig) {
      if (wk === 'parasol') { // 머리 위로 펼친 분홍 양산(가장자리 물결, 흰 줄) + 손잡이
        var hx = side ? 18 : 24;
        for (var y = 3; y <= 22; y++) P(g, y % 4 ? W.hd : W.hdd, hx, y);
        [[10, 21], [6, 25], [3, 28], [2, 29]].forEach(function (r, i) { for (var x = r[0]; x <= r[1]; x++) P(g, i === 0 ? W.hi : W.bl, x, i); });
        for (var x2 = 2; x2 <= 29; x2++) if (x2 % 7 < 4) P(g, W.dk, x2, 4);
        [9, 16, 23].forEach(function (x3) { for (var y2 = 0; y2 < 4; y2++) P(g, '#ffffff', x3, y2); });
        return true;
      }
      if (side) blade32(g, W, 24, 26, 0, -1, f);
      else if (dir === 'up') blade32(g, W, 24, 20, 0, -1, f);
      else blade32(g, W, 24, 26, 0, -1, f);
      return true;
    }
    if (dig === 3) { blade32(g, W, side ? 18 : dir === 'up' ? 6 : 24, 21, 0, -1, f); return true; }
    if (dig === 4) {
      if (side) { trail32(g, W, 18, 22, 12, -1.55, -0.15); blade32(g, W, 17, 22, 1, 0, f); }
      else if (dir === 'up') blade32(g, W, 24, 15, 0, -1, f);
      else { trail32(g, W, 22, 20, 10, -0.6, 0.9); blade32(g, W, 24, 21, 0, 1, f); }
      return true;
    }
    return false;
  }
  function charSprite(dir, step, dig, lk) {
    lk = lk || {};
    if (dir === 'left') {
      var src = charSprite('right', step, dig, lk), c2 = mk(src.width, src.height), g2 = c2.getContext('2d');
      g2.translate(src.width, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
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
    outline(c, '#140d0a');
    // [2026-10-29] "무기, 곡괭이만 32x32" -- 몸(16x16)을 두 배로 키운 32x32 위에 손에 든 것(gear32)을 32 격자로 겹친다(쓰는 곳은 같은 칸 크기로 그려서 화면 배치는 그대로)
    var big = mk(T * 2, T * 2), bg = big.getContext('2d');
    bg.imageSmoothingEnabled = false; bg.drawImage(c, 0, 0, T * 2, T * 2);
    var wl = mk(T * 2, T * 2), fl = mk(T * 2, T * 2);
    FX = fl.getContext('2d');
    var drew = gear32(wl.getContext('2d'), dir, step, dig, lk);
    FX = null;
    if (drew) { bg.drawImage(outline(wl, '#140d0a'), 0, 0); bg.drawImage(fl, 0, 0); } // 빛은 테두리 없이 맨 위에
    return big;
  }
  // [2026-10-29] 환생 날개 다시 그리기 -- "날개는 더 날개같이, 펄럭이는 효과, 전부 흰색 + 치장상점에서 염색" (참고: 큰 천사 날개 / 작은 날개)
  //  어깨에서 위로 휘어 오르는 날개뼈(앞날) + 그 아래로 늘어진 칼깃(바깥일수록 길다) + 날개뼈 아래 짧은 덮깃. 단계(1~5)가 오를수록 크고 깃이 많다.
  //  32x32 캐릭터 기준 캔버스(폭 32 + 2*ext, 높이 top + 36) -- 캐릭터는 (ext, top)에 겹친다. f = 날갯짓 프레임(0~3), dye = 염색('#색' / 'rainbow' / '' = 흰색).
  // [2026-10-29] "날개는 더 아래쪽까지" -- 칼깃이 발 근처까지 길게, 더 곧게 늘어진다(참고: 몸 옆으로 길게 내려오는 날개)
  var WING_SIZE = [null, { span: 9, rise: 7, drop: 16, n: 5 }, { span: 12, rise: 10, drop: 19, n: 6 }, { span: 15, rise: 13, drop: 22, n: 7 },
                   { span: 18, rise: 16, drop: 25, n: 8 }, { span: 21, rise: 19, drop: 28, n: 9 }];
  var WING_FLAP = [0, 0.22, 0.38, 0.18]; // 내려옴 -> 올라감 -> 가장 위 -> 내려옴
  var WING_RAIN = ['#ff7a7a', '#ffb04a', '#ffe14d', '#7ad87a', '#6ab8ff', '#a08aff', '#ff8ad8'];
  function wingPal(dye) {
    if (!dye || dye === 'rainbow') return { hi: '#ffffff', base: '#f4f6fa', mid: '#d8e0ec', dk: '#a8b4c8', line: '#c8d2e0' };
    return { hi: shade(dye, 0.55), base: dye, mid: shade(dye, -0.12), dk: shade(dye, -0.38), line: shade(dye, -0.22) };
  }
  function oneWing(g, p, z, ax, ay, s, flap, rain) {
    var P = function (x, y, c) { g.fillStyle = c; g.fillRect(Math.round(x), Math.round(y), 1, 1); };
    var rot = function (x, y) { var dx = x - ax, dy = y - ay, a = -flap * s, cs = Math.cos(a), sn = Math.sin(a); return [ax + dx * cs - dy * sn, ay + dx * sn + dy * cs]; };
    var arm = function (t) { return [ax + s * z.span * t, ay - z.rise * Math.sin(Math.min(1, t * 1.25) * Math.PI / 2) + (t > 0.8 ? (t - 0.8) * z.rise * 1.2 : 0)]; };
    for (var i = z.n - 1; i >= 0; i--) { // 칼깃 -- 바깥부터(안쪽이 위에 겹친다)
      var t = (i + 0.5) / z.n, b = arm(t), len = z.drop * (0.7 + 0.45 * t), ang = Math.PI / 2 + s * (0.02 - 0.26 * t);
      var dx = Math.cos(ang), dy = Math.sin(ang), w0 = 1.6 + t * 0.9, fp = rain ? wingPal(WING_RAIN[i % WING_RAIN.length]) : p;
      for (var k = 0; k <= len; k++) {
        var ww = Math.max(0.6, w0 * (k < len - 2 ? 1 : (len - k + 0.5) / 2.5));
        for (var j = -ww; j <= ww; j += 0.5) { var q = rot(b[0] + dx * k - dy * j, b[1] + dy * k + dx * j); P(q[0], q[1], j > ww - 0.8 ? fp.dk : j < -ww + 0.8 ? fp.hi : k > len - 2 ? fp.mid : fp.base); }
        if (k > 1 && k < len - 1) { var sq = rot(b[0] + dx * k, b[1] + dy * k); P(sq[0], sq[1], fp.line); } // 깃대
      }
    }
    for (var i2 = 0; i2 < z.n + 2; i2++) { // 덮깃
      var t2 = (i2 + 0.3) / (z.n + 2), b2 = arm(t2), l2 = 2 + z.drop * 0.25;
      for (var k2 = 0; k2 <= l2; k2++) for (var j2 = -1.2; j2 <= 1.2; j2 += 0.5) { var q2 = rot(b2[0] - s * 0.3 * k2 + j2, b2[1] + k2); P(q2[0], q2[1], k2 >= l2 - 0.5 ? p.mid : j2 > 0.6 ? p.dk : p.base); }
    }
    for (var t3 = 0; t3 <= 1; t3 += 0.02) { var a3 = arm(t3), q3 = rot(a3[0], a3[1]); P(q3[0], q3[1], p.hi); var q4 = rot(a3[0], a3[1] + 1); P(q4[0], q4[1], p.base); var q5 = rot(a3[0], a3[1] - 1); P(q5[0], q5[1], p.mid); } // 날개뼈
  }
  function wingSprite(stage, side, f, dye) {
    var z = WING_SIZE[stage], p = wingPal(dye), ext = z.span + 3, top = z.rise + 8, W = 32 + ext * 2, H = top + 16 + Math.ceil(z.drop * 1.15) + 6;
    if (side) z = { span: Math.round(z.span * 0.8), rise: Math.round(z.rise * 0.9), drop: Math.round(z.drop * 0.9), n: z.n };
    var c = mk(W, H), g = c.getContext('2d'), fl = WING_FLAP[f % 4], sy = top + 16, rain = dye === 'rainbow';
    if (side) oneWing(g, p, z, ext + 13, sy, -1, fl, rain);
    else { oneWing(g, p, z, ext + 10, sy, -1, fl, rain); oneWing(g, p, z, ext + 21, sy, 1, fl, rain); }
    outline(c, '#3a3050');
    if (stage >= 5) [[0.1, 0.2], [0.9, 0.25], [0.05, 0.6], [0.95, 0.55], [0.3, 0.05], [0.7, 0.08]].forEach(function (q, i) { // 5단계 빛 알갱이
      g.fillStyle = i % 2 ? '#7ff0ff' : '#ffffff'; g.fillRect(Math.round(q[0] * W), Math.round(q[1] * H) - (f % 2), 1, 1);
      g.fillStyle = p.hi; g.fillRect(Math.round(q[0] * W) + 1, Math.round(q[1] * H) - (f % 2), 1, 1);
    });
    return { c: c, ext: ext, top: top };
  }
  var wingCache = {};
  /** 방향에 맞는 날개(캐시). dir = down/up(양쪽) / right(등 쪽 하나) / left(뒤집기). */
  function wings(stage, dir, f, dye) {
    stage = Math.max(1, Math.min(5, stage | 0)); f = (f | 0) % 4; dye = dye || '';
    var side = dir === 'left' || dir === 'right', key = stage + (side ? dir : 'full') + f + dye;
    if (wingCache[key]) return wingCache[key];
    var w = wingSprite(stage, side, f, dye);
    if (dir === 'left') { var fc = mk(w.c.width, w.c.height), fg = fc.getContext('2d'); fg.translate(fc.width, 0); fg.scale(-1, 1); fg.drawImage(w.c, 0, 0); w = { c: fc, ext: w.ext, top: w.top }; }
    wingCache[key] = w;
    return w;
  }
  /** 날개 + 캐릭터를 한 장으로(32x32보다 크다). 환생 안 했으면 그냥 캐릭터. */
  function withWings(dir, step, dig, lk) {
    var rb = lk ? lk.rebirth | 0 : 0, ch = charSprite(dir, step, dig, lk);
    if (rb < 1) return ch;
    var w = wings(rb, dir, 0, lk.wing), c = mk(w.c.width, w.c.height), g = c.getContext('2d');
    g.drawImage(w.c, 0, 0);
    g.drawImage(ch, w.ext, w.top);
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
