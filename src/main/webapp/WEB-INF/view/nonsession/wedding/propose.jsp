<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>💌</title>
    <link href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@300;400;600&family=Dancing+Script:wght@400;600&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            min-height: 100vh;
            background: linear-gradient(135deg, #fdf8ef 0%, #f9f2e3 40%, #fdf5e6 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Noto Serif KR', serif;
            overflow: hidden;
            transition: background 3s ease;
        }

        /* YES 클릭 시 배경 오버레이 */
        #yesOverlay {
            position: fixed;
            inset: 0;
            opacity: 0;
            background: linear-gradient(135deg, #1e4a2e 0%, #2d6040 35%, #3a7050 65%, #2a5838 100%);
            transition: opacity 3.5s ease;
            z-index: 0;
            pointer-events: none;
        }

        /* 꽃잎 파티클 */
        .particles {
            position: fixed;
            inset: 0;
            pointer-events: none;
            z-index: 0;
        }

        .particle {
            position: absolute;
            border-radius: 50% 0 50% 0;
            background: rgba(200, 165, 100, 0.40);
            animation: floatPetal linear infinite;
        }

        @keyframes floatPetal {
            0%   { transform: translateY(100vh) rotate(0deg);   opacity: 0; }
            10%  { opacity: 0.6; }
            90%  { opacity: 0.3; }
            100% { transform: translateY(-40px) rotate(540deg); opacity: 0; }
        }

        /* ===== 북 래퍼 ===== */
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

        /* ===== 페이지 ===== */
        .page {
            position: absolute;
            inset: 0;
            transform-origin: left center;
            transform-style: preserve-3d;
            border-radius: 0 14px 14px 0;
            box-shadow: 3px 3px 24px rgba(160, 120, 70, 0.20), 8px 8px 40px rgba(0,0,0,0.08);
        }

        .page-face {
            position: absolute;
            inset: 0;
            backface-visibility: hidden;
            -webkit-backface-visibility: hidden;
            border-radius: 0 14px 14px 0;
            overflow: hidden;
        }

        .page-back {
            transform: rotateY(180deg);
            background: linear-gradient(145deg, #fdf7ea 0%, #f7edd8 100%);
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
                transparent 14px,
                rgba(200, 160, 100, 0.08) 14px,
                rgba(200, 160, 100, 0.08) 28px
            );
        }

        /* 제본 그림자 */
        .page-face::before {
            content: '';
            position: absolute;
            top: 0; left: 0; bottom: 0;
            width: 20px;
            background: linear-gradient(to right, rgba(160,120,70,0.10), transparent);
            z-index: 2;
            pointer-events: none;
        }

        /* ===== 내비게이션 ===== */
        .nav-btn {
            position: fixed;
            top: 50%;
            transform: translateY(-50%);
            background: rgba(180, 145, 90, 0.18);
            backdrop-filter: blur(4px);
            border: 1px solid rgba(180, 145, 90, 0.35);
            color: #8b6840;
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

        .nav-btn:hover  { background: rgba(180, 145, 90, 0.32); }
        .nav-btn:disabled { opacity: 0.2; cursor: default; pointer-events: none; }
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
            background: rgba(180, 145, 90, 0.30);
            transition: background 0.3s, transform 0.3s;
        }

        .dot.active {
            background: rgba(150, 100, 50, 0.80);
            transform: scale(1.4);
        }

        /* ===== 초대장 이중 테두리 프레임 ===== */
        .invite-frame {
            position: absolute;
            inset: 14px;
            border: 1px solid rgba(140, 105, 55, 0.38);
            border-radius: 2px;
            pointer-events: none;
            z-index: 0;
        }
        .invite-frame::before {
            content: '';
            position: absolute;
            inset: 5px;
            border: 1px solid rgba(140, 105, 55, 0.20);
            border-radius: 1px;
        }
        .invite-frame::after {
            content: '✦';
            position: absolute;
            bottom: -10px;
            left: 50%;
            transform: translateX(-50%);
            font-size: 12px;
            color: rgba(140, 105, 55, 0.40);
            background: inherit;
            padding: 0 6px;
        }

        /* ===== 페이지 1 : 커버 ===== */
        .p1-front {
            background: linear-gradient(160deg, #f8edd5 0%, #ede0c0 45%, #e4d0a8 100%);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px 32px;
            gap: 0;
            position: relative;
        }

        .p1-heart {
            font-size: 62px;
            animation: heartbeat 1.6s ease-in-out infinite;
            margin-bottom: 16px;
        }

        @keyframes heartbeat {
            0%, 100% { transform: scale(1); }
            50%       { transform: scale(1.18); }
        }

        .p1-title {
            font-family: 'Dancing Script', cursive;
            font-size: 40px;
            color: #7a4f2e;
            text-shadow: 0 2px 16px rgba(255,255,255,0.8);
            margin-bottom: 6px;
        }

        .p1-subtitle {
            font-size: 11px;
            color: rgba(120, 80, 40, 0.65);
            letter-spacing: 3px;
            margin-bottom: 36px;
        }

        .p1-form { width: 100%; display: flex; flex-direction: column; gap: 16px; }

        .p1-label {
            display: block;
            font-size: 10px;
            color: rgba(120, 80, 40, 0.70);
            letter-spacing: 2px;
            margin-bottom: 6px;
        }

        .p1-input {
            width: 100%;
            padding: 13px 16px;
            background: rgba(255, 255, 255, 0.70);
            border: 1px solid rgba(180, 140, 80, 0.30);
            border-radius: 8px;
            color: #6b4c2c;
            font-size: 14px;
            font-family: 'Noto Serif KR', serif;
            outline: none;
            transition: border-color 0.3s, background 0.3s;
        }

        .p1-input:focus {
            border-color: rgba(180, 140, 80, 0.60);
            background: rgba(255, 255, 255, 0.90);
        }

        .p1-input::placeholder { color: rgba(140, 100, 60, 0.35); }

        .p1-btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #c09060, #9a7040);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 15px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 4px 20px rgba(160, 110, 50, 0.40);
            transition: transform 0.2s, box-shadow 0.2s;
            margin-top: 6px;
        }

        .p1-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 7px 26px rgba(160, 110, 50, 0.55);
        }

        .p1-hint {
            position: absolute;
            bottom: 22px;
            font-size: 10px;
            color: rgba(140, 100, 50, 0.45);
            letter-spacing: 2px;
        }

        /* ===== 사진 페이지 ===== */
        .photo-page {
            background: linear-gradient(150deg, #fefaf2 0%, #f8f1e2 50%, #f5edd6 100%);
            padding: 22px 18px 14px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }

        .photo-page-label {
            text-align: center;
            font-size: 10px;
            color: #9a7040;
            letter-spacing: 4px;
        }

        .photo-grid-2 {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 8px;
            flex: 1;
            min-height: 0;
        }

        .photo-slot {
            background: linear-gradient(135deg, #fdf5e3, #f5e8cc);
            border: 2px dashed #c8a870;
            border-radius: 10px;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: #9a7040;
            font-size: 11px;
            overflow: hidden;
            width: 100%;
            height: 100%;
        }

        .photo-slot img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .photo-slot-large {
            background: linear-gradient(135deg, #fdf5e3, #f5e8cc);
            border: 2px dashed #c8a870;
            border-radius: 10px;
            overflow: hidden;
            flex: 1;
            min-height: 0;
        }

        .photo-slot-large img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .photo-caption {
            font-size: 11px;
            color: #a08050;
            text-align: center;
            font-style: italic;
        }

        .photo-deco { text-align: center; font-size: 16px; color: #c8a870; }

        /* ===== 마지막 페이지 : 프로포즈 ===== */
        .propose-front {
            background: linear-gradient(155deg, #f8edd5 0%, #ede0c0 45%, #e4d0a8 100%);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 38px 32px;
            text-align: center;
            position: relative;
            transition: background 3s ease;
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
            color: #7a4f2e;
            font-weight: 600;
            margin-bottom: 20px;
            line-height: 1.4;
        }

        .propose-msg {
            font-size: 14px;
            color: #8b6040;
            line-height: 2;
            font-weight: 300;
            margin-bottom: 36px;
        }

        .propose-btns { display: flex; gap: 16px; align-items: center; }

        .btn-yes {
            padding: 14px 34px;
            background: linear-gradient(135deg, #c09060, #9a7040);
            border: none;
            border-radius: 50px;
            color: white;
            font-size: 16px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 6px 24px rgba(160, 110, 50, 0.45);
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .btn-yes:hover {
            transform: scale(1.06);
            box-shadow: 0 8px 30px rgba(160, 110, 50, 0.65);
        }

        .btn-no {
            padding: 12px 26px;
            background: transparent;
            border: 2px solid #c8a870;
            border-radius: 50px;
            color: #a08050;
            font-size: 14px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            white-space: nowrap;
            pointer-events: auto;
        }

        /* YES 응답 */
        .yes-response {
            display: none;
            flex-direction: column;
            align-items: center;
            gap: 14px;
        }

        .totoro-img {
            width: 150px;
            height: 150px;
            object-fit: cover;
            border-radius: 50%;
            border: 4px solid #c8a870;
            box-shadow: 0 4px 20px rgba(160, 120, 60, 0.30);
            animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }

        @keyframes popIn {
            0%   { transform: scale(0); }
            100% { transform: scale(1); }
        }

        .yes-text {
            font-size: 20px;
            color: #f5e8c8;
            font-weight: 600;
            transition: color 3s ease;
        }

        .yes-sub {
            font-size: 13px;
            color: #d4c8a0;
            line-height: 1.9;
            transition: color 3s ease;
        }
    </style>
</head>
<body>

<div id="yesOverlay"></div>
<div class="particles" id="particles"></div>

<button class="nav-btn prev" id="prevBtn" onclick="goPrev()" disabled>&#8249;</button>
<button class="nav-btn next" id="nextBtn" onclick="goNext()">&#8250;</button>
<div class="page-dots" id="pageDots"></div>

<div class="book-scene">
  <div class="book-container" id="bookContainer">

    <!-- 페이지 1 : 커버 -->
    <div class="page" id="page-0">
      <div class="page-face p1-front">
        <div class="invite-frame"></div>
        <div class="p1-heart">🎫</div>
        <div class="p1-title">For You</div>
        <div class="p1-subtitle">함께 떠나는 여행으로 초대합니다</div>
        <div class="p1-form">
          <div>
            <label class="p1-label">이름</label>
            <input class="p1-input" type="text" placeholder="당신의 이름을 입력해주세요">
          </div>
          <div>
            <label class="p1-label">비밀번호</label>
            <input class="p1-input" type="password" placeholder="••••••••">
          </div>
          <button class="p1-btn" onclick="goNext()">초대장 열기 🎫</button>
        </div>
        <div class="p1-hint">✦ 펼쳐서 확인하세요 ✦</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

    <!-- 페이지 2 : 사진 1~4 -->
    <div class="page" id="page-1">
      <div class="page-face photo-page">
        <div class="photo-page-label">✦ OUR MOMENTS ✦</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <img src="/img_bom/1.jpg" alt="">
          </div>
          <div class="photo-slot">
            <img src="/img_bom/2.jpg" alt="">
          </div>
        </div>
        <div class="photo-caption">우리가 함께한 소중한 순간</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <img src="/img_bom/3.jpg" alt="">
          </div>
          <div class="photo-slot">
            <img src="/img_bom/4.jpg" alt="">
          </div>
        </div>
        <div class="photo-deco">❧</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

    <!-- 페이지 3 : 사진 5~7 -->
    <div class="page" id="page-2">
      <div class="page-face photo-page">
        <div class="photo-page-label">✦ MORE MEMORIES ✦</div>
        <div class="photo-slot-large">
          <img src="/img_bom/5.jpg" alt="">
        </div>
        <div class="photo-grid-2" style="height:38%; min-height:0;">
          <div class="photo-slot">
            <img src="/img_bom/6.jpg" alt="">
          </div>
          <div class="photo-slot">
            <img src="/img_bom/7.jpg" alt="">
          </div>
        </div>
        <div class="photo-deco">❧</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

    <!-- 페이지 4 : 프로포즈 -->
    <div class="page" id="page-3">
      <div class="page-face propose-front">
        <div class="invite-frame"></div>
        <div id="proposeMain">
          <div class="propose-icon">🗺️</div>
          <div class="propose-title">나와 결혼해줄래?</div>
          <div class="propose-msg">
            지금 이시간도, 당신과 있어서<br><br>
            행복해<br>
            앞으로도 같이<br>
            행복하자<br><br>
            같이 행복할래?
          </div>
          <div class="propose-btns">
            <button class="btn-yes" onclick="onYes()">응, 당연하지! 💛</button>
            <button class="btn-no" id="noBtn">싫어요ㅠ</button>
          </div>
        </div>
        <div class="yes-response" id="yesResponse">
          <img src="/img_bom/totoro.jpg" class="totoro-img" alt="">
          <div class="yes-text">😭💕</div>
          <div class="yes-sub">
            이제 옆을 봐!
          </div>
        </div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

  </div>
</div>

<script>
(function () {
    // ===== 파티클 =====
    var pc = document.getElementById('particles');
    for (var i = 0; i < 18; i++) {
        var p = document.createElement('div');
        p.className = 'particle';
        var w = Math.random() * 8 + 4;
        var h = Math.random() * 6 + 3;
        p.style.cssText = [
            'left:'               + Math.random() * 100 + 'vw',
            'width:'              + w + 'px',
            'height:'             + h + 'px',
            'animation-duration:' + (Math.random() * 10 + 8) + 's',
            'animation-delay:'    + (Math.random() * 8) + 's',
            'opacity:'            + (Math.random() * 0.4 + 0.1)
        ].join(';');
        pc.appendChild(p);
    }

    // ===== 페이지 플립 =====
    var TOTAL   = 4;
    var current = 0;
    var busy    = false;
    var pages   = [];

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
        prevBtn.disabled = (current === 0);
        nextBtn.disabled = (current === TOTAL - 1);
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
        setTimeout(function () { current++; updateUI(); busy = false; }, 880);
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
        setTimeout(function () { updateUI(); busy = false; }, 900);
    };

    document.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight' || e.key === 'ArrowDown') window.goNext();
        if (e.key === 'ArrowLeft'  || e.key === 'ArrowUp')   window.goPrev();
    });

    var touchX = 0;
    document.addEventListener('touchstart', function (e) { touchX = e.touches[0].clientX; }, { passive: true });
    document.addEventListener('touchend', function (e) {
        var diff = touchX - e.changedTouches[0].clientX;
        if (Math.abs(diff) > 50) { diff > 0 ? window.goNext() : window.goPrev(); }
    });

    updateUI();

    // ===== 아니요 버튼 : 마지막 페이지에서만 도망 =====
    var noBtn   = document.getElementById('noBtn');
    var escaped = false;

    function runAway() {
        if (!noBtn || noBtn.style.display === 'none') return;
        if (current !== TOTAL - 1) return;

        if (!escaped) {
            escaped = true;
            noBtn.style.position   = 'fixed';
            noBtn.style.zIndex     = '9998';
            noBtn.style.transition = 'left 0.55s cubic-bezier(0.25, 0.46, 0.45, 0.94), top 0.55s cubic-bezier(0.25, 0.46, 0.45, 0.94)';
            var r = noBtn.getBoundingClientRect();
            noBtn.style.left = r.left + 'px';
            noBtn.style.top  = r.top  + 'px';
        }

        var bw     = noBtn.offsetWidth  || 90;
        var bh     = noBtn.offsetHeight || 42;
        var margin = 20;
        var maxX   = window.innerWidth  - bw - margin;
        var maxY   = window.innerHeight - bh - margin;
        var rect   = noBtn.getBoundingClientRect();
        var attempts = 0;
        var nx, ny;
        do {
            nx = margin + Math.random() * Math.max(0, maxX - margin);
            ny = margin + Math.random() * Math.max(0, maxY - margin);
            attempts++;
        } while (attempts < 15 &&
                 Math.abs(nx - rect.left) < 130 &&
                 Math.abs(ny - rect.top)  < 100);

        nx = Math.max(margin, Math.min(nx, maxX));
        ny = Math.max(margin, Math.min(ny, maxY));
        noBtn.style.left = nx + 'px';
        noBtn.style.top  = ny + 'px';
    }

    document.addEventListener('mousemove', function (e) {
        if (!noBtn || noBtn.style.display === 'none') return;
        if (current !== TOTAL - 1) return;
        var rect = noBtn.getBoundingClientRect();
        var cx   = rect.left + rect.width  / 2;
        var cy   = rect.top  + rect.height / 2;
        var dist = Math.sqrt(Math.pow(e.clientX - cx, 2) + Math.pow(e.clientY - cy, 2));
        if (dist < 100) runAway();
    });

    noBtn.addEventListener('touchstart', function (e) {
        e.preventDefault();
        runAway();
    }, { passive: false });

    noBtn.addEventListener('click', function (e) { e.preventDefault(); runAway(); });

    // ===== YES 응답 =====
    window.onYes = function () {
        document.getElementById('proposeMain').style.display = 'none';
        var yr = document.getElementById('yesResponse');
        yr.style.display = 'flex';
        if (escaped) noBtn.style.display = 'none';

        // 배경을 토토로 숲 색으로 서서히 동화
        document.getElementById('yesOverlay').style.opacity = '1';
        document.body.style.background = 'linear-gradient(135deg, #1e4a2e 0%, #2d6040 35%, #3a7050 65%, #2a5838 100%)';
        document.querySelector('.propose-front').style.background = 'linear-gradient(155deg, #2a5a3a 0%, #3a7050 50%, #2d6040 100%)';

        launchHearts();
    };

    function launchHearts() {
        var symbols = ['💕','💖','💗','💓','💝','🌹','✨','🌸'];
        for (var h = 0; h < 20; h++) {
            (function (idx) {
                setTimeout(function () {
                    var el  = document.createElement('div');
                    var sz  = Math.random() * 20 + 16;
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
                }, idx * 100);
            })(h);
        }
    }

    var hfStyle = document.createElement('style');
    hfStyle.textContent = '@keyframes heartFly{0%{transform:translateY(0) rotate(0);opacity:1}100%{transform:translateY(-100vh) rotate(360deg);opacity:0}}';
    document.head.appendChild(hfStyle);
})();
</script>

</body>
</html>
