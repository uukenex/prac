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
    immortal: { bl: '#fff4cc', hi: '#ffffff', dk: '#d8b04a', hg: '#f0d060', hgd: '#8a6a20', hd: '#3a2a4a', hdd: '#1a1024', bw: 3, n: 16, trail: '#fff0a0' }, // [2026-11-17] 심판의 검 -- 희게 빛나는 넓은 날, 금빛 코등이, 검보라 손잡이
    sea:     { bl: '#4ab8f0', hi: '#e8fbff', dk: '#1a5a9a', hg: '#e8c860', hgd: '#8a6a20', hd: '#14304a', hdd: '#0a1a2e', bw: 3, n: 15, wind: 1, trail: '#9fe8ff' }, // [2026-11-18] 바다의 검(시안 승인) -- 물빛 넓은 날, 금 코등이, 손잡이 물보라(wind 장식 재사용)
    harpy:   { bl: '#7ae8c8', hi: '#f0fff8', dk: '#2a9a78', hg: '#e8fff4', hgd: '#5ab89a', hd: '#1e4a3a', hdd: '#3a7a5a', bw: 3, n: 15, wind: 1, trail: '#bfeee0' }, // [2026-11-11] 하피의 검 -- 비취색 날, 깃털 날개 코등이, 칼날을 감는 바람, 손잡이 깃털 장식
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
    if (W.wind) { var wx0 = hx - dx * 5, wy0 = hy - dy * 5; for (var k0 = 1; k0 <= 4; k0++) P(g, k0 % 2 ? '#5ad8b0' : '#f0fff8', wx0 + (k0 > 2 ? 1 : 0), wy0 + k0); } // 손잡이 깃털 장식
    if (W.tassel) { var tx = hx - dx * 5, ty = hy - dy * 5; for (var k = 1; k <= 5; k++) P(g, k % 2 ? W.tassel : '#ff7a7a', tx + (k > 3 ? 1 : 0), ty + k); }
    if (W.curve) { for (var a = -1; a <= 2; a++) { at(1, a, W.hg); at(2, a, W.hg); } at(1, -1, W.hgd); at(2, 2, W.hgd); } // 둥근 코등이 + 금테
    else if (W.fire) { for (var b = -2; b <= bw + 1; b++) at(1, b, W.hg); at(1, -2, '#ff6a1a'); at(1, bw + 1, '#ff6a1a'); }
    else if (W.wind) { for (var b4 = -3; b4 <= bw + 2; b4++) at(1, b4, b4 < 0 || b4 >= bw ? W.hg : W.hgd); at(0, -3, W.hg); at(0, bw + 2, W.hg); at(2, -4, '#bfeee0'); at(2, bw + 3, '#bfeee0'); at(1, -4, '#ffffff'); at(1, bw + 3, '#ffffff'); } // 깃털 날개 코등이
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
      if (W.wind && (s + f * 2) % 5 === 0) { af(ii, -2, '#bfeee0'); af(ii + 1, -3, '#ffffff'); af(ii + 2, bw + 1, '#bfeee0'); af(ii + 3, bw + 2, '#ffffff'); } // 칼날을 감고 오르는 바람
      if (W.bolt && s % 5 !== 4) { // 칼날을 휘감는 푸른 번개
        var zl = [-2, -3, -4, -3][(s + f) % 4], zr = bw + [1, 2, 3, 2][(s + f + 2) % 4];
        af(ii, zl, s % 2 ? '#a8dcff' : '#ffffff'); af(ii, zr, s % 2 ? '#ffffff' : '#7ab8ff'); if (s % 3 === 0) af(ii + 1, zl + 1, '#7ab8ff');
      }
    }
    var te = b0 + n;
    if (W.fire) { at(te, 1, '#ffe060'); af(te + 1, f % 2, '#ff9a20'); af(te + 2, 1 - f % 2, '#e8401a'); af(te + 1, 2 - f % 2, '#ffb030'); af(te + 3, f % 2 ? 0 : 2, '#ffd040'); } // 칼끝 불꽃
    if (W.wind) { at(te, 1, '#f0fff8'); af(te + 1, f % 2 ? 0 : 2, '#bfeee0'); af(te + 2, 1, '#ffffff'); } // 칼끝 바람
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
  // [2026-11-06] 용병 궁수의 활 / 마법사의 지팡이(32 격자). drawn = 쏘는 중 -- 활은 시위를 당겨 화살을 메기고, 지팡이는 끝의 구슬이 밝게 빛난다.
  function bow32(g, dir, side, drawn) {
    var wd = '#a8743a', wdd = '#5a3a1a', st = '#f0ece0';
    if (side) { // 옆모습: 앞(오른쪽)으로 세워 든 활, 시위는 몸 쪽
      var bx = drawn ? 23 : 25;
      for (var y = 9; y <= 31; y++) P(g, y % 4 ? wd : wdd, bx + Math.round(Math.sin((y - 9) / 22 * Math.PI) * 4), y);
      for (var y2 = 10; y2 <= 30; y2++) P(g, st, bx - (drawn ? Math.round((1 - Math.abs(y2 - 20) / 10) * 4) : 0), y2);
      if (drawn) { for (var x = bx - 4; x <= bx + 5; x++) P(g, '#c8a070', x, 20); P(g, '#e8e8f0', bx + 6, 20); P(g, '#e8e8f0', bx + 5, 19); P(g, '#e8e8f0', bx + 5, 21); P(g, '#ff5a4a', bx - 4, 19); P(g, '#ff5a4a', bx - 4, 21); }
    } else { // 앞/뒤: 몸 옆에 세워 든다
      var hx = dir === 'up' ? 5 : 25;
      for (var y3 = 10; y3 <= 30; y3++) P(g, y3 % 4 ? wd : wdd, hx + Math.round(Math.sin((y3 - 10) / 20 * Math.PI) * 3), y3);
      for (var y4 = 11; y4 <= 29; y4++) P(g, st, hx - (drawn && y4 > 15 && y4 < 25 ? 1 : 0), y4);
      if (drawn && dir !== 'up') for (var y5 = 13; y5 <= 27; y5++) P(g, y5 === 13 ? '#e8e8f0' : '#c8a070', hx - 2, y5);
    }
  }
  function staff32(g, dir, side, glow, f) {
    var wd = '#7a4a22', wdd = '#4a2a12', hx = side ? (glow ? 26 : 24) : dir === 'up' ? 5 : 25, top = glow ? 5 : 8;
    for (var y = top + 3; y <= 31; y++) { P(g, y % 3 ? wd : wdd, hx, y); P(g, wdd, hx + 1, y); }
    P(g, wd, hx - 1, top + 2); P(g, wd, hx + 2, top + 2); P(g, wd, hx - 2, top + 1); P(g, wd, hx + 3, top + 1); // 구슬을 쥔 갈고리
    for (var j = 0; j < 3; j++) for (var i = 0; i < 4; i++) P(g, i === 1 && j === 0 ? '#ffffff' : j === 2 ? '#6a3ad0' : '#9a6aff', hx - 1 + i, top - 1 + j);
    var fx = FX || g, gc = glow ? '#e8d8ff' : '#b89aff';
    [[-3, 0], [4, 0], [1, -4], [0, 3], [-2, -3], [3, -3]].slice(0, glow ? 6 : f % 2 ? 2 : 0).forEach(function (q) { P(fx, gc, hx + q[0], top + q[1]); });
  }
  /** 손에 든 것(32 격자) -- 무기(가만히/휘두르기)나 곡괭이. 그린 게 있으면 true. */
  function gear32(g, dir, step, dig, lk) {
    var side = dir === 'right', wk = lk.weapon || 'sword', W = W32[wk] || W32.sword, f = step | 0;
    if (dig === 1 || dig === 2) { pick32(g, dir, side, dig); return true; }
    if (wk === 'bow') { bow32(g, dir, side, dig === 3 || dig === 4); return true; } // [2026-11-06] 용병 궁수
    if (wk === 'staff') { staff32(g, dir, side, dig === 3 || dig === 4, f); return true; } // [2026-11-06] 용병 마법사
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
    if (HD_ON && !lk.noHD) return heroSprite(dir, step, dig, lk); // [2026-11-20] 옵션 64x64
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
    // [2026-11-19] "NPC 룩템 -- 기사단장 코스튬, 기사단원 코스튬" -- 탑 가는 길 기사단(_tower_mine.jsp npcSprite guard/knight)처럼: 판금 갑옷 + 정강이 받이 + 투구(깃),
    //  단원 = 강철 + 푸른 깃 + 방패, 단장 = 금빛 + 붉은 깃 + 붉은 망토. 입으면 모자 대신 투구를 쓴다.
    var KN = top.s === 'knight' || top.s === 'knightlord' ? (top.s === 'knightlord' ? { a1: '#d9b44a', a2: '#a8842a', a3: '#f6e3a0', acc: '#c8202a', accD: '#8a1018', lord: true } : { a1: '#9aa3b6', a2: '#666f82', a3: '#dfe4ee', acc: '#3557a8', accD: '#22386e', lord: false }) : null;
    if (KN && KN.lord && dir !== 'up') { px(g, KN.acc, side ? 3 : 2, 7, 1, 8); if (!side) px(g, KN.acc, 13, 7, 1, 8); px(g, KN.accD, side ? 3 : 2, 13, 1, 2); if (!side) px(g, KN.accD, 13, 13, 1, 2); } // 망토(몸 뒤)
    var lh = step === 1 ? 2 : 3, rh = step === 2 ? 2 : 3;
    if (top.s === 'cape' && dir === 'up') px(g, robeD, 3, 7, 10, 7); // 기사 망토는 뒤에서 보면 등을 덮는다
    var tailC = lk.tail, tailD = tailC ? shade(tailC, -0.25) : ''; // [2026-11-06] 용병(고양이 소녀) 꼬리 -- 앞/옆은 몸 뒤로, 뒷모습은 등 위에
    if (tailC && dir !== 'up') (side ? [[3, 12], [2, 11], [2, 10], [1, 9], [1, 8], [2, 7]] : [[12, 12], [13, 11], [13, 10], [14, 9], [14, 8], [13, 7]]).forEach(function (q, i) { px(g, i >= 4 ? tailD : tailC, q[0], q[1]); });
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
    // [2026-11-06] 용병 옷차림 치장: 메이드복 / 기모노 / 무녀복 / 학원 블레이저 / 고딕 드레스
    else if (top.s === 'maid') { if (dir !== 'up') { px(g, '#ffffff', 6, 7, 4, 1); px(g, '#f8f8fa', 6, 8, 4, 4); px(g, '#f8f8fa', 5, 10, 6, 2); } else px(g, '#f8f8fa', 6, 10, 4, 1); }
    else if (top.s === 'kimono') { if (dir !== 'up') { px(g, '#f8f8fa', 6, 7); px(g, '#f8f8fa', 9, 7); px(g, '#f8f8fa', 7, 8, 2, 1); } px(g, '#e2b040', 4, 9, 8, 2); px(g, shade(robe, 0.45), 5, 11); px(g, shade(robe, 0.45), 10, 8); px(g, robe, 4, 12, 8, 2); px(g, robeD, 4, 13, 8, 1); }
    else if (top.s === 'miko') { if (dir !== 'up') px(g, '#c8303c', 7, 7, 2, 1); px(g, '#c8303c', 4, 10, 8, 2); px(g, '#c8303c', 4, 12, 8, 2); px(g, '#a82030', 4, 13, 8, 1); }
    else if (top.s === 'blazer') { if (dir !== 'up') { px(g, '#f8f8fa', 7, 7, 2, 1); px(g, '#c03040', 7, 8, 2, 3); px(g, '#e2b040', 5, 10); } px(g, robeD, 4, 11, 8, 1); }
    else if (top.s === 'gothic') { if (dir !== 'up') px(g, '#c0202a', 7, 7, 2, 2); px(g, '#c0202a', 4, 10, 8, 1); }
    else if (top.s === 'rainbow') { ['#e8433a', '#f08a30', '#ffcf30', '#4fbf6a', '#3a8ae8'].forEach(function (rc, i) { px(g, rc, 4, 7 + i, 8, 1); }); }
    else if (KN) { // [2026-11-19] 기사단 판금 -- 가슴판 빛, 어깨받이, 허리띠, 정강이 받이 + 쇠장화
      px(g, KN.a2, 4, 7, 8, 5); px(g, KN.a1, 5, 7, 6, 4); px(g, KN.a2, 3, 7, 2, 2); px(g, KN.a2, 11, 7, 2, 2); px(g, KN.a3, 3, 7, 1, 1); px(g, KN.a3, 11, 7, 1, 1);
      if (dir !== 'up') { px(g, KN.a3, 6, 8, 1, 2); px(g, KN.acc, 7, 9, 2, 1); } else px(g, KN.a3, 7, 8, 2, 2);
      px(g, KN.a2, 4, 11, 8, 1); px(g, KN.a3, 7, 11, 2, 1);
      px(g, KN.a2, lx, 12, 2, lh); px(g, KN.a1, lx, 12, 1, lh); px(g, '#2a2e3c', lx, 12 + lh, 2, 1);
      px(g, KN.a2, rx, 12, 2, rh); px(g, KN.a1, rx, 12, 1, rh); px(g, '#2a2e3c', rx, 12 + rh, 2, 1);
    }
    else px(g, belt, 4, 10, 8, 1);
    if (fem) { px(g, robe, 3, 11, 10, 2); px(g, robeD, 3, 12, 10, 1); } // 치마처럼 퍼지는 아랫단
    if (top.s === 'maid' || top.s === 'gothic') px(g, top.s === 'maid' ? '#f8f8fa' : '#c0202a', fem ? 3 : 4, fem ? 12 : 11, fem ? 10 : 8, 1); // [2026-11-06] 프릴 단
    if (fem && (top.s === 'miko' || top.s === 'kimono')) { px(g, top.s === 'miko' ? '#c8303c' : robe, 3, 11, 10, 2); px(g, top.s === 'miko' ? '#a82030' : robeD, 3, 12, 10, 1); }
    if (side) { px(g, robeD, 7, 8, 2, 3); px(g, KN ? KN.a2 : skin, 8, 11); }
    else { px(g, robe, 3, 8, 1, 3); px(g, robe, 12, 8, 1, 3); px(g, KN ? KN.a2 : skin, 3, 11); px(g, KN ? KN.a2 : skin, 12, 11); } // [2026-11-19] 기사단은 쇠장갑
    if (KN && KN.lord && dir === 'up') { px(g, KN.acc, 3, 7, 10, 8); px(g, KN.accD, 3, 14, 10, 1); px(g, KN.accD, 3, 7, 1, 7); px(g, KN.accD, 12, 7, 1, 7); px(g, '#e0b040', 7, 7, 2, 1); } // 뒤에서 보면 망토가 등을 덮는다
    if (KN && !KN.lord && dir === 'down') { px(g, KN.a2, 0, 8, 3, 6); px(g, KN.acc, 1, 9, 1, 4); px(g, KN.a3, 1, 10, 1, 1); } // 단원 방패(왼팔)
    if (KN && !KN.lord && dir === 'up') { px(g, KN.a2, 5, 8, 6, 5); px(g, KN.acc, 7, 9, 2, 3); px(g, KN.a3, 6, 8, 1, 1); } // 뒤에서 보면 등에 멘 방패
    px(g, skin, 4, 1, 8, 6);
    var hs16 = lk.hairStyle || (fem ? 'long' : 'boy'); // [2026-11-20] 머리 모양 -- 16x16은 길이만(+트윈/포니/땋은 머리 꼬리)
    var longHair = !KN && (hs16 === 'long' || hs16 === 'wavy'); // [2026-11-19] 투구를 쓰면 긴 머리는 투구 안으로
    if (dir === 'up') { px(g, hair, 4, 0, 8, 6); px(g, shade(hair, 0.15), 6, 1, 4, 1); if (longHair) px(g, hair, 4, 6, 8, 3); }
    else if (side) { px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 3, longHair ? 6 : 3); px(g, '#1a1a1a', 10, 4); px(g, '#f29b8a', 10, 5); }
    else {
      px(g, hair, 4, 0, 8, 3); px(g, hair, 4, 3, 1, longHair ? 6 : 2); px(g, hair, 11, 3, 1, longHair ? 6 : 2);
      px(g, '#1a1a1a', 6, 4); px(g, '#1a1a1a', 9, 4); px(g, '#f29b8a', 5, 5); px(g, '#f29b8a', 10, 5);
      if (fem) { px(g, '#1a1a1a', 5, 3); px(g, '#1a1a1a', 10, 3); px(g, '#d9536f', 7, 6, 2, 1); } // 속눈썹 + 입술
    }
    if (tailC && dir === 'up') [[8, 11], [9, 12], [10, 12], [11, 11], [12, 10], [12, 9]].forEach(function (q, i) { px(g, i >= 4 ? tailD : tailC, q[0], q[1]); });
    if (!KN) { // [2026-11-20] 머리 모양 꼬리(16x16)
      if (hs16 === 'twin') { if (dir === 'right') px(g, hair, 3, 2, 1, 5); else { px(g, hair, 3, 2, 1, 5); px(g, hair, 12, 2, 1, 5); } }
      if (hs16 === 'ponytail') { if (dir === 'up') px(g, hair, 7, 5, 2, 3); else if (side) px(g, hair, 3, 2, 1, 4); }
      if (hs16 === 'side' && dir !== 'right') px(g, hair, dir === 'up' ? 4 : 11, 5, 1, 4);
      if (hs16 === 'bob' && dir !== 'up') { px(g, hair, 4, 3, 1, 3); px(g, hair, side ? 4 : 11, 3, 1, 3); }
    }
    if (KN) { // [2026-11-19] 기사단 투구 -- 앞: 눈구멍 + 콧대, 옆: 눈구멍(얼굴 쪽), 뒤: 통 투구. 꼭대기 깃
      px(g, KN.a2, 4, 0, 8, 1); // 정수리(머리카락이 투구 위로 안 비치게)
      if (dir === 'up') { px(g, KN.a2, 3, 1, 10, 6); px(g, KN.a1, 4, 1, 8, 5); px(g, KN.a3, 5, 1, 3, 1); px(g, KN.acc, 7, 0, 2, 1); px(g, KN.acc, 7, 1, 2, 3); px(g, KN.accD, 7, 4, 2, 1); }
      else if (side) { px(g, KN.a2, 3, 1, 10, 6); px(g, KN.a1, 4, 1, 8, 5); px(g, KN.a3, 5, 1, 3, 1); px(g, '#12141c', 9, 3, 3, 1); px(g, KN.a2, 11, 4, 1, 2); px(g, KN.acc, 5, 0, 4, 1); px(g, KN.acc, 4, 1, 1, 2); }
      else { px(g, KN.a2, 3, 1, 10, 6); px(g, KN.a1, 4, 1, 8, 5); px(g, KN.a3, 5, 1, 3, 1); px(g, '#12141c', 5, 3, 6, 1); px(g, KN.a2, 7, 4, 2, 3); px(g, KN.acc, 7, 0, 2, 1); px(g, KN.acc, 8, 0, 3, 1); }
    } else if (hat.s) { // 모자는 머리 위에 덮어 그린다
      var hc = hat.c, hd = shade(hc, -0.3);
      if (hat.s === 'straw') { px(g, hc, 2, 2, 12, 1); px(g, hc, 5, 0, 6, 2); px(g, '#b5251f', 5, 1, 6, 1); }
      else if (hat.s === 'helmet') { px(g, hc, 4, 0, 8, 2); px(g, hd, 3, 2, 10, 1); px(g, '#ffe14d', 7, 0, 2, 1); }
      else if (hat.s === 'pirate') { px(g, hc, 3, 1, 10, 2); px(g, hc, 5, 0, 6, 1); px(g, '#ffffff', 7, 1, 2, 1); }
      else if (hat.s === 'wizard') { px(g, hc, 7, 0, 2, 1); px(g, hc, 6, 1, 4, 1); px(g, hd, 3, 2, 10, 1); px(g, '#ffe14d', 8, 1); }
      else if (hat.s === 'crown') { px(g, hc, 4, 1, 8, 1); px(g, hc, 4, 0); px(g, hc, 7, 0, 2, 1); px(g, hc, 11, 0); px(g, '#d4104f', 7, 1, 2, 1); }
      // [2026-10-28] 치장 추가
      else if (hat.s === 'neko') { g.clearRect(3, 0, 10, 1); px(g, hc, 4, 0, 2, 1); px(g, hc, 10, 0, 2, 1); px(g, '#f29bb0', 5, 0); px(g, '#f29bb0', 10, 0); px(g, hd, 6, 1, 4, 1); } // [2026-11-06] 용병 고양이 귀 -- 머리 위로 뾰족 튀어나오게(귀 사이는 비움)
      else if (hat.s === 'cat') { px(g, hc, 4, 0, 2, 2); px(g, hc, 10, 0, 2, 2); px(g, '#f29bb0', 5, 1); px(g, '#f29bb0', 10, 1); }
      else if (hat.s === 'flower') { px(g, '#3a9a4a', 4, 1, 8, 1); px(g, hc, 4, 0); px(g, hc, 7, 0, 2, 1); px(g, hc, 11, 0); px(g, '#ffe14d', 5, 1); px(g, '#ffe14d', 10, 1); }
      else if (hat.s === 'santa') { px(g, hc, 5, 0, 6, 2); px(g, hc, 11, 1); px(g, '#ffffff', 12, 1); px(g, '#ffffff', 4, 2, 8, 1); }
      else if (hat.s === 'horn') { px(g, hc, 4, 0, 1, 2); px(g, hc, 11, 0, 1, 2); px(g, '#ffd84a', 4, 0); px(g, '#ffd84a', 11, 0); }
      // [2026-10-28] 고급 치장 모자
      else if (hat.s === 'rabbit') { px(g, hc, 4, 2, 8, 1); px(g, hc, 5, 0, 1, 2); px(g, hc, 10, 0, 1, 2); px(g, '#f29bb0', 5, 1); px(g, '#f29bb0', 10, 1); }
      else if (hat.s === 'halo') { px(g, hc, 4, 0, 8, 1); px(g, '#ffffff', 6, 0, 2, 1); }
      else if (hat.s === 'beret') { px(g, hc, 3, 1, 9, 2); px(g, hc, 5, 0, 6, 1); px(g, hd, 11, 1); px(g, hd, 7, 0); }
      else if (hat.s === 'ribbon') { px(g, hc, 9, 0, 4, 2); px(g, hd, 10, 0, 2, 2); px(g, '#ffffff', 9, 0); }
      // [2026-11-06] 용병 머리 장식 치장
      else if (hat.s === 'maidband') { px(g, hc, 4, 1, 8, 1); px(g, hc, 5, 0); px(g, hc, 7, 0, 2, 1); px(g, hc, 10, 0); px(g, '#d8d0e0', 4, 1); px(g, '#d8d0e0', 11, 1); }
      else if (hat.s === 'sunhat') { px(g, hc, 1, 2, 14, 1); px(g, hc, 4, 0, 8, 2); px(g, '#4a6ad0', 4, 1, 8, 1); px(g, hd, 1, 2); px(g, hd, 14, 2); }
      else if (hat.s === 'kanzashi') { px(g, hc, 10, 0, 2, 2); px(g, '#f4f0f0', 11, 0); px(g, '#e2b040', 9, 1); px(g, '#e2b040', 8, 2); }
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
  function oneWing(g, p, z, ax, ay, s, flap, rain, R) {
    R = R || 1; // [2026-11-20] 64x64 캐릭터용 -- 같은 날개를 R배 해상도로(점을 촘촘히)
    var P = function (x, y, c) { g.fillStyle = c; g.fillRect(Math.round(x * R), Math.round(y * R), 1, 1); };
    var rot = function (x, y) { var dx = x - ax, dy = y - ay, a = -flap * s, cs = Math.cos(a), sn = Math.sin(a); return [ax + dx * cs - dy * sn, ay + dx * sn + dy * cs]; };
    var arm = function (t) { return [ax + s * z.span * t, ay - z.rise * Math.sin(Math.min(1, t * 1.25) * Math.PI / 2) + (t > 0.8 ? (t - 0.8) * z.rise * 1.2 : 0)]; };
    for (var i = z.n - 1; i >= 0; i--) { // 칼깃 -- 바깥부터(안쪽이 위에 겹친다)
      var t = (i + 0.5) / z.n, b = arm(t), len = z.drop * (0.7 + 0.45 * t), ang = Math.PI / 2 + s * (0.02 - 0.26 * t);
      var dx = Math.cos(ang), dy = Math.sin(ang), w0 = 1.6 + t * 0.9, fp = rain ? wingPal(WING_RAIN[i % WING_RAIN.length]) : p;
      for (var k = 0; k <= len; k += 1 / R) {
        var ww = Math.max(0.6, w0 * (k < len - 2 ? 1 : (len - k + 0.5) / 2.5));
        for (var j = -ww; j <= ww; j += 0.5 / R) { var q = rot(b[0] + dx * k - dy * j, b[1] + dy * k + dx * j); P(q[0], q[1], j > ww - 0.8 ? fp.dk : j < -ww + 0.8 ? fp.hi : k > len - 2 ? fp.mid : fp.base); }
        if (k > 1 && k < len - 1) { var sq = rot(b[0] + dx * k, b[1] + dy * k); P(sq[0], sq[1], fp.line); } // 깃대
      }
    }
    for (var i2 = 0; i2 < z.n + 2; i2++) { // 덮깃
      var t2 = (i2 + 0.3) / (z.n + 2), b2 = arm(t2), l2 = 2 + z.drop * 0.25;
      for (var k2 = 0; k2 <= l2; k2 += 1 / R) for (var j2 = -1.2; j2 <= 1.2; j2 += 0.5 / R) { var q2 = rot(b2[0] - s * 0.3 * k2 + j2, b2[1] + k2); P(q2[0], q2[1], k2 >= l2 - 0.5 ? p.mid : j2 > 0.6 ? p.dk : p.base); }
    }
    for (var t3 = 0; t3 <= 1; t3 += 0.02 / R) { var a3 = arm(t3), q3 = rot(a3[0], a3[1]); P(q3[0], q3[1], p.hi); var q4 = rot(a3[0], a3[1] + 1); P(q4[0], q4[1], p.base); var q5 = rot(a3[0], a3[1] - 1); P(q5[0], q5[1], p.mid); } // 날개뼈
  }
  function wingSprite(stage, side, f, dye, R) {
    R = R || 1;
    var z = WING_SIZE[stage], p = wingPal(dye), ext = z.span + 3, top = z.rise + 8, W = (32 + ext * 2) * R, H = (top + 16 + Math.ceil(z.drop * 1.15) + 6) * R;
    if (side) z = { span: Math.round(z.span * 0.8), rise: Math.round(z.rise * 0.9), drop: Math.round(z.drop * 0.9), n: z.n };
    var c = mk(W, H), g = c.getContext('2d'), fl = WING_FLAP[f % 4], sy = top + 16, rain = dye === 'rainbow';
    if (side) oneWing(g, p, z, ext + 13, sy, -1, fl, rain, R);
    else { oneWing(g, p, z, ext + 10, sy, -1, fl, rain, R); oneWing(g, p, z, ext + 21, sy, 1, fl, rain, R); }
    outline(c, '#3a3050');
    if (stage >= 5) [[0.1, 0.2], [0.9, 0.25], [0.05, 0.6], [0.95, 0.55], [0.3, 0.05], [0.7, 0.08]].forEach(function (q, i) { // 5단계 빛 알갱이
      g.fillStyle = i % 2 ? '#7ff0ff' : '#ffffff'; g.fillRect(Math.round(q[0] * W), Math.round(q[1] * H) - (f % 2) * R, R, R);
      g.fillStyle = p.hi; g.fillRect(Math.round(q[0] * W) + R, Math.round(q[1] * H) - (f % 2) * R, R, R);
    });
    return { c: c, ext: ext, top: top, R: R }; // ext/top은 32 기준(그림은 R배)
  }
  var wingCache = {};
  /** 방향에 맞는 날개(캐시). dir = down/up(양쪽) / right(등 쪽 하나) / left(뒤집기). */
  function wings(stage, dir, f, dye) {
    stage = Math.max(1, Math.min(5, stage | 0)); f = (f | 0) % 4; dye = dye || '';
    var R = HD_ON ? 2 : 1, side = dir === 'left' || dir === 'right', key = stage + (side ? dir : 'full') + f + dye + 'R' + R;
    if (wingCache[key]) return wingCache[key];
    var w = wingSprite(stage, side, f, dye, R);
    if (dir === 'left') { var fc = mk(w.c.width, w.c.height), fg = fc.getContext('2d'); fg.translate(fc.width, 0); fg.scale(-1, 1); fg.drawImage(w.c, 0, 0); w = { c: fc, ext: w.ext, top: w.top, R: w.R }; }
    wingCache[key] = w;
    return w;
  }
  /** 날개 + 캐릭터를 한 장으로(32x32보다 크다). 환생 안 했으면 그냥 캐릭터. */
  function withWings(dir, step, dig, lk) {
    var rb = lk ? lk.rebirth | 0 : 0, ch = charSprite(dir, step, dig, lk);
    if (rb < 1) return ch;
    var w = wings(rb, dir, 0, lk.wing), c = mk(w.c.width, w.c.height), g = c.getContext('2d'), R = w.R || 1;
    g.drawImage(w.c, 0, 0);
    g.drawImage(ch, w.ext * R, w.top * R, 32 * R, 32 * R); // [2026-11-20] 64x64면 날개도 2배

    return c;
  }
  /** 정면(서 있는 모습) 그림 data URL -- 외형이 같으면 한 번만 그린다.
   *  [2026-10-28] wings = true면 환생 날개까지 그린 그림(환생했으면 16x16보다 넓다 -- 쓰는 곳이 가로세로 비율을 지켜야 함, wide(lk)로 확인). */
  function dataUrl(lk, wingsOn) {
    var key = (wingsOn ? 'w' : '') + (HD_ON ? 'H' : '') + JSON.stringify(lk || {}); // [2026-11-20] 64x64면 따로
    if (!urlCache[key]) urlCache[key] = (wingsOn ? withWings('down', 0, 0, lk) : charSprite('down', 0, 0, lk)).toDataURL();
    return urlCache[key];
  }
  /** 날개 때문에 그림이 16x16보다 넓은지(환생 1 이상). */
  function wide(lk) { return !!(lk && (lk.rebirth | 0) > 0); }
  // ---- [2026-11-06] "용병 도트 해상도를 32x32 정도로" -- 용병(고양이 소녀) 전용 32x32 그림. lk = { hair '#색', job 0 전사 / 1 궁수 / 2 마법사, weapon }.
  //  얼굴(눈·하이라이트·볼터치·입), 들쭉날쭉한 앞머리, 긴 뒷머리, 안쪽이 분홍인 고양이 귀, 꼬리, 직업 옷(전사 은빛 흉갑+견갑 / 궁수 초록 튜닉+화살통 끈 / 마법사 금테 별빛 로브).
  //  방향 down / right / up(left = right 뒤집기), step 0~2 = 걸음, dig 3/4 = 공격(무기는 gear32 그대로 32 격자에 겹친다).
  var NEKO_JOB = [
    { o: '#c8d0dc', od: '#8a96a6', ol: '#ffffff', b: '#8a2a3a', bd: '#5a1a26', leg: '#8a96a6', shoe: '#4a3a2a', belt: '#7a4a1f' },
    { o: '#3f8a4a', od: '#2a6a34', ol: '#6ac87a', b: '#5a4a2a', bd: '#3a2e1a', leg: '#f6d2b0', shoe: '#5b3a1e', belt: '#7a4a1f' },
    { o: '#5a3aa0', od: '#3a2a7a', ol: '#8a6ad0', b: '#5a3aa0', bd: '#3a2a7a', leg: '#2a1a3a', shoe: '#2a1a3a', belt: '#ffd84a' }
  ];
  function nekoSprite(dir, step, dig, lk) {
    lk = lk || {};
    if (dir === 'left') {
      var src = nekoSprite('right', step, dig, lk), c2 = mk(src.width, src.height), g2 = c2.getContext('2d');
      g2.translate(src.width, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
      return c2;
    }
    var c = mk(32, 32), g = c.getContext('2d'), J0 = NEKO_JOB[lk.job | 0] || NEKO_JOB[0];
    var H = lk.hair || '#2b1d16', HD = shade(H, -0.3), HL = shade(H, 0.35), S = '#f6d2b0', SD = '#e2b08a', E = '#3a2340', EI = '#6a4aa0', PK = '#ff9aa8', IN = '#f29bb0';
    var side = dir === 'right', up = dir === 'up', st = step | 0;
    var P = function (col, x, y, w, h) { px(g, col, x, y, w, h); };
    var tail = function (pts) { pts.forEach(function (q, i) { P(i >= pts.length - 2 ? HL : i % 3 === 2 ? HD : H, q[0], q[1], 2, 2); }); };
    var ear = function (x0, flip) { // 귀 -- 아래 폭 5칸, 꼭짓점 위
      [[1, 0, 1], [1, 1, 2], [0, 2, 3], [0, 3, 4], [0, 4, 5]].forEach(function (r) { P(H, flip ? x0 + 4 - r[0] - r[2] + 1 : x0 + r[0], r[1], r[2], 1); });
      P(IN, flip ? x0 + 2 : x0 + 2, 2, 1, 2); P(IN, flip ? x0 + 1 : x0 + 3, 3, 1, 1);
    };
    // 1) 몸 뒤 -- 꼬리, 긴 뒷머리
    if (!up) tail(side ? [[8, 24], [6, 23], [5, 21], [4, 19], [4, 17], [5, 15]] : [[23, 24], [25, 23], [26, 21], [27, 19], [27, 17], [26, 15]]);
    if (side) P(H, 9, 10, 5, 14);
    else if (!up) { P(H, 7, 12, 3, 12); P(H, 22, 12, 3, 12); P(HD, 7, 23, 3, 1); P(HD, 22, 23, 3, 1); }
    // 2) 다리/신발(걸음)
    var lu = st === 1 ? 1 : 0, ru = st === 2 ? 1 : 0;
    if (side) {
      var fx = st === 1 ? 17 : st === 2 ? 15 : 16, bx = st === 1 ? 13 : st === 2 ? 15 : 14;
      P(J0.leg, bx, 26, 2, 4); P(J0.shoe, bx, 30, 3, 2);
      P(J0.leg, fx, 26, 2, 4); P(J0.shoe, fx, 30, 3, 2);
    } else {
      P(J0.leg, 12, 26 - lu, 2, 4); P(J0.shoe, 11, 30 - lu, 4, 2);
      P(J0.leg, 18, 26 - ru, 2, 4); P(J0.shoe, 17, 30 - ru, 4, 2);
    }
    // 3) 치마/로브 아랫단
    var skirt = function (x0, w0, rows) { for (var r = 0; r < rows; r++) P(r === rows - 1 ? J0.bd : J0.b, x0 - (r >> 1), 23 + r, w0 + (r >> 1) * 2, 1); };
    if (side) skirt(12, 8, (lk.job | 0) === 2 ? 6 : 4);
    else skirt(11, 10, (lk.job | 0) === 2 ? 6 : 4);
    // 4) 몸통 + 팔
    if (side) {
      P(J0.o, 12, 17, 8, 6); P(J0.od, 12, 17, 1, 6);
      P(J0.belt, 12, 22, 8, 1);
      P(J0.od, 16, 17, 3, 6); P(J0.o, 17, 17, 2, 5); P(S, 17, 23, 2, 1); // 앞팔 + 손
    } else {
      P(J0.o, 11, 17, 10, 6); P(J0.od, 11, 17, 1, 6); P(J0.od, 20, 17, 1, 6);
      P(J0.belt, 11, 22, 10, 1);
      P(J0.o, 9, 17, 2, 6); P(J0.o, 21, 17, 2, 6); P(J0.od, 9, 22, 2, 1); P(J0.od, 21, 22, 2, 1);
      P(S, 9, 23, 2, 1); P(S, 21, 23, 2, 1);
    }
    var job = lk.job | 0;
    if (!up) {
      if (job === 0) { // 흉갑 광택 + 견갑
        P(J0.ol, side ? 14 : 13, 18, side ? 3 : 6, 1); P(J0.od, side ? 13 : 12, 20, side ? 6 : 8, 1);
        if (side) P('#e8eef6', 15, 16, 4, 2); else { P('#e8eef6', 8, 16, 4, 2); P('#e8eef6', 20, 16, 4, 2); }
      } else if (job === 1) { // 흰 깃 + 화살통 끈
        P('#e8e0c0', side ? 15 : 14, 17, side ? 3 : 4, 1);
        if (!side) for (var k = 0; k < 6; k++) P('#5a3a1a', 12 + k + (k >> 1), 17 + k, 1, 1);
      } else { // 금 깃 + 가운데 금테 + 별
        P('#ffd84a', side ? 15 : 13, 17, side ? 4 : 6, 1); if (!side) P('#ffd84a', 15, 18, 2, 10);
        P('#ffe14d', side ? 14 : 12, 20, 1, 1); P('#ffffff', side ? 18 : 19, 24, 1, 1); P('#ffe14d', side ? 13 : 18, 26, 1, 1);
      }
    } else if (job === 1) for (var q = 0; q < 6; q++) P('#7a4a1f', 19 - q, 17 + q, 1, 1); // 등의 화살통 끈
    // 5) 머리
    if (up) { // 뒷모습 -- 머리카락이 머리와 등을 덮는다
      P(H, 8, 4, 16, 13); P(H, 9, 3, 14, 1); P(H, 9, 17, 14, 6); P(HD, 9, 22, 14, 1);
      for (var u = 0; u < 4; u++) P(HD, 11 + u * 3, 8 + (u % 2) * 3, 1, 12);
      P(HL, 12, 5, 6, 1);
      ear(7, false); ear(20, true);
      tail([[17, 25], [19, 25], [21, 24], [22, 22], [22, 20], [21, 18]]);
      return nekoFinish(c, dir, step, dig, lk);
    }
    if (side) {
      P(S, 14, 7, 9, 10); P(SD, 14, 16, 8, 1); P(S, 16, 16, 2, 1); // 얼굴(오른쪽)
      P(H, 10, 3, 13, 5); P(H, 9, 5, 6, 12); P(HD, 9, 15, 5, 2); // 정수리 + 뒤통수
      for (var bx2 = 15; bx2 <= 22; bx2++) P(H, bx2, 7, 1, 2 + (bx2 * 7 % 3 === 0 ? 1 : 0)); // 앞머리
      P(H, 14, 7, 2, 8); P(HL, 12, 4, 6, 1);
      ear(9, false); ear(16, true);
      P(E, 19, 11, 2, 3); P(EI, 19, 13, 2, 1); P('#ffffff', 19, 11, 1, 1);
      P(PK, 21, 14, 1, 1); P('#c0505a', 22, 15, 1, 1);
      return nekoFinish(c, dir, step, dig, lk);
    }
    P(S, 9, 8, 14, 9); P(S, 10, 16, 12, 1); P(SD, 11, 16, 10, 1); P(S, 15, 16, 2, 1); // 얼굴 + 목
    P(H, 9, 3, 14, 5); P(H, 8, 5, 16, 3); P(H, 8, 8, 2, 9); P(H, 22, 8, 2, 9); // 정수리 + 옆머리
    for (var x = 10; x <= 21; x++) P(H, x, 8, 1, 1 + ((x * 7) % 3)); // 들쭉날쭉한 앞머리
    P(HD, 8, 15, 2, 2); P(HD, 22, 15, 2, 2); P(HL, 12, 4, 7, 1); P(HL, 11, 5, 2, 1);
    ear(7, false); ear(20, true);
    P(E, 11, 11, 2, 3); P(E, 19, 11, 2, 3); P(EI, 11, 13, 2, 1); P(EI, 19, 13, 2, 1); P('#ffffff', 11, 11, 1, 1); P('#ffffff', 19, 11, 1, 1); // 눈
    P(PK, 9, 14, 2, 1); P(PK, 21, 14, 2, 1); P('#c0505a', 15, 15, 2, 1); // 볼터치 + 입
    return nekoFinish(c, dir, step, dig, lk);
  }
  function nekoFinish(c, dir, step, dig, lk) {
    outline(c, '#140d0a');
    var wl = mk(32, 32), fl = mk(32, 32);
    FX = fl.getContext('2d');
    var drew = gear32(wl.getContext('2d'), dir, step, dig, lk);
    FX = null;
    if (drew) { var g = c.getContext('2d'); g.drawImage(outline(wl, '#140d0a'), 0, 0); g.drawImage(fl, 0, 0); }
    return c;
  }
  // [2026-11-06] "초상화 기반으로 용병 캐릭터를 다시, 32x32보다 높은 해상도(64x64)" -- 64칸 좌표로 모양(머리 모양/귀/옷/장식/무기)을 정의하고
  //  칸마다 그 자리의 맨 위 모양 색을 칠한 뒤, 머리 윤기/그늘, 안쪽 경계, 눈(픽셀 단계), 바깥 외곽선을 입힌다. d = 용병 한 명의 모습(_tower_mine.jsp MERC_HD).
  //  방향: down(정면) / up(뒤) / right(옆, left는 뒤집기), step 1/2 = 걸음, dig 3/4 = 공격(무기 휘두르기 / 시위 당기기 / 지팡이 들기).
  var hdC = {};
  function hdHex(c) { c = c.replace('#', ''); return [0, 2, 4].map(function (i) { return parseInt(c.substr(i, 2), 16); }); }
  function hdShade(c, k) { return '#' + hdHex(c).map(function (v) { v = Math.round(k < 0 ? v * (1 + k) : v + (255 - v) * k); v = Math.max(0, Math.min(255, v)); return ('0' + v.toString(16)).slice(-2); }).join(''); }
  function hdEll(cx, cy, rx, ry) { return function (u, v) { var a = (u - cx) / rx, b = (v - cy) / ry; return a * a + b * b <= 1; }; }
  function hdRect(x0, y0, x1, y1) { return function (u, v) { return u >= x0 && u <= x1 && v >= y0 && v <= y1; }; }
  function hdTri(ax, ay, bx, by, cx, cy) {
    return function (u, v) {
      var d1 = (u - bx) * (ay - by) - (ax - bx) * (v - by), d2 = (u - cx) * (by - cy) - (bx - cx) * (v - cy), d3 = (u - ax) * (cy - ay) - (cx - ax) * (v - ay);
      return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
    };
  }
  function hdTrap(y0, y1, hw0, hw1, cx) { return function (u, v) { if (v < y0 || v > y1) return false; var t = (v - y0) / (y1 - y0); return Math.abs(u - cx) <= hw0 + (hw1 - hw0) * t; }; }
  function hdAnd(a, b) { return function (u, v) { return a(u, v) && b(u, v); }; }
  function hdOr() { var fs = arguments; return function (u, v) { for (var i = 0; i < fs.length; i++) if (fs[i](u, v)) return true; return false; }; }
  function hdSaw(p, amp) { return function (u) { var t = (u / p) % 1; if (t < 0) t += 1; return amp * Math.abs(t * 2 - 1); }; }
  function hdRot(f, px0, py0, ang) { var c = Math.cos(-ang), s = Math.sin(-ang); return function (u, v) { var x = u - px0, y = v - py0; return f(px0 + x * c - y * s, py0 + x * s + y * c); }; }
  function hdMove(f, dx, dy) { return function (u, v) { return f(u - dx, v - dy); }; }
  function hdBuild(d, dir, step, dig) {
    var L = [], add = function (g, col, f) { L.push({ g: g, c: col, f: f }); };
    var side = dir === 'right', back = dir === 'up', front = !side && !back;
    var H = d.hair, S = '#fde3cf', T = d.top, K = d.skirt || T, A = d.acc || '#d03040', st = d.style, W = '#f8f8fa';
    var cx = side ? 32.5 : 32, head = hdEll(32, 21, 14.5, 13.8), job = d.job | 0;
    var hk = function (n) { if (d.hook && d.hook[n]) d.hook[n](add, { side: side, back: back, front: front, cx: cx, step: step | 0 }); }; // [2026-11-20] 플레이어 코스튬 갈고리(back/body/head)
    var jag = function (bot, p, a) { return function (u, v) { return v < bot - hdSaw(p, a)(u); }; };
    // -- 머리 뒤쪽(정면/옆: 몸 뒤, 뒤: 몸 앞에 다시 그림)
    // [2026-11-20] "머리 스타일 골고루 추가" -- 짧은 머리 / 삐죽 머리 / 단정한 단발 / 보브컷 / 긴 생머리 / 웨이브 / 트윈테일 / 포니테일 / 올림머리 / 옆으로 땋은 머리
    var shortS = st === 'boy' || st === 'spiky' || st === 'ponytail', tieC = d.tieC || '#e84a6a';
    var backHair = function () {
      if (st === 'long') add('hair', H, hdAnd(side ? hdEll(27, 31, 13.5, 21.5) : hdEll(32, 31, 17, 22), jag(51, 5, 3)));
      if (st === 'wavy') add('hair', H, hdAnd(side ? hdEll(26.5, 31, 14.5, 22.5) : hdEll(32, 31, 18.6, 22.5), function (u, v) { return v < 53 - Math.abs(Math.sin(u * 0.9)) * 4 && !(v > 34 && Math.abs(Math.sin(v * 0.55 + u * 0.2)) < 0.08); }));
      if (st === 'spiky') add('hair', H, side ? hdEll(29.5, 22.5, 14.8, 12) : hdEll(32, 22, 16, 11.8));
      if (st === 'ponytail') {
        add('hair', H, side ? hdEll(29.5, 22.5, 14.8, 12) : hdEll(32, 22, 16, 11.8));
        if (side) { add('hair', H, hdOr(hdEll(18.5, 17.5, 4, 3.6), hdEll(15.8, 25, 3.6, 6.4), hdAnd(hdEll(15.4, 34, 3, 6.4), jag(40, 2.5, 1.5)))); add('acc', tieC, hdEll(19.5, 17, 1.6, 2.2)); }
        if (back) { add('hair', H, hdOr(hdEll(32, 14, 4.2, 3.6), hdEll(32, 23, 3.8, 7), hdAnd(hdEll(32, 33, 3.2, 6.6), jag(40, 2.5, 1.5)))); add('acc', tieC, hdEll(32, 16.4, 2.4, 1.4)); }
      }
      if (st === 'side') add('hair', H, side ? hdEll(29.5, 24, 15, 12.5) : hdEll(32, 24, 16.5, 12.8));
      if (st === 'twin') {
        if (side) add('hair', H, hdAnd(hdEll(18.5, 37, 5.5, 15), jag(51, 4, 2)));
        else { add('hair', H, hdAnd(hdEll(13.5, 37, 5.5, 15), jag(51, 4, 2))); add('hair', H, hdAnd(hdEll(50.5, 37, 5.5, 15), jag(51, 4, 2))); }
      }
      if (st === 'boy') add('hair', H, side ? hdEll(29.5, 22.5, 14.8, 12) : hdEll(32, 22, 16, 11.8)); // [2026-11-20] 플레이어 남자 -- 짧은 머리
      if (st === 'short' || st === 'bob' || st === 'twin' || st === 'bun') add('hair', H, side ? hdEll(29.5, 24, 15, 12.5) : hdEll(32, 24, 16.5, st === 'bob' ? 13.5 : 12.5));
      if (st === 'bun') add('hair', H, side ? hdEll(24.5, 8, 6, 5.2) : hdEll(32, 6.5, 6.5, 5.5));
    };
    hk('back');
    if (!back) backHair();
    // -- 무기(뒤 모습은 몸 뒤)
    var hand = side ? [35.6, 48] : back ? [22.6, 48] : [41.4, 48], wx = hand[0] + (side ? 1.6 : back ? -1.6 : 3.2);
    var weapon = function () {
      if (job === 0) {
        var ang = dig === 3 ? -0.7 : dig === 4 ? 1.1 : 0, R = function (f) { return ang ? hdRot(f, hand[0], hand[1], back ? -ang : ang) : f; };
        add('blade', '#e6edf5', R(hdOr(hdTri(wx - 1, 16, wx + 1, 16, wx, 12.5), hdRect(wx - 1, 16, wx + 1, 45))));
        add('blade', '#ffffff', R(hdRect(wx - 1, 17, wx - 0.2, 44)));
        add('guard', '#e2b040', R(hdRect(wx - 3.6, 45, wx + 3.6, 46.8)));
        add('grip', '#6a3a24', R(hdRect(wx - 0.8, 46.8, wx + 0.8, 51.5)));
      } else if (job === 1) {
        var bx = side ? 26 : back ? 38 : 33, dirX = back ? -1 : 1, pull = dig === 3 ? 3 : 0;
        add('bow', '#8a5a2a', function (u, v) { var r = Math.hypot(u - bx, v - 44), a = Math.atan2(v - 44, (u - bx) * dirX); return Math.abs(r - 15) <= 1.1 && Math.abs(a) <= 1.0; });
        add('string', '#f4ead0', function (u, v) { var sx = bx + dirX * (15 * Math.cos(1.0) - pull * (1 - Math.abs(v - 44) / (15 * Math.sin(1.0)))); return Math.abs(u - sx) <= 0.45 && Math.abs(v - 44) <= 15 * Math.sin(1.0); });
        if (dig === 3) add('blade', '#e6edf5', hdRect(Math.min(bx + dirX * 5, bx + dirX * 18), 43.6, Math.max(bx + dirX * 5, bx + dirX * 18), 44.4));
      } else {
        var up = dig ? -3 : 0;
        add('grip', '#7a4a2a', hdRect(wx - 0.9, 18 + up, wx + 0.9, 60 + up));
        add('orb', d.orb || A, hdEll(wx, 15.5 + up, dig ? 4.6 : 3.8, dig ? 4.6 : 3.8));
        add('orbHi', '#ffffff', hdEll(wx - 1.1, 14.3 + up, 1.1, 1.1));
      }
    };
    if (back && !d.noWeapon) weapon();
    // -- 다리/신발(걸음)
    var LG = d.pants || d.legs || S, SH = d.shoes || '#3a2a30', ly = d.pants ? 45 : 52; // [2026-11-20] 바지면 다리가 허리 아래부터
    if (side) {
      var fo = step === 1 ? 2.2 : step === 2 ? -2.2 : 0;
      add('leg', LG, hdOr(hdRect(29.4 - fo, ly, 32.2 - fo, 60), hdRect(32.8 + fo, ly, 35.6 + fo, 60)));
      add('shoe', SH, hdOr(hdEll(31.6 - fo, 61, 3.4, 2.1), hdEll(35 + fo, 61, 3.4, 2.1)));
    } else {
      var l1 = step === 1 ? 1.6 : 0, r1 = step === 2 ? 1.6 : 0;
      add('leg', LG, hdOr(hdRect(27.3, ly, 30.2, 60 - l1), hdRect(33.8, ly, 36.7, 60 - r1)));
      add('shoe', SH, hdOr(hdEll(28.6, 61 - l1, 3.2, 2.1), hdEll(35.4, 61 - r1, 3.2, 2.1)));
    }
    // -- 치마/기모노 아랫단, 프릴
    var sk = side ? [8.5, 11.5] : [9.5, 13.5];
    if (d.kimono) add('skirt', K, hdTrap(44, 59, side ? 8.5 : 9.5, side ? 10 : 11.5, cx));
    else if (d.pants) add('skirt', K, hdTrap(44, 47.6, sk[0], sk[0] + 1.6, cx)); // [2026-11-20] 바지 -- 웃옷 아랫단만
    else add('skirt', K, hdTrap(44, 53.5, sk[0], sk[1], cx));
    if (d.frill) add('frill', d.frillCol || W, hdAnd(hdTrap(44, 54.5, sk[0], sk[1] + 1, cx), function (u, v) { return v > 52.2 - hdSaw(2.4, 1.2)(u); }));
    // -- 몸통/팔/손
    add('top', T, hdTrap(34.5, 46, side ? 6.5 : 7.5, side ? 8 : 9.5, cx));
    if (front) {
      if (d.collar) add('collar', d.collar, hdOr(hdTri(24.5, 34.5, 32, 34.5, 32, 40), hdTri(39.5, 34.5, 32, 34.5, 32, 40)));
      if (d.shirt) add('shirt', d.shirt, hdTri(28.5, 34.5, 35.5, 34.5, 32, 42));
      if (d.apron) add('apron', W, hdOr(hdRect(27.5, 41.5, 36.5, 52), hdTrap(38, 41.5, 3.5, 4.5, 32)));
    }
    if (side && d.apron) add('apron', W, hdRect(35, 41.5, 40.5, 52));
    if (d.obi) add('obi', d.obi, hdRect(cx - 8.4, 42.5, cx + 8.4, 45.5));
    if (d.belt) add('obi', d.belt, hdRect(cx - 8.2, 44.5, cx + 8.2, 45.6));
    if (back && d.apron) add('apron', W, hdOr(hdTri(26.5, 41, 26.5, 46, 32, 43.5), hdTri(37.5, 41, 37.5, 46, 32, 43.5)));
    var arm = side ? [hdTrap(36, 46.5, 2.2, 2.8, 34.6)] : [hdTrap(36, 46.5, 2.2, 2.8, 22.6), hdTrap(36, 46.5, 2.2, 2.8, 41.4)];
    add('top', T, hdOr.apply(null, arm));
    if (d.sleeve) add('shirt', d.sleeve, side ? hdRect(31.8, 44.5, 37.4, 46.6) : hdOr(hdRect(20, 44.5, 25.2, 46.6), hdRect(38.8, 44.5, 44, 46.6)));
    add('skin', S, side ? hdEll(35.6, 48, 2.2, 2) : hdOr(hdEll(22.6, 48, 2.2, 2), hdEll(41.4, 48, 2.2, 2)));
    if (front && !d.noAcc) {
      if (d.tie) add('acc', A, hdOr(hdRect(31.2, 36.5, 32.8, 43), hdTri(30.8, 42.5, 33.2, 42.5, 32, 44.5)));
      else add('acc', A, hdOr(hdTri(27.5, 35, 27.5, 39.5, 32, 37.2), hdTri(36.5, 35, 36.5, 39.5, 32, 37.2), hdEll(32, 37.2, 1.5, 1.4)));
    } else if (side && !d.noAcc) add('acc', A, hdOr(hdTri(36, 35, 36, 39.5, 39.5, 37.2), hdEll(38.6, 37.2, 1.4, 1.4)));
    if (!back && !d.noWeapon) weapon(); // [2026-11-20] 플레이어는 손에 든 것을 따로(gear32)
    hk('body');
    if (st === 'side' && !side) { // [2026-11-20] 옆으로 땋은 머리 -- 어깨 앞으로 늘어진 땋은 머리(앞: 오른쪽 어깨, 뒤: 왼쪽 등)
      var bx0 = back ? 20 : 44, bs = back ? -1 : 1;
      add('hair', H, hdOr(hdEll(bx0, 30, 2.8, 2.8), hdEll(bx0 + 0.6 * bs, 34.6, 2.6, 2.6), hdEll(bx0 + 1 * bs, 39.2, 2.4, 2.4), hdEll(bx0 + 1.2 * bs, 43.6, 2.2, 2.2)));
      add('acc', tieC, hdEll(bx0 + 1.3 * bs, 46.6, 1.8, 1.3));
    }
    // -- 머리
    add('skin', S, hdRect(29.3, 32, 34.7, 35.5));
    add('hair', H, head);
    if (back) backHair();
    if (st === 'spiky') add('hair', H, side ? hdOr(hdTri(18, 14, 21, 3, 27, 10), hdTri(24, 9, 28, 0.6, 33, 8), hdTri(31, 8.5, 36, 0.4, 40, 9), hdTri(37, 10, 43, 3, 44, 13)) : hdOr(hdTri(16.5, 15, 19.5, 3.5, 26, 10), hdTri(23.5, 9.5, 27.5, 0.6, 32.5, 8.5), hdTri(31.5, 8.5, 36.5, 0.4, 40.5, 9.5), hdTri(38, 10, 44.5, 3.5, 47.5, 15))); // 삐죽 머리
    if (st === 'boy') add('hair', H, side ? hdOr(hdTri(20, 12, 24, 3.5, 29, 10), hdTri(27, 9, 31, 1.5, 36, 8.5), hdTri(34, 9, 40, 3, 42, 11)) : hdOr(hdTri(19, 13, 22, 4, 28, 9.5), hdTri(26, 9, 31, 1.2, 36, 8.5), hdTri(35, 9, 41, 3.5, 45, 13))); // 정수리 삐침
    if (front) {
      add('skin', S, hdAnd(hdEll(32, 25.5, 12.2, 10.3), function (u, v) { return v > 19.2 + hdSaw(5.2, 3.6)(u + 1.3); }));
      if (shortS) add('hair', H, hdOr(hdAnd(hdRect(17.4, 19, 20.6, 28.5), hdEll(19, 23, 3, 7)), hdAnd(hdRect(43.4, 19, 46.6, 28.5), hdEll(45, 23, 3, 7)))); else // 짧은 머리들은 옆머리도 짧게
      add('hair', H, hdOr(hdAnd(hdRect(17.4, 19, 20.6, 34), hdEll(19, 25, 3, 10)), hdAnd(hdRect(43.4, 19, 46.6, 34), hdEll(45, 25, 3, 10))));
      if (st === 'bob') add('hair', H, hdAnd(head, function (u, v) { return v < 21.5 && v > 9; }));
    } else if (side) {
      add('skin', S, hdAnd(hdEll(36.5, 25.5, 10, 10.3), function (u, v) { return u > 31.5 && v > 19.2 + hdSaw(5.2, 3.6)(u + 1.3); }));
      add('skin', S, hdAnd(hdEll(40.5, 27, 6.6, 8.6), function (u, v) { return v > 21; })); // 볼/턱이 앞으로
      add('hair', H, shortS ? hdAnd(hdRect(28, 18, 32, 28), hdEll(30, 23, 3, 6)) : hdAnd(hdRect(28, 18, 32, 34), hdEll(30, 26, 3, 9.5))); // 귀 앞 옆머리
      if (st === 'bob') add('hair', H, hdAnd(head, function (u, v) { return v < 21.5 && v > 9 && u < 44; }));
    }
    if (d.ears) {
      var E1 = side ? hdTri(17.5, 15, 21, 2.5, 28.5, 9.5) : hdTri(16.6, 15, 19.5, 2.5, 27.5, 9.5), E2 = side ? hdTri(31, 10, 36, 1.5, 42, 8.5) : hdTri(47.4, 15, 44.5, 2.5, 36.5, 9.5);
      add('hair', d.earC || H, hdOr(E1, E2)); // [2026-11-20] 플레이어 고양이 귀 모자는 모자 색
      if (!back) add('earIn', '#f6a8b8', side ? hdTri(34, 8.6, 36, 4.6, 39.4, 8) : hdOr(hdTri(19.6, 11.5, 20.6, 6, 24.4, 9.6), hdTri(44.4, 11.5, 43.4, 6, 39.6, 9.6)));
    }
    if (d.band) add('band', d.bandCol || W, hdAnd(hdEll(32, 21, 15.2, 14.5), hdAnd(function (u, v) { return !hdEll(32, 22.5, 14.2, 13)(u, v); }, function (u, v) { return v < 13 && v > 5; })));
    if (d.orn && !back) add('acc', d.orn, side ? hdEll(27, 12.5, 2.2, 2) : hdEll(20.5, 12.5, 2.2, 2));
    if (d.orn && back) add('acc', d.orn, hdEll(43.5, 12.5, 2.2, 2));
    if (st === 'bun' && d.orn2) add('acc', d.orn2, side ? hdOr(hdEll(20.5, 6, 2, 2), hdEll(28.5, 5, 1.8, 1.8)) : hdOr(hdEll(27, 4.5, 2, 2), hdEll(37, 5, 1.8, 1.8)));
    if (d.hat) {
      add('hat', W, hdEll(32, 11.5, side ? 19 : 21, 4.2));
      add('hat', W, hdAnd(hdEll(side ? 31 : 32, 9, 11.5, 8), function (u, v) { return v <= 11.5; }));
      add('acc', A, hdAnd(hdEll(side ? 31 : 32, 9, 11.6, 8), function (u, v) { return v > 7.6 && v <= 10.2; }));
    }
    if (d.beret) add('hat', d.beret, hdOr(hdAnd(hdEll(side ? 30 : 32, 10, 15.5, 7.5), function (u, v) { return v <= 12.5; }), hdEll(side ? 30 : 32, 3.4, 1.4, 1.4)));
    hk('head');
    return L;
  }
  // ---- [2026-11-20] 플레이어 고해상도(64x64, 애니메이션풍) -- "캐릭터를 더 애니메이션 캐릭터처럼, 진짜 고해상도, 64x64, 옵션에서 고르게, 코스튬 전체(날개 포함)" ----
  //  용병 64x64 그리기(hdBuild/nekoHD)에 플레이어 외형(성별/머리색/옷 색/모자·옷 모양/신발/꼬리/기사단)을 옮긴다. 코스튬은 hdBuild의 갈고리(d.hook: back/body/head)로 얹는다.
  //  손에 든 것(무기/곡괭이/양산/활/지팡이)은 지금 32 격자 그림(gear32)을 Scale2x로 다듬어 손 자리에 겹친다. 날개는 2배 해상도로 다시 그린다(wings(..., R=2)).
  //  MineChar.setHD(true)면 sprite()/withWings()/wings()가 이 그림을 돌려준다(쓰는 곳은 같은 칸 크기로 그리므로 배치는 그대로).
  var HD_ON = false, heroC = {};
  function scale2x(src) {
    var w = src.width, h = src.height, d = src.getContext('2d').getImageData(0, 0, w, h).data;
    var out = mk(w * 2, h * 2), g = out.getContext('2d'), od = g.createImageData(w * 2, h * 2), o = od.data;
    var px2 = function (x, y) { x = Math.max(0, Math.min(w - 1, x)); y = Math.max(0, Math.min(h - 1, y)); var i = (y * w + x) * 4; return (d[i] << 24 | d[i + 1] << 16 | d[i + 2] << 8 | d[i + 3]) >>> 0; };
    var put = function (x, y, v) { var i = (y * w * 2 + x) * 4; o[i] = v >>> 24 & 255; o[i + 1] = v >>> 16 & 255; o[i + 2] = v >>> 8 & 255; o[i + 3] = v & 255; };
    for (var y = 0; y < h; y++) for (var x = 0; x < w; x++) {
      var P0 = px2(x, y), A = px2(x, y - 1), B = px2(x + 1, y), C = px2(x - 1, y), D = px2(x, y + 1), e0 = P0, e1 = P0, e2 = P0, e3 = P0;
      if (A !== D && C !== B) { if (C === A) e0 = A; if (A === B) e1 = B; if (C === D) e2 = C; if (D === B) e3 = B; }
      put(x * 2, y * 2, e0); put(x * 2 + 1, y * 2, e1); put(x * 2, y * 2 + 1, e2); put(x * 2 + 1, y * 2 + 1, e3);
    }
    g.putImageData(od, 0, 0);
    return out;
  }
  var HSTAR = function (x, y, r) { return hdOr(hdRect(x - r, y - 0.55, x + r, y + 0.55), hdRect(x - 0.55, y - r, x + 0.55, y + r)); };
  var HCLIP = function (f, y0, y1) { return hdAnd(f, function (u, v) { return v >= y0 && v <= y1; }); };
  // ---- [2026-11-21] 갑옷 치장(top 모양 armor) 64x64 -- "더 웅장하게": 색으로 셋을 나눈다.
  //  검은 갑옷(흑철) = 층층 어깨받이 + 가시, 가슴 문장, 붉은 망토 / 황금 갑옷 = 흰금 테두리, 붉은 보석, 왕실 망토 / 무사 갑옷 = 미늘 띠 + 끈, 네모 어깨판(소데), 허리 비늘판.
  //  모두 목 가리개, 허리띠 버클, 허리 판, 쇠장갑·팔 보호대, 정강이 받이·무릎(걸음에 맞춰).
  function armorPal(robe) {
    var r = String(robe || '').toLowerCase();
    if (r === '#8a1a1a') return { kind: 'sam', m: '#9a2020', m2: '#5a0e10', hi: '#d04a3a', tr: '#e2b040', cord: '#f4ead0', dk: '#2a1010' };
    if (r === '#e0b030') return { kind: 'gold', m: '#e8b830', m2: '#a8780a', hi: '#fff3b0', tr: '#fffbe0', gem: '#d4104f', cape: '#b01828', capeTr: '#f2c230', dk: '#6a4608' };
    return { kind: 'dark', m: robe || '#3a3f4a', m2: shade(robe || '#3a3f4a', -0.4), hi: shade(robe || '#3a3f4a', 0.5), tr: '#c8303a', gem: '#ff5a4a', cape: '#6a1424', capeTr: '#c8303a', dk: '#14161c' };
  }
  function armorCapeHD(add, c, robe) {
    var P = armorPal(robe);
    if (!P.cape) return;
    var cp = c.side ? hdTrap(34, 58.5, 5.5, 9.5, c.cx - 3.5) : hdTrap(34, 58.5, 9.6, 14.5, c.cx);
    add('cape', P.cape, cp);
    add('cape', P.capeTr, hdAnd(cp, function (u, v) { return v > 57.2; }));
  }
  function armorHD(add, c, robe) {
    var P = armorPal(robe), cx = c.cx, F = c.front, B = c.back, S = c.side, st = c.step | 0;
    var torso = hdTrap(34.5, 46, S ? 6.5 : 7.5, S ? 8 : 9.5, cx);
    if (B && P.cape) { var cpB = hdTrap(34, 58.5, 9.8, 14.5, cx); add('cape', P.cape, cpB); add('cape', P.capeTr, hdAnd(cpB, function (u, v) { return v > 57.2; })); add('cape', shade(P.cape, -0.3), hdOr(hdRect(cx - 4.3, 40, cx - 3.6, 57), hdRect(cx + 3.6, 40, cx + 4.3, 57))); }
    // 정강이 받이 + 무릎(다리 위에)
    if (S) {
      var fo = st === 1 ? 2.2 : st === 2 ? -2.2 : 0;
      [30.8 - fo, 34.2 + fo].forEach(function (x) { add('leg', P.m2, hdRect(x - 1.8, 52, x + 1.8, 59.4)); add('leg', P.m, hdEll(x, 52.4, 2.3, 1.7)); add('leg', P.hi, hdRect(x + 0.4, 53.4, x + 1, 58.6)); });
    } else {
      [[28.75, st === 1 ? 1.6 : 0], [35.25, st === 2 ? 1.6 : 0]].forEach(function (q) { add('leg', P.m2, hdRect(q[0] - 1.9, 52, q[0] + 1.9, 59.4 - q[1])); add('leg', P.m, hdEll(q[0], 52.4, 2.4, 1.8)); add('leg', P.hi, hdRect(q[0] - 1.3, 53.4, q[0] - 0.7, 58.6 - q[1])); });
    }
    // 허리 판
    if (P.kind === 'sam') {
      (S ? [cx + 1] : [cx - 5.4, cx, cx + 5.4]).forEach(function (x) {
        add('skirt', P.m2, hdRect(x - 2.5, 45.2, x + 2.5, 53.4));
        [46.6, 49.1, 51.6].forEach(function (y) { add('skirt', P.m, hdRect(x - 2.5, y, x + 2.5, y + 1.3)); });
        add('skirt', P.cord, hdRect(x - 0.3, 45.2, x + 0.3, 53.4));
      });
    } else {
      (S ? [cx + 0.6] : [cx - 4.7, cx + 4.7]).forEach(function (x) {
        var tp = hdTrap(45.2, 52.4, 3.3, 4.3, x);
        add('skirt', P.m2, tp); add('skirt', P.m, hdTrap(45.6, 51.4, 2.5, 3.3, x));
        add('skirt', P.tr, hdAnd(tp, function (u, v) { return v > 51.4; })); add('skirt', P.hi, hdRect(x - 2.1, 46.4, x - 1.5, 50.4));
      });
    }
    // 몸통 판
    add('top', P.m2, torso);
    if (P.kind === 'sam') {
      for (var i = 0; i < 4; i++) add('top', i % 2 ? P.m : P.hi, HCLIP(torso, 35.2 + i * 2.5, 36.5 + i * 2.5));
      add('acc', P.cord, hdAnd(torso, function (u, v) { return v > 35 && v < 44.5 && Math.abs(((u - cx + 64) % 3.4) - 1.7) < 0.32; }));
      if (F) add('acc', P.tr, hdEll(cx, 36.2, 1.2, 0.9));
    } else {
      add('top', P.m, hdTrap(35, 44.4, S ? 5.6 : 6.6, S ? 6.8 : 8, cx));
      add('top', P.hi, S ? hdRect(cx + 3.4, 35.6, cx + 4.2, 43.8) : hdRect(cx - 0.45, 35.4, cx + 0.45, 44));
      if (F) {
        add('top', P.hi, hdOr(hdTrap(36.4, 41.6, 0.9, 0.5, cx - 4), hdTrap(36.4, 41.6, 0.9, 0.5, cx + 4)));
        if (P.kind === 'gold') { add('acc', P.tr, hdEll(cx, 39.4, 2.7, 2.7)); add('acc', P.gem, hdEll(cx, 39.4, 1.8, 1.8)); add('acc', '#ffffff', hdEll(cx - 0.6, 38.8, 0.55, 0.55));
          add('acc', P.tr, hdOr(hdTri(cx - 2.6, 36.2, cx - 1, 37.8, cx - 4.4, 37.4), hdTri(cx + 2.6, 36.2, cx + 1, 37.8, cx + 4.4, 37.4))); }
        else { add('acc', P.tr, hdOr(hdTri(cx - 2.8, 37.4, cx + 2.8, 37.4, cx, 42.4), hdRect(cx - 0.55, 36, cx + 0.55, 37.6))); add('acc', P.gem, hdEll(cx, 38.9, 0.95, 0.95)); }
      }
      if (S && P.kind === 'gold') add('acc', P.gem, hdEll(cx + 4.8, 39.4, 1, 1));
    }
    // 목 가리개
    add('top', P.m2, hdTrap(33.4, 35.9, S ? 3.8 : 4.8, S ? 5.2 : 6.4, cx)); add('top', P.hi, hdRect(cx - (S ? 3 : 4.2), 33.6, cx + (S ? 3 : 4.2), 34.2));
    // 허리띠 + 버클
    add('obi', P.dk, hdRect(cx - 8.9, 43.5, cx + 8.9, 45.7)); add('obi', P.tr, hdRect(cx - 8.9, 43.5, cx + 8.9, 44));
    if (!B) { var bxx = S ? cx + 5 : cx; add('acc', P.tr, hdRect(bxx - 1.7, 43.3, bxx + 1.7, 45.9)); add('acc', P.gem || P.cord, hdEll(bxx, 44.6, 0.85, 0.85)); }
    // 쇠장갑 + 팔 보호대
    (S ? [35.6] : [22.6, 41.4]).forEach(function (x) { add('glove', P.m2, hdTrap(42.2, 47, 2.4, 2.9, x)); add('glove', P.m, hdEll(x, 48.3, 2.8, 2.5)); add('glove', P.tr, hdRect(x - 2.9, 42.2, x + 2.9, 42.9)); add('glove', P.hi, hdEll(x - 0.9, 47.6, 0.9, 0.7)); });
    // 어깨
    if (P.kind === 'sam') {
      var sode = function (x) { add('top', P.m2, hdRect(x - 4.5, 34.8, x + 4.5, 43.8)); [36.2, 38.7, 41.2].forEach(function (y) { add('top', P.m, hdRect(x - 4.5, y, x + 4.5, y + 1.3)); }); add('top', P.cord, hdRect(x - 4.5, 34.8, x + 4.5, 35.5)); add('acc', P.tr, hdEll(x, 35.1, 1, 0.7)); };
      if (S) sode(34.4); else { sode(21); sode(43); }
    } else {
      var pauld = function (x, ds) {
        if (P.kind === 'dark') add('acc', P.hi, hdOr(hdTri(x - 1.4 + ds * 0.4, 35, x + 0.6 + ds * 0.4, 35, x + ds * 0.9, 29.6), hdTri(x + ds * 2.4 - 1, 35.4, x + ds * 2.4 + 1, 35.4, x + ds * 3.6, 31.4))); // 가시
        [[41, 4.3, 2.6], [39, 5, 3], [36.9, 5.7, 3.5]].forEach(function (r, i) {
          var e = hdEll(x, r[0], r[1], r[2]);
          add('top', i === 2 ? P.m : P.m2, e); add('top', P.tr, hdAnd(e, function (u, v) { return v > r[0] + r[2] - 0.9; }));
        });
        add('top', P.hi, hdEll(x - ds * 1.7, 35.7, 2.1, 1));
        add('acc', P.hi, hdOr(hdEll(x - 3.4, 37.6, 0.5, 0.5), hdEll(x + 3.4, 37.6, 0.5, 0.5)));
        if (P.kind === 'gold') add('acc', P.gem, hdEll(x, 36.6, 1, 1));
      };
      if (S) pauld(34.4, 1); else { pauld(22.4, -1); pauld(41.6, 1); }
    }
  }
  /** 플레이어 외형(lk) -> hdBuild 값(d). 코스튬은 갈고리로. */
  function heroD(lk) {
    lk = lk || {};
    var top = lookPart(lk.top), hat = lookPart(lk.hat), shoe = lookPart(lk.shoes), fem = lk.gender === 1;
    var robe = top.c || '#3f5fd1', robeD = shade(robe, -0.28), pants = lk.bottom || '#3a2b22', ts = top.s, hs = hat.s, hc = hat.c || '#c8402a', hdk = shade(hc, -0.3);
    var KN = ts === 'knight' || ts === 'knightlord' ? (ts === 'knightlord' ? { a1: '#d9b44a', a2: '#a8842a', a3: '#f6e3a0', acc: '#c8202a', accD: '#8a1018', lord: true } : { a1: '#9aa3b6', a2: '#666f82', a3: '#dfe4ee', acc: '#3557a8', accD: '#22386e', lord: false }) : null;
    var d = { _k: JSON.stringify(lk), hair: lk.hair || '#2b1d16', style: KN ? 'boy' : lk.hairStyle || (fem ? 'long' : 'boy'), boyFace: !fem, top: robe, skirt: robe, legs: pants, shoes: shoe.c || '#5b3a1e',
      eye: fem ? '#7a4ac8' : '#3a4a6a', acc: '#e84a6a', job: 0, noWeapon: true, pants: !fem ? pants : null, noAcc: !fem };
    if (!ts || ts === 'cape' || ts === 'armor') d.belt = '#7a4a1f';
    if (ts === 'maid') { d.apron = true; d.frill = true; d.frillCol = '#f8f8fa'; d.noAcc = true; }
    if (ts === 'gothic') { d.frill = true; d.frillCol = '#c0202a'; d.acc = '#c0202a'; d.noAcc = false; }
    if (ts === 'kimono') { d.kimono = true; d.obi = '#e2b040'; d.collar = '#f8f8fa'; d.noAcc = true; d.pants = null; }
    if (ts === 'miko') { d.skirt = '#c8303c'; if (d.pants) d.pants = '#c8303c'; d.collar = '#f8f8fa'; d.acc = '#c8303c'; d.noAcc = false; }
    if (ts === 'blazer') { d.shirt = '#f8f8fa'; d.tie = true; d.acc = '#c03040'; d.noAcc = false; }
    if (ts === 'hanbok' || ts === 'fur' || ts === 'gm' || ts === 'tux' || ts === 'hoodie' || ts === 'sailor' || ts === 'star' || ts === 'rainbow' || ts === 'wings' || ts === 'bat' || KN) d.noAcc = true;
    if (KN) { d.pants = KN.a2; d.legs = KN.a2; d.shoes = '#2a2e3c'; d.top = KN.a1; d.skirt = KN.a2; d.noFace = true; }
    if (hs === 'neko' || hs === 'cat') { d.ears = true; d.earC = hc; }
    if (hs === 'beret') d.beret = hc;
    if (hs === 'maidband') { d.band = true; d.bandCol = hc; }
    var tail = lk.tail, tailD = tail ? shade(tail, -0.25) : '';
    d.hook = {
      back: function (add, c) { // 몸 뒤
        if (ts === 'armor' && !c.back) armorCapeHD(add, c, robe); // [2026-11-21] 갑옷 치장 망토(몸 뒤)
        if ((ts === 'cape' || (KN && KN.lord)) && !c.back) add('cape', KN ? KN.acc : robeD, c.side ? hdTrap(34, 58, 5.5, 9, c.cx - 3.5) : hdTrap(34, 58, 9.2, 13.5, c.cx));
        if (ts === 'hoodie' && !c.back) add('cape', robeD, hdEll(32, 33.5, 11, 4.5));
        if ((ts === 'wings' || ts === 'bat') && !c.back) {
          var wc = ts === 'wings' ? '#ffffff' : '#2a0a1a', wd = ts === 'wings' ? '#d8e8ff' : '#5a1030';
          var one = function (x, s) { return ts === 'bat' ? hdAnd(hdEll(x, 39, 7, 7.5), function (u, v) { return v < 44 - Math.abs(Math.sin((u - x) * 1.1)) * 3; }) : hdEll(x, 39, 6, 7.5); };
          if (c.side) add('cape', wc, one(25, -1)); else { add('cape', wc, one(17.5, -1)); add('cape', wc, one(46.5, 1)); }
          if (!c.side) { add('cape', wd, hdEll(16, 41, 3, 4.5)); add('cape', wd, hdEll(48, 41, 3, 4.5)); }
        }
        if (tail && !c.back) add('tail', tail, hdOr(hdEll(c.side ? 22 : 43, 47, 2.4, 2.4), hdEll(c.side ? 19 : 46, 43, 2.4, 2.6), hdEll(c.side ? 18 : 47.5, 38.5, 2.3, 2.8), hdEll(c.side ? 19 : 46.5, 34.5, 2.1, 2.2)));
      },
      body: function (add, c) { // 몸통 위(머리 아래)
        var torso = hdTrap(34.5, 46, c.side ? 6.5 : 7.5, c.side ? 8 : 9.5, c.cx), F = c.front, B = c.back, S = c.side;
        if (ts === 'armor') armorHD(add, c, robe); // [2026-11-21] "갑옷 치장이 고해상도에서 멋이 없다 -- 더 웅장하게"
        if (ts === 'hanbok') { if (F) add('acc', '#c0392b', hdOr(hdTri(29.5, 35, 35.5, 35, 32.5, 40.5), hdRect(32.5, 38, 34.2, 46))); add('obi', '#2e5aa8', hdRect(c.cx - 9, 43.5, c.cx + 9, 45.5)); }
        if (ts === 'cape') { if (B) add('cape', robeD, hdTrap(34, 58, 9.6, 13, c.cx)); if (F) add('acc', '#ffd84a', hdEll(32, 35.6, 1.7, 1.5)); }
        if (ts === 'fur') { add('fur', '#ece2c8', HCLIP(torso, 34.5, 37.4)); add('fur', '#ece2c8', hdRect(c.cx - 9.6, 44, c.cx + 9.6, 46.4));
          add('fur', '#ece2c8', S ? hdRect(32.2, 44.4, 37, 46.6) : hdOr(hdRect(20, 44.4, 25.2, 46.6), hdRect(38.8, 44.4, 44, 46.6))); if (!B) add('acc', '#9fd8f0', hdEll(S ? 36 : 32, 40.5, 1.6, 1.6)); }
        if (ts === 'star') { [[27, 38, '#ffe14d'], [36.5, 41.5, '#ffffff'], [29.5, 44.5, '#9fd8f0'], [37.5, 36.6, '#ffe14d'], [33, 50, '#ffe14d'], [26, 50, '#ffffff']].forEach(function (q) { add('acc', q[2], HSTAR(q[0] + (S ? 2 : 0), q[1], 1.4)); }); }
        if (ts === 'gm') { add('acc', '#ffd84a', HCLIP(torso, 34.5, 36.3)); add('acc', '#ffd84a', hdRect(c.cx - 9.6, 44, c.cx + 9.6, 45.8)); if (F) { add('acc', '#ffd84a', hdRect(31, 36.3, 33, 44)); add('acc', '#ff3b6b', hdEll(36, 38.6, 1.5, 1.5)); } }
        if (ts === 'tux') { if (F) { add('shirt', '#ffffff', hdTri(28, 34.5, 36, 34.5, 32, 45)); add('acc', '#d4104f', hdOr(hdTri(29.6, 35.2, 29.6, 38, 32, 36.6), hdTri(34.4, 35.2, 34.4, 38, 32, 36.6))); add('top', robeD, hdOr(hdTri(25, 35, 28.5, 35, 30.5, 44), hdTri(39, 35, 35.5, 35, 33.5, 44))); }
          if (B) add('top', robeD, hdTrap(44, 55, 7, 4.5, c.cx)); }
        if (ts === 'hoodie') { if (F) { add('shirt', '#ffffff', hdOr(hdRect(29.6, 36, 30.6, 41), hdRect(33.4, 36, 34.4, 41))); add('top', robeD, hdRect(27, 41, 37, 44)); } if (B) add('top', robeD, hdEll(32, 36.5, 9.5, 4.8)); }
        if (ts === 'sailor') { var nv = '#2a3a8a'; if (F) { add('collar', nv, hdOr(hdTri(24.5, 34.5, 32, 34.5, 28.8, 40.5), hdTri(39.5, 34.5, 32, 34.5, 35.2, 40.5))); add('acc', '#d4104f', hdTri(29.6, 38.2, 34.4, 38.2, 32, 42.5)); }
          if (B) add('collar', nv, hdRect(25, 34.5, 39, 40.5)); if (S) add('collar', nv, hdRect(27, 34.5, 32, 40)); add('obi', nv, hdRect(c.cx - 9.6, 44, c.cx + 9.6, 45.6)); }
        if ((ts === 'wings' || ts === 'bat') && B) { var wc2 = ts === 'wings' ? '#ffffff' : '#2a0a1a'; add('cape', wc2, hdOr(hdEll(23, 39, 6, 7.5), hdEll(41, 39, 6, 7.5))); }
        if (ts === 'wings' || ts === 'bat') add('obi', ts === 'wings' ? '#ffe97a' : '#c0283a', hdRect(c.cx - 8.6, 43.4, c.cx + 8.6, 45.2));
        if (ts === 'rainbow') ['#e8433a', '#f08a30', '#ffcf30', '#4fbf6a', '#3a8ae8'].forEach(function (rc, i) { add('top', rc, HCLIP(hdOr(torso, hdTrap(44, 53.5, 9.5, 13.5, c.cx)), 34.5 + i * 3.8, 34.5 + (i + 1) * 3.8)); });
        if (KN) { // 판금: 가슴판 빛, 어깨받이, 허리띠, 쇠장갑, 단원 방패 / 단장 망토(뒤)
          add('top', KN.a2, torso); add('top', KN.a1, hdTrap(35.5, 45, S ? 5.2 : 6.2, S ? 6.6 : 8, c.cx)); add('top', KN.a3, hdRect(c.cx - (S ? 1 : 3.5), 36.5, c.cx - (S ? -0.5 : 2), 41.5));
          add('top', KN.a2, S ? hdEll(34.6, 36.8, 3.8, 2.8) : hdOr(hdEll(22.6, 36.8, 4, 2.8), hdEll(41.4, 36.8, 4, 2.8))); add('top', KN.a3, S ? hdEll(33.6, 36, 1.4, 0.9) : hdOr(hdEll(21.6, 36, 1.4, 0.9), hdEll(40.4, 36, 1.4, 0.9)));
          add('obi', KN.a2, hdRect(c.cx - 8.8, 43.4, c.cx + 8.8, 45.4)); add('acc', F ? KN.acc : KN.a3, hdRect(c.cx - 1.2, 43.4, c.cx + 1.2, 45.4));
          add('glove', KN.a2, S ? hdEll(35.6, 48, 2.6, 2.4) : hdOr(hdEll(22.6, 48, 2.6, 2.4), hdEll(41.4, 48, 2.6, 2.4)));
          if (KN.lord && B) { add('cape', KN.acc, hdTrap(34, 58, 9.6, 13, c.cx)); add('acc', '#e0b040', hdRect(30.5, 34.5, 33.5, 36)); }
          if (!KN.lord && F) { add('shield', KN.a2, hdAnd(hdRect(14, 37, 23.5, 51), hdEll(18.75, 44, 6, 9.5))); add('shield', KN.acc, hdRect(17.6, 38.5, 19.9, 49.5)); add('shield', KN.a3, hdEll(18.75, 43, 1.6, 1.6)); }
          if (!KN.lord && B) { add('shield', KN.a2, hdAnd(hdRect(25, 37, 39, 50), hdEll(32, 43.5, 8, 8.5))); add('shield', KN.acc, hdRect(30.8, 38.5, 33.2, 49)); }
        }
        if (tail && B) add('tail', tail, hdOr(hdEll(34, 47, 2.4, 2.4), hdEll(37.5, 44, 2.4, 2.6), hdEll(39.5, 40, 2.3, 2.6), hdEll(40, 36.5, 2.1, 2.1)));
        if (shoe.s === 'wing') add('acc', '#ffffff', S ? hdOr(hdTri(28, 58, 24, 55, 25, 60), hdTri(31.5, 58, 27.5, 55, 28.5, 60)) : hdOr(hdTri(26.5, 58, 22, 55, 23.5, 60.5), hdTri(37.5, 58, 42, 55, 40.5, 60.5)));
        if (shoe.s === 'flame') { var fl = S ? hdOr(hdTri(27, 61, 30, 61, 27.5, 55.5), hdTri(31, 61, 34, 61, 31.5, 56)) : hdOr(hdTri(24.5, 61.5, 27.5, 61.5, 25, 55.5), hdTri(36.5, 61.5, 39.5, 61.5, 39, 55.5));
          add('acc', '#ff6a2a', fl); add('acc', '#ffcf30', S ? hdOr(hdTri(27.5, 61, 29.5, 61, 28, 58), hdTri(31.5, 61, 33.5, 61, 32, 58.5)) : hdOr(hdTri(25, 61.5, 27, 61.5, 25.5, 58), hdTri(37, 61.5, 39, 61.5, 38.5, 58))); }
      },
      head: function (add, c) { // 머리 위(모자/투구)
        var S = c.side, B = c.back, F = c.front, x0 = S ? 31 : 32;
        if (KN) { // 투구 -- 앞: 눈 틈 + 콧대, 옆: 눈 틈, 뒤: 통 투구. 꼭대기 깃
          add('helm', KN.a2, hdEll(32, 20.5, 15.4, 14.8)); add('helm', KN.a1, hdEll(32, 20, 13.8, 13.2)); add('helm', KN.a3, hdEll(S ? 30 : 26, 12.5, 3.2, 2));
          if (F) { add('visor', '#12141c', hdRect(20.5, 22.4, 43.5, 24.8)); add('helm', KN.a2, hdRect(30.8, 24.8, 33.2, 31)); for (var vi = 0; vi < 4; vi++) add('visor', '#12141c', hdRect(26 + vi * 3.6, 28, 27 + vi * 3.6, 31)); }
          if (S) { add('visor', '#12141c', hdRect(33, 22.4, 46.8, 24.8)); add('helm', KN.a2, hdRect(44.5, 24.8, 46.5, 31)); }
          add('plume', KN.acc, S ? hdOr(hdEll(28, 6.5, 7.5, 2.8), hdEll(23, 9, 3, 3.4)) : hdEll(32, 5.5, 2.6, 5.2)); add('plume', KN.accD, S ? hdEll(22.5, 10.5, 2.2, 2.4) : hdEll(32, 9.5, 2.4, 1.6));
          return;
        }
        if (hs === 'straw') { add('hat', hc, hdEll(32, 11.5, S ? 18 : 22, 3.6)); add('hat', hc, HCLIP(hdEll(x0, 9, 11.5, 7.6), -9, 11.5)); add('acc', '#b5251f', HCLIP(hdEll(x0, 9, 11.6, 7.6), 8.2, 10.4)); }
        if (hs === 'helmet') { add('hat', hc, HCLIP(hdEll(32, 16, 16.2, 12.6), -9, 15.5)); add('hat', hdk, hdRect(15.6, 14.6, 48.4, 16.8)); add('acc', '#ffe14d', hdRect(30.4, 2.6, 33.6, 14.6)); }
        if (hs === 'pirate') { add('hat', hc, HCLIP(hdEll(32, 9.5, 19.5, 6.6), -9, 13)); add('hat', hc, hdTri(13, 12.5, 32, 1.5, 51, 12.5)); add('acc', '#ffffff', hdOr(hdEll(32, 7.6, 2.2, 1.9), hdRect(30.6, 9.2, 33.4, 10.2))); }
        if (hs === 'wizard') { add('hat', hdk, hdEll(32, 12.2, S ? 18 : 21, 3.4)); add('hat', hc, hdTri(21.5, 12.2, 42.5, 12.2, S ? 35 : 33, -1)); add('acc', hdk, hdAnd(hdTri(21.5, 12.2, 42.5, 12.2, S ? 35 : 33, -1), function (u, v) { return v > 9.6; })); add('acc', '#ffe14d', HSTAR(S ? 34 : 33, 5, 1.6)); }
        if (hs === 'crown') { add('hat', hc, hdOr(hdRect(21.5, 7.6, 42.5, 11.6), hdTri(21.5, 7.8, 25, 2, 28.5, 7.8), hdTri(28.5, 7.8, 32, 0.8, 35.5, 7.8), hdTri(35.5, 7.8, 39, 2, 42.5, 7.8))); add('acc', '#d4104f', hdEll(32, 9.6, 1.7, 1.4)); add('acc', '#ffffff', hdEll(25, 9.6, 0.9, 0.9)); add('acc', '#ffffff', hdEll(39, 9.6, 0.9, 0.9)); }
        if (hs === 'flower') { add('hat', '#3a9a4a', hdAnd(hdEll(32, 21, 15.4, 14.6), function (u, v) { return v > 8.6 && v < 10.6; }));
          [[21.5, 9.6], [32, 7.4], [42.5, 9.6]].forEach(function (q) { add('hat', hc, hdOr(hdEll(q[0] - 1.6, q[1], 1.6, 1.6), hdEll(q[0] + 1.6, q[1], 1.6, 1.6), hdEll(q[0], q[1] - 1.6, 1.6, 1.6), hdEll(q[0], q[1] + 1.6, 1.6, 1.6))); add('acc', '#ffe14d', hdEll(q[0], q[1], 1.1, 1.1)); }); }
        if (hs === 'santa') { add('hat', hc, hdOr(HCLIP(hdEll(30, 10, 14, 8.4), -9, 12.5), hdTri(38, 3.5, 48.5, 13.5, 41, 12.5))); add('fur', '#ffffff', hdAnd(hdEll(32, 21, 15.6, 14.8), function (u, v) { return v > 10.4 && v < 13.6; })); add('fur', '#ffffff', hdEll(48.5, 15, 2.8, 2.8)); }
        if (hs === 'horn') { add('hat', hc, hdOr(hdTri(18.5, 10.5, 24, 9, 17, 0.5), hdTri(45.5, 10.5, 40, 9, 47, 0.5))); add('acc', '#ffd84a', hdOr(hdTri(17.4, 4, 19.2, 3.6, 17, 0.5), hdTri(46.6, 4, 44.8, 3.6, 47, 0.5))); }
        if (hs === 'rabbit') { add('hat', hc, hdOr(hdRect(19, 8.6, 45, 10.8), hdEll(25.5, 3, 2.8, 7.5), hdEll(38.5, 3, 2.8, 7.5))); add('acc', '#f29bb0', hdOr(hdEll(25.5, 3.5, 1.2, 5.2), hdEll(38.5, 3.5, 1.2, 5.2))); }
        if (hs === 'halo') { add('hat', hc, hdAnd(hdEll(32, 3.2, 10.5, 2.6), function (u, v) { return !hdEll(32, 3.2, 8, 1.3)(u, v); })); add('acc', '#ffffff', hdEll(27, 2.2, 1.4, 0.7)); }
        if (hs === 'ribbon') { add('hat', hc, hdOr(hdTri(44, 8, 37.5, 3, 37.5, 13), hdTri(44, 8, 50.5, 3, 50.5, 13))); add('acc', hdk, hdEll(44, 8, 2, 2)); add('acc', '#ffffff', hdEll(39.5, 6, 0.9, 0.9)); }
        if (hs === 'sunhat') { add('hat', hc, hdEll(32, 12.2, S ? 21 : 25, 3.8)); add('hat', hc, HCLIP(hdEll(x0, 9.6, 12, 7.2), -9, 12.2)); add('acc', '#4a6ad0', HCLIP(hdEll(x0, 9.6, 12.1, 7.2), 9.2, 11.4)); add('acc', '#4a6ad0', hdTri(42, 10, 47, 14, 44.5, 9)); }
        if (hs === 'kanzashi') { add('acc', '#e2b040', function (u, v) { return Math.abs((u - 40) * 9 + (v - 12) * 7) < 9 && u > 39.5 && u < 47.5; }); add('hat', hc, hdOr(hdEll(46.2, 4.8, 2.6, 2.6), hdEll(43.6, 2.8, 1.6, 1.6), hdEll(48.8, 2.8, 1.6, 1.6))); add('acc', '#f4f0f0', hdEll(46.2, 4.8, 1, 1)); }
        if (hs === 'tiara') { add('hat', hc, hdOr(hdRect(25, 8.2, 39, 9.8), hdTri(29.2, 8.4, 32, 3.6, 34.8, 8.4))); add('acc', '#ff8ad8', hdEll(32, 7.6, 1.3, 1.3)); add('acc', '#ffffff', hdOr(hdEll(26.5, 7.6, 0.8, 0.8), hdEll(37.5, 7.6, 0.8, 0.8))); }
        if (hs === 'gmcrown') { add('hat', hc, hdOr(hdRect(19, 7, 45, 12.4), hdTri(19, 7.2, 21.5, 0.5, 25, 7.2), hdTri(25.5, 7.2, 28.5, 0.8, 31.5, 7.2), hdTri(32.5, 7.2, 35.5, 0.8, 38.5, 7.2), hdTri(39, 7.2, 42.5, 0.5, 45, 7.2)));
          add('acc', '#3ad1ff', hdOr(hdEll(23, 9.8, 1.5, 1.5), hdEll(41, 9.8, 1.5, 1.5))); add('acc', '#ff3b6b', hdEll(32, 9.4, 2, 1.8)); add('acc', '#ffffff', hdEll(31.3, 8.8, 0.7, 0.7)); }
      }
    };
    return d;
  }
  // 손에 든 것(gear32)의 손잡이 자리 -> 고해상도 손 자리로 옮기는 양(64 도트). 방향마다.
  var HERO_GEAR_OFF = { down: [4, 7], right: [-2, 7], up: [2, 10] }, HERO_GEAR_K = 0.78; // [2026-11-20] 64 몸(머리가 크다)에 맞게 손에 든 것은 0.78배
  /** 플레이어 한 장(64x64, 캐시). */
  function heroSprite(dir, step, dig, lk) {
    lk = lk || {};
    var key = (lk ? JSON.stringify(lk) : '') + dir + (step | 0) + (dig | 0);
    if (heroC[key]) return heroC[key];
    if (dir === 'left') {
      var src = heroSprite('right', step, dig, lk), c2 = mk(64, 64), g2 = c2.getContext('2d');
      g2.translate(64, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
      return (heroC[key] = c2);
    }
    var body = nekoHD(dir, step | 0, (dig | 0) >= 3 ? dig | 0 : 0, heroD(lk), 64), c = mk(64, 64), g = c.getContext('2d');
    var wl = mk(32, 32), fl = mk(32, 32);
    FX = fl.getContext('2d');
    var drew = gear32(wl.getContext('2d'), dir, step, dig, lk);
    FX = null;
    var o = HERO_GEAR_OFF[dir] || [0, 0], gs = 64 * HERO_GEAR_K, behind = dir === 'up' || (dir === 'right' && !(dig | 0)); // 뒷모습, 옆모습 가만히는 몸 뒤에(얼굴을 안 가리게)
    var gw = drew ? scale2x(outline(wl, '#140d0a')) : null, gf = drew ? scale2x(fl) : null;
    if (drew && behind) { g.drawImage(gw, o[0], o[1], gs, gs); g.drawImage(gf, o[0], o[1], gs, gs); }
    g.drawImage(body, 0, 0);
    if (drew && !behind) { g.drawImage(gw, o[0], o[1], gs, gs); g.drawImage(gf, o[0], o[1], gs, gs); }
    return (heroC[key] = c);
  }
  function setHD(on) { HD_ON = !!on; }
  function isHD() { return HD_ON; }
  /** 64x64 용병 한 장(캐시). */
  function nekoHD(dir, step, dig, d, N) {
    N = N || 64;
    var key = JSON.stringify(d) + dir + (step | 0) + (dig | 0) + N; // d._k = 플레이어 외형(갈고리는 JSON에 안 들어간다)
    if (hdC[key]) return hdC[key];
    if (dir === 'left') {
      var src = nekoHD('right', step, dig, d, N), c2 = mk(N, N), g2 = c2.getContext('2d');
      g2.translate(N, 0); g2.scale(-1, 1); g2.drawImage(src, 0, 0);
      return (hdC[key] = c2);
    }
    var L = hdBuild(d, dir, step | 0, dig | 0), k = 64 / N, col = new Array(N * N), grp = new Array(N * N), x, y, i;
    for (y = 0; y < N; y++) for (x = 0; x < N; x++) {
      var u = (x + 0.5) * k, v = (y + 0.5) * k;
      for (i = L.length - 1; i >= 0; i--) if (L[i].f(u, v)) { col[y * N + x] = L[i].c; grp[y * N + x] = L[i].g; break; }
    }
    var gat = function (xx, yy) { return xx < 0 || yy < 0 || xx >= N || yy >= N ? null : grp[yy * N + xx]; };
    var out = col.slice(), H = d.hair, HL = hdShade(H, 0.35), HD = hdShade(H, -0.28), side = dir === 'right', back = dir === 'up';
    for (y = 0; y < N; y++) for (x = 0; x < N; x++) {
      i = y * N + x; var g = grp[i], c = col[i];
      if (!c) continue;
      var uu = (x + 0.5) * k, vv = (y + 0.5) * k;
      if (g === 'hair') {
        var r = Math.hypot((uu - 32) / 14.5, (vv - 21) / 13.8);
        if (vv < 17 && r > 0.6 && r < 0.78 && Math.abs(uu - (side ? 35 : 32)) < 11) out[i] = HL;
        else if (back ? vv > 30 && uu > 36 : side ? uu < 27 && vv > 22 : uu > 38 && vv > 22) out[i] = HD;
      }
      if ((g === 'top' || g === 'skirt') && (side ? uu < 30 : uu > 35.5)) out[i] = hdShade(c, -0.14);
      var gb = gat(x, y + 1);
      if (gb && gb !== g && ((g === 'hair' && gb === 'skin') || (g === 'top' && gb !== 'top') || (g === 'skirt' && gb === 'leg'))) out[i] = hdShade(out[i], -0.3);
    }
    if (!back && !d.noFace) { // 눈 / 입 / 볼 -- [2026-11-20] 기사단 투구면 얼굴 없음
      var boy = d.boyFace != null ? !!d.boyFace : d.style === 'boy'; // [2026-11-20] 머리 모양과 따로 -- 플레이어는 성별로
      var eye = function (u0, u1) {
        var x0 = Math.round(u0 / k), x1 = Math.max(x0 + 1, Math.round(u1 / k) - 1), y0 = Math.round((boy ? 24.8 : 23.5) / k), y1 = Math.max(y0 + 2, Math.round((boy ? 29.3 : 30) / k) - 1), xx, yy;
        if (boy) for (xx = x0; xx <= x1; xx++) out[Math.round(21.6 / k) * N + xx] = hdShade(H, -0.45); // 눈썹
        for (yy = y0; yy <= y1; yy++) for (xx = x0; xx <= x1; xx++) { var t = (yy - y0) / Math.max(1, y1 - y0); out[yy * N + xx] = yy === y0 ? '#2a1a26' : t < 0.5 ? hdShade(d.eye, -0.35) : t > 0.85 ? hdShade(d.eye, 0.35) : d.eye; }
        out[(y0 + 1) * N + x0] = '#ffffff';
        if (N >= 56) { out[(y0 + 1) * N + x0 + 1] = '#ffffff'; out[(y0 + 2) * N + x0] = '#ffffff'; }
        out[y0 * N + x0 - 1] = '#2a1a26'; out[y0 * N + x1 + 1] = '#2a1a26';
      };
      var dot = function (u0, v0, c2, w) { var bx = Math.round(u0 / k), by = Math.round(v0 / k); out[by * N + bx] = c2; if (w && N >= 56) out[by * N + bx + 1] = c2; };
      if (side) { eye(37.8, 42); dot(42.5, 32.3, '#b8505e'); if (!boy) dot(38.5, 31, '#f7a8a8', 1); }
      else { eye(23.6, 29.2); eye(34.8, 40.4); dot(31.5, 32.3, '#b8505e', 1); if (!boy) { dot(24.5, 31, '#f7a8a8', 1); dot(39.5, 31, '#f7a8a8', 1); } }
    }
    var fin = out.slice(), at = function (xx, yy) { return xx < 0 || yy < 0 || xx >= N || yy >= N ? null : col[yy * N + xx]; };
    for (y = 0; y < N; y++) for (x = 0; x < N; x++) {
      if (out[y * N + x]) continue;
      var nb = at(x, y - 1) || at(x, y + 1) || at(x - 1, y) || at(x + 1, y);
      if (nb) fin[y * N + x] = hdShade(nb, -0.62);
    }
    var cv = mk(N, N), gg = cv.getContext('2d'), im = gg.createImageData(N, N);
    for (i = 0; i < N * N; i++) if (fin[i]) { var c3 = hdHex(fin[i]); im.data[i * 4] = c3[0]; im.data[i * 4 + 1] = c3[1]; im.data[i * 4 + 2] = c3[2]; im.data[i * 4 + 3] = 255; }
    gg.putImageData(im, 0, 0);
    return (hdC[key] = cv);
  }
  return { sprite: charSprite, neko: nekoSprite, nekoHD: nekoHD, dataUrl: dataUrl, wings: wings, withWings: withWings, wide: wide, setHD: setHD, isHD: isHD, hero: heroSprite };
})();
</script>
