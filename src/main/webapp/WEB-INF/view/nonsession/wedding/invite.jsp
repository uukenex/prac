<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>&#52397;&#52392;&#51109;</title>
    <link href="https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@300;400;600&family=Cormorant+Garamond:ital,wght@0,300;0,400;1,300&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }

        body {
            min-height: 100vh;
            background: linear-gradient(150deg, #f5e8e4 0%, #fdf4ec 50%, #f8eaf0 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: 'Noto Serif KR', serif;
            padding: 40px 16px;
        }

        /* WIP badge */
        .wip {
            position: fixed;
            top: 18px; right: 18px;
            background: rgba(0,0,0,0.08);
            color: #aaa;
            font-size: 11px;
            padding: 6px 14px;
            border-radius: 20px;
            letter-spacing: 1px;
            z-index: 10;
        }

        /* Invitation card */
        .card {
            background: #fffdf9;
            width: min(380px, 100%);
            border-radius: 2px;
            box-shadow:
                0 2px 8px rgba(180, 120, 100, 0.1),
                0 20px 80px rgba(180, 100, 100, 0.15);
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 56px 40px 48px;
            gap: 22px;
            position: relative;
            overflow: hidden;
        }

        /* Decorative corner lines */
        .card::before,
        .card::after {
            content: '';
            position: absolute;
            border: 1px solid rgba(180, 140, 120, 0.25);
        }
        .card::before {
            inset: 12px;
            border-radius: 1px;
        }

        .top-ornament {
            font-size: 13px;
            letter-spacing: 10px;
            color: #b09070;
            font-family: 'Cormorant Garamond', serif;
        }

        .sub-label {
            font-size: 10px;
            letter-spacing: 5px;
            color: #c0a890;
            font-family: 'Cormorant Garamond', serif;
        }

        .divider {
            width: 100px;
            height: 1px;
            background: linear-gradient(to right, transparent, #c0a890, transparent);
        }

        .divider-sm {
            width: 60px;
            height: 1px;
            background: linear-gradient(to right, transparent, #c0a890, transparent);
        }

        /* Photo placeholder */
        .photo-area {
            width: 120px;
            height: 120px;
            border-radius: 50%;
            background: linear-gradient(135deg, #f5e4e0, #ead4d0);
            border: 1px solid #d4b0a8;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 40px;
            color: rgba(160,100,90,0.4);
        }

        /* Names */
        .names-row {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .name {
            font-size: 24px;
            color: #3a2828;
            font-weight: 600;
            letter-spacing: 4px;
        }

        .name-sub {
            font-size: 11px;
            color: #a09080;
            letter-spacing: 2px;
            margin-top: 3px;
            text-align: center;
        }

        .amp {
            font-family: 'Cormorant Garamond', serif;
            font-size: 28px;
            color: #b09070;
            font-style: italic;
        }

        /* Date / Time / Venue */
        .info-label {
            font-size: 9px;
            letter-spacing: 4px;
            color: #b0a090;
        }

        .info-main {
            font-size: 18px;
            color: #3a2828;
            letter-spacing: 2px;
            margin-top: 4px;
        }

        .info-sub {
            font-size: 12px;
            color: #806858;
            margin-top: 4px;
            letter-spacing: 1px;
        }

        /* Map placeholder */
        .map-box {
            width: 100%;
            height: 150px;
            background: linear-gradient(135deg, #f5ece8, #e8dcd8);
            border-radius: 4px;
            border: 1px dashed #c8b0a0;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 6px;
            color: #a09088;
            font-size: 12px;
            letter-spacing: 2px;
        }

        .map-icon { font-size: 26px; opacity: 0.45; }

        /* Message */
        .message {
            font-size: 13px;
            color: #706050;
            line-height: 2;
            text-align: center;
            font-weight: 300;
        }

        /* RSVP placeholder */
        .rsvp-area {
            width: 100%;
            background: linear-gradient(135deg, #faf0ec, #f5e8e4);
            border-radius: 4px;
            border: 1px solid #dfc8c0;
            padding: 18px;
            text-align: center;
            display: flex;
            flex-direction: column;
            gap: 8px;
        }

        .rsvp-label {
            font-size: 9px;
            letter-spacing: 4px;
            color: #b09080;
        }

        .rsvp-phone {
            font-size: 16px;
            color: #3a2828;
            letter-spacing: 3px;
        }

        .rsvp-note {
            font-size: 10px;
            color: #a09080;
            letter-spacing: 1px;
        }

        .bottom-ornament {
            font-size: 13px;
            letter-spacing: 10px;
            color: #b09070;
            font-family: 'Cormorant Garamond', serif;
        }
    </style>
</head>
<body>

<div class="wip">&#128679; &#51089;&#50629;&#51473;</div>

<div class="card">

    <div class="top-ornament">&#10022; &#10022; &#10022;</div>
    <div class="sub-label">WEDDING INVITATION</div>

    <div class="divider"></div>

    <!-- Couple photo placeholder -->
    <div class="photo-area">&#128149;</div>

    <!-- Names -->
    <div>
        <div class="names-row">
            <div style="text-align:center;">
                <div class="name">&#54869;&#44600;&#46041;</div>
                <div class="name-sub">&#49888;&#47791; &#51060;&#47492;</div>
            </div>
            <div class="amp">&amp;</div>
            <div style="text-align:center;">
                <div class="name">&#51060;&#49344;&#48176;</div>
                <div class="name-sub">&#49888;&#48512; &#51060;&#47492;</div>
            </div>
        </div>
    </div>

    <div class="divider"></div>

    <!-- Date -->
    <div style="text-align:center;">
        <div class="info-label">DATE &amp; TIME</div>
        <div class="info-main">2025&#45380; 00&#50900; 00&#51068;</div>
        <div class="info-sub">&#50724;&#54980; 0&#49884; 00&#48516;</div>
    </div>

    <div class="divider-sm"></div>

    <!-- Venue -->
    <div style="text-align:center;">
        <div class="info-label">VENUE</div>
        <div class="info-main">&#50937;&#46377;&#54856; &#51060;&#47492;</div>
        <div class="info-sub">&#49436;&#50872;&#53945;&#ubcc4;&#49884; &#50612;&#46356;&#44032;</div>
    </div>

    <!-- Map -->
    <div class="map-box">
        <div class="map-icon">&#128205;</div>
        <div>&#51648;&#46020; &#50601;&#50669;</div>
    </div>

    <div class="divider"></div>

    <!-- Message -->
    <div class="message">
        &#49436;&#47196;&#44032; &#49436;&#47196;&#50640;&#44172; &#44032;&#51109;<br>
        &#49548;&#51473;&#54620; &#49324;&#46993;&#51060; &#46104;&#50612;<br>
        &#54217;&#49373;&#51012; &#54632;&#44760; &#44077;&#44192;&#49845;&#45768;&#45796;.
    </div>

    <!-- RSVP -->
    <div class="rsvp-area">
        <div class="rsvp-label">RSVP</div>
        <div class="rsvp-phone">010-0000-0000</div>
        <div class="rsvp-note">&#52384;&#49437; &#50668;&#48512;&#47484; &#48277;&#47556; 00&#51068; &#51204;&#44620;&#51648; &#50504;&#47244;&#54644; &#51452;&#49464;&#50836;</div>
    </div>

    <div class="divider"></div>

    <div class="bottom-ornament">&#10022; &#10022; &#10022;</div>

</div>

</body>
</html>
