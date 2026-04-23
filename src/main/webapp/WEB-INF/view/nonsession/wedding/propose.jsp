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
            background: linear-gradient(135deg, #fff0f5 0%, #fde8f0 35%, #fff5f0 65%, #fef0f8 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Noto Serif KR', serif;
            overflow: hidden;
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
            background: rgba(220, 100, 140, 0.25);
            animation: floatPetal linear infinite;
        }

        @keyframes floatPetal {
            0%   { transform: translateY(100vh) rotate(0deg);   opacity: 0; }
            10%  { opacity: 0.7; }
            90%  { opacity: 0.4; }
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
            box-shadow: 3px 3px 24px rgba(180, 80, 120, 0.18), 8px 8px 40px rgba(0,0,0,0.10);
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
            background: linear-gradient(145deg, #fdf0f4 0%, #f8e4ec 100%);
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
                rgba(220, 140, 160, 0.07) 14px,
                rgba(220, 140, 160, 0.07) 28px
            );
        }

        /* 제본 그림자 */
        .page-face::before {
            content: '';
            position: absolute;
            top: 0; left: 0; bottom: 0;
            width: 20px;
            background: linear-gradient(to right, rgba(180,80,120,0.10), transparent);
            z-index: 2;
            pointer-events: none;
        }

        /* ===== 내비게이션 ===== */
        .nav-btn {
            position: fixed;
            top: 50%;
            transform: translateY(-50%);
            background: rgba(200, 80, 130, 0.12);
            backdrop-filter: blur(4px);
            border: 1px solid rgba(200, 80, 130, 0.22);
            color: #c0406a;
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

        .nav-btn:hover  { background: rgba(200, 80, 130, 0.22); }
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
            background: rgba(200, 100, 140, 0.25);
            transition: background 0.3s, transform 0.3s;
        }

        .dot.active {
            background: rgba(200, 60, 110, 0.75);
            transform: scale(1.4);
        }

        /* ===== 페이지 1 : 커버 / 로그인 ===== */
        .p1-front {
            background: linear-gradient(160deg, #fce4ec 0%, #f8bbd0 45%, #f48fb1 100%);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 40px 32px;
            gap: 0;
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
            color: #7b1040;
            text-shadow: 0 2px 16px rgba(255,255,255,0.6);
            margin-bottom: 6px;
        }

        .p1-subtitle {
            font-size: 11px;
            color: rgba(100, 20, 50, 0.6);
            letter-spacing: 3px;
            margin-bottom: 36px;
        }

        .p1-form { width: 100%; display: flex; flex-direction: column; gap: 16px; }

        .p1-label {
            display: block;
            font-size: 10px;
            color: rgba(100, 20, 50, 0.65);
            letter-spacing: 2px;
            margin-bottom: 6px;
        }

        .p1-input {
            width: 100%;
            padding: 13px 16px;
            background: rgba(255, 255, 255, 0.65);
            border: 1px solid rgba(180, 60, 100, 0.25);
            border-radius: 8px;
            color: #4a1030;
            font-size: 14px;
            font-family: 'Noto Serif KR', serif;
            outline: none;
            transition: border-color 0.3s, background 0.3s;
        }

        .p1-input:focus {
            border-color: rgba(180, 60, 100, 0.55);
            background: rgba(255, 255, 255, 0.85);
        }

        .p1-input::placeholder { color: rgba(100, 40, 60, 0.35); }

        .p1-btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #e91e63, #880e4f);
            border: none;
            border-radius: 8px;
            color: white;
            font-size: 15px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 4px 20px rgba(233, 30, 99, 0.35);
            transition: transform 0.2s, box-shadow 0.2s;
            margin-top: 6px;
        }

        .p1-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 7px 26px rgba(233, 30, 99, 0.5);
        }

        .p1-hint {
            position: absolute;
            bottom: 22px;
            font-size: 10px;
            color: rgba(100, 20, 50, 0.35);
            letter-spacing: 2px;
        }

        /* ===== 사진 페이지 ===== */
        .photo-page {
            background: linear-gradient(150deg, #fff8f9 0%, #fef0f4 50%, #fff5f8 100%);
            padding: 26px 22px 20px;
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .photo-page-label {
            text-align: center;
            font-size: 10px;
            color: #c06080;
            letter-spacing: 4px;
        }

        .photo-grid-2 {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            flex: 1;
            min-height: 0;
        }

        .photo-slot {
            background: linear-gradient(135deg, #fde8ee, #f8d8e4);
            border: 2px dashed #e0a0b8;
            border-radius: 10px;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: #b07888;
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
            color: #b08090;
            text-align: center;
            font-style: italic;
        }

        .photo-deco { text-align: center; font-size: 16px; color: #e0a0b8; }

        /* ===== 마지막 페이지 : 프로포즈 ===== */
        .propose-front {
            background: linear-gradient(155deg, #fff0f5 0%, #fce4ec 45%, #f8bbd0 100%);
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
            color: #8b1040;
            font-weight: 600;
            margin-bottom: 20px;
            line-height: 1.4;
        }

        .propose-msg {
            font-size: 14px;
            color: #5a2838;
            line-height: 2;
            font-weight: 300;
            margin-bottom: 36px;
        }

        .propose-btns { display: flex; gap: 16px; align-items: center; }

        .btn-yes {
            padding: 14px 34px;
            background: linear-gradient(135deg, #e91e63, #880e4f);
            border: none;
            border-radius: 50px;
            color: white;
            font-size: 16px;
            font-family: 'Noto Serif KR', serif;
            cursor: pointer;
            box-shadow: 0 6px 24px rgba(233, 30, 99, 0.4);
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .btn-yes:hover {
            transform: scale(1.06);
            box-shadow: 0 8px 30px rgba(233, 30, 99, 0.6);
        }

        .btn-no {
            padding: 12px 26px;
            background: transparent;
            border: 2px solid #d4a0b8;
            border-radius: 50px;
            color: #b08098;
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
            color: #8b1040;
            font-weight: 600;
        }

        .yes-sub {
            font-size: 13px;
            color: #9b4060;
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

    <!-- 페이지 1 : 커버 / 로그인 -->
    <div class="page" id="page-0">
      <div class="page-face p1-front">
        <div class="p1-heart">💌</div>
        <div class="p1-title">For You</div>
        <div class="p1-subtitle">당신에게 전하는 이야기</div>
        <div class="p1-form">
          <div>
            <label class="p1-label">이름</label>
            <input class="p1-input" type="text" placeholder="당신의 이름을 입력해주세요">
          </div>
          <div>
            <label class="p1-label">비밀번호</label>
            <input class="p1-input" type="password" placeholder="••••••••">
          </div>
          <button class="p1-btn" onclick="goNext()">열어보기 💌</button>
        </div>
        <div class="p1-hint">→ 눌러서 열어보세요</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

    <!-- 페이지 2 : 사진 -->
    <div class="page" id="page-1">
      <div class="page-face photo-page">
        <div class="photo-page-label">✦ OUR MOMENTS ✦</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <!-- 사진 교체: <img src="/img_wedding/propose1.jpg" alt=""> -->
            <div class="photo-slot-icon">📷</div>
            <div>사진 1</div>
          </div>
          <div class="photo-slot">
            <!-- 사진 교체: <img src="/img_wedding/propose2.jpg" alt=""> -->
            <div class="photo-slot-icon">📷</div>
            <div>사진 2</div>
          </div>
        </div>
        <div class="photo-caption">우리가 함께한 소중한 순간</div>
        <div class="photo-grid-2" style="flex:1; min-height:0;">
          <div class="photo-slot">
            <!-- 사진 교체: <img src="/img_wedding/propose3.jpg" alt=""> -->
            <div class="photo-slot-icon">📷</div>
            <div>사진 3</div>
          </div>
          <div class="photo-slot">
            <!-- 사진 교체: <img src="/img_wedding/propose4.jpg" alt=""> -->
            <div class="photo-slot-icon">📷</div>
            <div>사진 4</div>
          </div>
        </div>
        <div class="photo-deco">❧</div>
      </div>
      <div class="page-face page-back">
        <div class="page-back-inner">🌸</div>
      </div>
    </div>

    <!-- 페이지 3 : 프로포즈 -->
    <div class="page" id="page-2">
      <div class="page-face propose-front">
        <div id="proposeMain">
          <div class="propose-icon">💍</div>
          <div class="propose-title">결혼해줄래요?</div>
          <div class="propose-msg">
            당신과 함께한 모든 순간이<br>
            내 인생 최고의 선물이었어요.<br><br>
            앞으로도 영원히<br>
            당신 곁에 있고 싶어요.
          </div>
          <div class="propose-btns">
            <button class="btn-yes" onclick="onYes()">💕 응, 할게요!</button>
            <button class="btn-no" id="noBtn">아니요...</button>
          </div>
        </div>
        <div class="yes-response" id="yesResponse">
          <div class="yes-emoji">🥰</div>
          <div class="yes-text">사랑해요! 💕</div>
          <div class="yes-sub">
            행복하게 해줄게요.<br>
            영원히 함께해요.
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
    var TOTAL   = 3;
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

    // ===== 아니요 버튼 : 마우스 근접 시 도망 =====
    var noBtn   = document.getElementById('noBtn');
    var escaped = false;

    function runAway() {
        if (!noBtn || noBtn.style.display === 'none') return;

        if (!escaped) {
            escaped = true;
            noBtn.style.position   = 'fixed';
            noBtn.style.zIndex     = '9998';
            noBtn.style.transition = 'left 0.15s ease, top 0.15s ease';
            // 초기 위치 잡기
            var r = noBtn.getBoundingClientRect();
            noBtn.style.left = r.left + 'px';
            noBtn.style.top  = r.top  + 'px';
        }

        var bw = noBtn.offsetWidth  || 90;
        var bh = noBtn.offsetHeight || 42;
        var margin = 50;
        var rect   = noBtn.getBoundingClientRect();
        var attempts = 0;
        var nx, ny;
        do {
            nx = margin + Math.random() * (window.innerWidth  - bw - margin * 2);
            ny = margin + Math.random() * (window.innerHeight - bh - margin * 2);
            attempts++;
        } while (attempts < 15 &&
                 Math.abs(nx - rect.left) < 130 &&
                 Math.abs(ny - rect.top)  < 100);

        noBtn.style.left = nx + 'px';
        noBtn.style.top  = ny + 'px';
    }

    // 마우스가 100px 이내 접근하면 도망
    document.addEventListener('mousemove', function (e) {
        if (!noBtn || noBtn.style.display === 'none') return;
        var rect = noBtn.getBoundingClientRect();
        var cx   = rect.left + rect.width  / 2;
        var cy   = rect.top  + rect.height / 2;
        var dist = Math.sqrt(Math.pow(e.clientX - cx, 2) + Math.pow(e.clientY - cy, 2));
        if (dist < 100) runAway();
    });

    // 모바일 터치 시 도망
    noBtn.addEventListener('touchstart', function (e) {
        e.preventDefault();
        runAway();
    }, { passive: false });

    // 혹시 click이 발생해도 차단
    noBtn.addEventListener('click', function (e) { e.preventDefault(); runAway(); });

    // ===== YES 응답 =====
    window.onYes = function () {
        document.getElementById('proposeMain').style.display = 'none';
        var yr = document.getElementById('yesResponse');
        yr.style.display = 'flex';
        if (escaped) noBtn.style.display = 'none';
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
