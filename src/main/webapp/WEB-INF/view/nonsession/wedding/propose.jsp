<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>&#128140;</title>
    <link href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@300;400;600&family=Dancing+Script:wght@400;600&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            min-height: 100vh;
            background: linear-gradient(135deg, #1a0a0f 0%, #2d0a1e 35%, #1a0520 65%, #0a0a2e 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Noto Serif KR', serif;
            overflow: hidden;
        }

        /* Floating particles */
        .particles {
            position: fixed;
            inset: 0;
            pointer-events: none;
            z-index: 0;
        }

        .particle {
            position: absolute;
            border-radius: 50%;
            background: rgba(255, 180, 200, 0.5);
            animation: floatUp linear infinite;
        }

        @keyframes floatUp {
            0%   { transform: translateY(100vh) rotate(0deg);   opacity: 0; }
            10%  { opacity: 1; }
            90%  { opacity: 0.6; }
            100% { transform: translateY(-40px) rotate(720deg); opacity: 0; }
        }

        /* ===== Book wrapper ===== */
        .book-scene {
            position: relative;
            z-index: 1;
            width: min(380px, 92vw);
            height: min(580px, 88vh);
        }

        .book-container {
            position: relative;
            width: 100%;
            height: 100%;
            perspective: 2000px;
        }

        /* ===== Pages ===== */
        .page {
            position: absolute;
            inset: 0;
            transform-origin: left center;
            transform-style: preserve-3d;
            border-radius: 0 12px 12px 0;
            box-shadow: 4px 4px 30px rgba(0, 0, 0, 0.5);
        }

        .page-face {
            position: absolute;
            inset: 0;
            backface-visibility: hidden;
            -webkit-backface-visibility: hidden;
            border-radius: 0 12px 12px 0;
            overflow: hidden;
        }

        .page-back {
            transform: rotateY(180deg);
            background: linear-gradient(145deg, #f7ece8 0%, #eedcd8 100%);
        }

        .page-back-inner {
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 72px;
            background-image: repeating-linear-gradient(
                45deg,
                transparent,
                transparent 12px,
                rgba(200, 130, 150, 0.08) 12px,
                rgba(200, 130, 150, 0.08) 24px
            );
        }

        /* Spine gradient */
        .page-face::before {
            content: '';
            position: absolute;
            top: 0; left: 0; bottom: 0;
            width: 18px;
            background: linear-gradient(to right, rgba(0,0,0,0.12), transparent);
            z-index: 2;
            pointer-events: none;
        }

        /* Edge shadow */
        .page-face::after {
            content: '';
            position: absolute;
            top: 0; right: 0; bottom: 0;
            width: 28px;
            background: linear-gradient(to right, transparent, rgba(0,0,0,0.08));
            z-index: 2;
            pointer-events: none;
        }

        /* ===== Navigation ===== */
        .nav-btn {
            position: fixed;
            top: 50%;
            transform: translateY(-50%);
            background: rgba(255, 255, 255, 0.12);
            backdrop-filter: blur(6px);
            border: 1px solid rgba(255,255,255,0.15);
            color: rgba(255,255,255,0.85);
            font-size: 28px;
            width: 50px;
            height: 50px;
            border-radius: 50%;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: background 0.25s;
            z-index: 100;
        }

        .nav-btn:hover { background: rgba(255,255,255,0.25); }
        .nav-btn:disabled { opacity: 0.15; cursor: default; pointer-events: none; }
        .nav-btn.prev { left: 16px; }
        .nav-btn.next { right: 16px; }

        .page-dots {
            position: fixed;
            bottom: 28px;
            left: 50%;
            transform: translateX(-50%);
            display: flex;
            gap: 9px;
            z-index: 100;
        }

        .dot {
            width: 8px; height: 8px;
            border-radius: 50%;
            background: rgba(255,255,255,0.25);
            transition: background 0.3s, transform 0.3s;
        }

        .dot.active {
            background: rgba(255, 190, 200, 0.9);
            transform: scale(1.4);
        }

        /* ===== Page 1 : Login / Cover ===== */
        .p1-front {
            background: linear-gradient(160deg, #2d0a1e 0%, #5a1a3e 45%, #8b2a5a 100%);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px 32px;
            color: white;
            gap: 0;
        }

        .p1-heart {
            font-size: 62px;
            animation: heartbeat 1.6s ease-in-out infinite;
            margin-bottom: 18px;
        }

        @keyframes heartbeat {
            0%, 100% { transform: scale(1); }
            50%       { transform: scale(1.18); }
        }

        .p1-title {
            font-family: 'Dancing Script', cursive;
            font-size: 38px;
            color: #ffd4d8;
            text-shadow: 0 0 24px rgba(255, 130, 150, 0.5);
            margin-bottom: 6px;
        }

        .p1-subtitle {
            font-size: 11px;
            color: rgba(255, 210, 215, 0.65);
            letter-spacing: 3px;
            margin-bottom: 38px;
        }

        .p1-form { width: 100%; display: flex; flex-direction: column; gap: 18px; }

        .p1-input-group { position: relative; }

        .p1-label {
            display: block;
            font-size: 10px;
            color: rgba(255,200,210,0.75);
            letter-spacing: 2px;
            margin-bottom: 6px;
        }

        .p1-input {
            width: 100%;
            padding: 13px 16px;
            background: rgba(255,255,255,0.09);
            border: 1px solid rgba(255, 200, 210, 0.28);
            border-radius: 8px;
            color: white;
            font-size: 14px;
            font-family: 'Noto Serif KR', serif;
            outline: none;
            transition: border-color 0.3s, background 0.3s;
        }

        .p1-input:focus {
            border-color: rgba(255, 180, 200, 0.7);
            background: rgba(255,255,255,0.14);
        }

        .p1-input::placeholder { color: rgba(255,255,255,0.32); }

        .p1-btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #c0416a, #8b2a5a);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 15px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 4px 22px rgba(192, 65, 106, 0.45);
            transition: transform 0.2s, box-shadow 0.2s;
            margin-top: 6px;
        }

        .p1-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 7px 28px rgba(192, 65, 106, 0.65);
        }

        .p1-hint {
            position: absolute;
            bottom: 22px;
            font-size: 10px;
            color: rgba(255, 200, 210, 0.35);
            letter-spacing: 2px;
        }

        /* ===== Photo pages ===== */
        .photo-page {
            background: linear-gradient(150deg, #fff8f8 0%, #fef0f2 50%, #fff5f8 100%);
            padding: 26px 22px 20px;
            display: flex;
            flex-direction: column;
            gap: 14px;
        }

        .photo-page-label {
            text-align: center;
            font-size: 10px;
            color: #c07888;
            letter-spacing: 4px;
        }

        .photo-grid-2 {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            flex: 1;
            min-height: 0;
        }

        .photo-grid-1 {
            flex: 1;
            min-height: 0;
        }

        .photo-slot {
            background: linear-gradient(135deg, #f5e4e8, #e8d0d6);
            border: 2px dashed #c8a0ac;
            border-radius: 8px;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: #a07888;
            font-size: 11px;
            letter-spacing: 1px;
            overflow: hidden;
            width: 100%;
            height: 100%;
        }

        .photo-slot img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .photo-slot-icon { font-size: 26px; opacity: 0.45; margin-bottom: 5px; }

        .photo-caption {
            font-size: 11px;
            color: #a08090;
            text-align: center;
            font-style: italic;
        }

        .photo-deco { text-align: center; font-size: 15px; color: #d4a0b0; }

        /* ===== Page 5 : Propose ===== */
        .propose-front {
            background: linear-gradient(155deg, #fff0f5 0%, #ffe4ec 45%, #ffd4e8 100%);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 38px 32px;
            text-align: center;
        }

        .propose-icon {
            font-size: 52px;
            animation: floatIcon 2.2s ease-in-out infinite;
            margin-bottom: 18px;
        }

        @keyframes floatIcon {
            0%, 100% { transform: translateY(0); }
            50%       { transform: translateY(-10px); }
        }

        .propose-title {
            font-size: 28px;
            color: #8b2050;
            font-weight: 600;
            margin-bottom: 20px;
            line-height: 1.4;
        }

        .propose-msg {
            font-size: 14px;
            color: #5a3040;
            line-height: 2;
            font-weight: 300;
            margin-bottom: 36px;
        }

        .propose-btns { display: flex; gap: 16px; align-items: center; }

        .btn-yes {
            padding: 14px 34px;
            background: linear-gradient(135deg, #c0416a, #8b2a5a);
            border: none;
            border-radius: 50px;
            color: white;
            font-size: 16px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 6px 26px rgba(192, 65, 106, 0.5);
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .btn-yes:hover {
            transform: scale(1.06);
            box-shadow: 0 8px 32px rgba(192, 65, 106, 0.7);
        }

        .btn-no {
            padding: 12px 26px;
            background: transparent;
            border: 2px solid #c0a0b0;
            border-radius: 50px;
            color: #a08090;
            font-size: 14px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            transition: border-color 0.2s;
            white-space: nowrap;
        }

        /* YES response */
        .yes-response {
            display: none;
            flex-direction: column;
            align-items: center;
            gap: 14px;
        }

        .yes-emoji {
            font-size: 64px;
            animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }

        @keyframes popIn {
            0%   { transform: scale(0); }
            100% { transform: scale(1); }
        }

        .yes-text {
            font-size: 20px;
            color: #8b2050;
            font-weight: 600;
        }

        .yes-sub {
            font-size: 13px;
            color: #8b4a6a;
            line-height: 1.9;
        }
    </style>
</head>
<body>

<div class="particles" id="particles"></div>

<button class="nav-btn prev" id="prevBtn" onclick="goPrev()" disabled>&#8249;</button>
<button class="nav-btn next" id="nextBtn" onclick="goNext()">&#8250;</button>
<div class="page-dots" id="pageDots"></div>

<div class="book-scene">
  <div class="book-container" id="bookContainer">

    <!-- Page 1 : Cover / Login -->
    <div class="page" id="page-0">
      <div class="page-face p1-front">
        <div class="p1-heart">&#128140;</div>
        <div class="p1-title">For You</div>
        <div class="p1-subtitle">&#45813;&#49888;&#50640;&#44172; &#51204;&#54616;&#45716; &#51060;&#50556;&#44592;</div>
        <div class="p1-form">
          <div class="p1-input-group">
            <label class="p1-label">&#51060;&#47492;</label>
            <input class="p1-input" type="text" placeholder="&#45813;&#49888;&#51032; &#51060;&#47492;&#51012; &#51077;&#47141;&#54644;&#51452;&#49464;&#50836;">
          </div>
          <div class="p1-input-group">
            <label class="p1-label">&#48708;&#48128;&#48264;&#54840;</label>
            <input class="p1-input" type="password" placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;">
          </div>
          <button class="p1-btn" onclick="goNext()">&#50676;&#50612;&#48372;&#44592; &#128140;</button>
        </div>
        <div class="p1-hint">&#8594; &#45572;&#44592;&#44144; &#46284;&#47141&#44049; &#43578;</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">&#127800;</div>
      </div>
    </div>

    <!-- Page 2 : Photos 1 -->
    <div class="page" id="page-1">
      <div class="page-face photo-page">
        <div class="photo-page-label">&#10022; OUR MOMENTS &#10022;</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 1</div>
          </div>
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 2</div>
          </div>
        </div>
        <div class="photo-caption">&#50864;&#47532;&#44032; &#52376;&#51020; &#47564;&#45806;&#45358; &#45216;</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 3</div>
          </div>
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 4</div>
          </div>
        </div>
        <div class="photo-deco">&#10023;</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">&#127800;</div>
      </div>
    </div>

    <!-- Page 3 : Photos 2 -->
    <div class="page" id="page-2">
      <div class="page-face photo-page">
        <div class="photo-page-label">&#10022; MEMORIES &#10022;</div>
        <div class="photo-grid-1" style="flex:1.4; min-height:0; display:flex;">
          <div class="photo-slot" style="flex:1;">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 5</div>
          </div>
        </div>
        <div class="photo-caption">&#54632;&#44760;&#54588;&#45358; &#49548;&#51473;&#54620; &#49894;&#44036;&#46é4;</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 6</div>
          </div>
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 7</div>
          </div>
        </div>
        <div class="photo-deco">&#10023;</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">&#127800;</div>
      </div>
    </div>

    <!-- Page 4 : Photos 3 -->
    <div class="page" id="page-3">
      <div class="page-face photo-page">
        <div class="photo-page-label">&#10022; WITH YOU &#10022;</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 8</div>
          </div>
          <div class="photo-slot">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 9</div>
          </div>
        </div>
        <div class="photo-caption">&#50553;&#51004;&#47196;&#46020; &#54632;&#44760;&#54616;&#44256; &#49910;&#50612;</div>
        <div class="photo-grid-1" style="flex:1.2; min-height:0; display:flex;">
          <div class="photo-slot" style="flex:1;">
            <div class="photo-slot-icon">&#128247;</div>
            <div>&#49324;&#51652; 10</div>
          </div>
        </div>
        <div class="photo-deco">&#10023;</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">&#127800;</div>
      </div>
    </div>

    <!-- Page 5 : Propose -->
    <div class="page" id="page-4">
      <div class="page-face propose-front">
        <div id="proposeMain">
          <div class="propose-icon">&#128141;</div>
          <div class="propose-title">&#44208;&#54844;&#54644;&#51904;&#47000;&#50836;?</div>
          <div class="propose-msg">
            &#45813;&#49888;&#44284; &#54632;&#44760;&#54620; &#47784;&#46304; &#49894;&#44036;&#51060;<br>
            &#45236; &#51064;&#49373; &#52572;&#44256;&#51032; &#49440;&#47932;&#51060;&#50788;&#50836;.<br><br>
            &#50534;&#51004;&#47196;&#46020; &#50689;&#50896;&#54616;<br>
            &#45813;&#49888; &#44158;&#50640; &#51080;&#44256; &#49910;&#50612;&#50836;.
          </div>
          <div class="propose-btns">
            <button class="btn-yes" onclick="onYes()">&#128149; &#51025;, &#54624;&#44172;&#50836;!</button>
            <button class="btn-no" id="noBtn"
                    onmouseenter="runAway(this)"
                    onclick="runAway(this)">&#50500;&#45768;&#50724;...</button>
          </div>
        </div>
        <div class="yes-response" id="yesResponse">
          <div class="yes-emoji">&#129392;</div>
          <div class="yes-text">&#49324;&#46993;&#54644;&#50836;! &#128149;</div>
          <div class="yes-sub">
            &#54665;&#48373;&#54616;&#44172; &#54644;&#51904;&#44228;&#50836;.<br>
            &#50689;&#50896;&#54616; &#54632;&#44760;&#54644;&#50836;.
          </div>
        </div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">&#127800;</div>
      </div>
    </div>

  </div>
</div>

<script>
(function () {
    // ===== Particles =====
    var pc = document.getElementById('particles');
    for (var i = 0; i < 22; i++) {
        var p = document.createElement('div');
        p.className = 'particle';
        var size = Math.random() * 5 + 2;
        p.style.cssText = [
            'left:' + Math.random() * 100 + 'vw',
            'width:' + size + 'px',
            'height:' + size + 'px',
            'animation-duration:' + (Math.random() * 9 + 7) + 's',
            'animation-delay:' + (Math.random() * 8) + 's',
            'opacity:' + (Math.random() * 0.5 + 0.15)
        ].join(';');
        pc.appendChild(p);
    }

    // ===== Page flip =====
    var TOTAL = 5;
    var current = 0;
    var busy = false;
    var pages = [];
    for (var k = 0; k < TOTAL; k++) {
        var pg = document.getElementById('page-' + k);
        pg.style.zIndex = String(TOTAL - k);
        pages.push(pg);
    }

    var prevBtn = document.getElementById('prevBtn');
    var nextBtn = document.getElementById('nextBtn');
    var dotsEl  = document.getElementById('pageDots');

    for (var d = 0; d < TOTAL; d++) {
        var dot = document.createElement('div');
        dot.className = 'dot' + (d === 0 ? ' active' : '');
        dotsEl.appendChild(dot);
    }
    var dots = dotsEl.querySelectorAll('.dot');

    function updateUI() {
        prevBtn.disabled = current === 0;
        nextBtn.disabled = current === TOTAL - 1;
        dots.forEach(function (dt, i) {
            dt.className = 'dot' + (i === current ? ' active' : '');
        });
    }

    window.goNext = function () {
        if (current >= TOTAL - 1 || busy) return;
        busy = true;
        var pg = pages[current];
        pg.style.transition = 'transform 0.88s cubic-bezier(0.645,0.045,0.355,1)';
        pg.style.transform   = 'rotateY(-180deg)';
        setTimeout(function () { pg.style.zIndex = '0'; }, 440);
        setTimeout(function () {
            current++;
            updateUI();
            busy = false;
        }, 880);
    };

    window.goPrev = function () {
        if (current <= 0 || busy) return;
        busy = true;
        current--;
        var pg = pages[current];
        pg.style.zIndex = String(TOTAL - current);
        setTimeout(function () {
            pg.style.transition = 'transform 0.88s cubic-bezier(0.645,0.045,0.355,1)';
            pg.style.transform   = 'rotateY(0deg)';
        }, 16);
        setTimeout(function () {
            updateUI();
            busy = false;
        }, 900);
    };

    document.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') window.goNext();
        if (e.key === 'ArrowLeft'  || e.key === 'ArrowUp')   window.goPrev();
    });

    var touchX = 0;
    document.addEventListener('touchstart', function (e) { touchX = e.touches[0].clientX; });
    document.addEventListener('touchend',   function (e) {
        var diff = touchX - e.changedTouches[0].clientX;
        if (Math.abs(diff) > 50) { diff > 0 ? window.goNext() : window.goPrev(); }
    });

    updateUI();

    // ===== NO button escape =====
    var noBtn = document.getElementById('noBtn');
    var escaped = false;

    window.runAway = function (btn) {
        if (!escaped) {
            escaped = true;
            btn.style.position  = 'fixed';
            btn.style.transition = 'left 0.18s ease, top 0.18s ease';
            btn.style.zIndex     = '999';
        }
        var bw = btn.offsetWidth  || 80;
        var bh = btn.offsetHeight || 40;
        var margin = 60;
        var rect   = btn.getBoundingClientRect();
        var attempts = 0;
        var nx, ny;
        do {
            nx = margin + Math.random() * (window.innerWidth  - bw - margin * 2);
            ny = margin + Math.random() * (window.innerHeight - bh - margin * 2);
            attempts++;
        } while (attempts < 10 &&
                 Math.abs(nx - rect.left) < 120 &&
                 Math.abs(ny - rect.top)  < 80);
        btn.style.left = nx + 'px';
        btn.style.top  = ny + 'px';
    };

    // ===== YES response =====
    window.onYes = function () {
        document.getElementById('proposeMain').style.display = 'none';
        var yr = document.getElementById('yesResponse');
        yr.style.display = 'flex';
        if (escaped) noBtn.style.display = 'none';
        launchHearts();
    };

    function launchHearts() {
        var symbols = ['&#128149;','&#128150;','&#128151;','&#128152;','&#128157;','&#127801;','&#10024;'];
        for (var h = 0; h < 18; h++) {
            (function (idx) {
                setTimeout(function () {
                    var el = document.createElement('div');
                    var sz = Math.random() * 18 + 16;
                    el.innerHTML = symbols[Math.floor(Math.random() * symbols.length)];
                    el.style.cssText = [
                        'position:fixed',
                        'font-size:' + sz + 'px',
                        'left:' + Math.random() * 100 + 'vw',
                        'bottom:0',
                        'z-index:9999',
                        'pointer-events:none',
                        'animation:heartFly 2.2s ease-out forwards'
                    ].join(';');
                    document.body.appendChild(el);
                    setTimeout(function () { el.remove(); }, 2200);
                }, idx * 110);
            })(h);
        }
    }

    var hfStyle = document.createElement('style');
    hfStyle.textContent = '@keyframes heartFly{0%{transform:translateY(0) rotate(0deg);opacity:1}100%{transform:translateY(-100vh) rotate(360deg);opacity:0}}';
    document.head.appendChild(hfStyle);
})();
</script>

</body>
</html>
