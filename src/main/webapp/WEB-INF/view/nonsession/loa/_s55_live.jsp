<%@ page pageEncoding="UTF-8" %><%--
  [2026-11-03] 시즌 5.5 -- 새 빌드 감지(강력 새로고침) + 1분 넘게 통신이 없으면 재접속 화면. tower_view.jsp와 광산 웹앱(s55_mine.jsp)이 <body> 바로 뒤에 정적 포함.
  pageBuild(요청 속성)가 있어야 한다.
--%>
<!-- [2026-10-26] "구버전에서 업데이트 안 되는 사람, 새로고침해도 신버전이 안 된다" -- 이 화면의 빌드 번호를 서버(/api/tower-page-build, 배포마다 바뀜)와 비교.
     열자마자 다르면(브라우저/카톡 웹뷰가 예전 화면을 저장해 둠) 주소에 ?_b=새번호를 붙여 바로 다시 불러오고(주소가 달라 저장본을 못 씀),
     보는 도중 새로 배포되면 위쪽에 "새 버전" 띠를 띄워 누르면 다시 불러온다(놀이 중에 갑자기 끊지 않게). 다른 스크립트와 따로 돌아 예전 화면이 깨져 있어도 동작. -->
<script>
(function () {
  var MY = '<%= request.getAttribute("pageBuild") == null ? "" : request.getAttribute("pageBuild") %>';
  function freshUrl(b) {
    var u = location.pathname + location.search.replace(/([?&])_b=[^&]*&?/, '$1').replace(/[?&]$/, '');
    return u + (u.indexOf('?') >= 0 ? '&' : '?') + '_b=' + encodeURIComponent(b) + location.hash;
  }
  // [2026-10-30] "서버와 1분 이상 통신이 안 되면(백그라운드 등) 접속 종료 멘트 후 다시 접속, 배포 때도" --
  //  모든 fetch의 성공/실패 시각을 적어 둔다. 광산이 열려 있으면 1분 넘게 성공한 응답이 없을 때(창이 숨어 있다 돌아온 경우 포함),
  //  광산 밖에선 실패가 이어져 1분이 넘을 때(서버 재시작/배포) 화면을 덮고 서버가 답할 때까지 3초마다 물어본 뒤 다시 불러온다(광산이 열려 있었으면 ?go=mine으로 광산까지).
  var lastOk = Date.now(), lastFail = 0, lost = false, LOST_MS = 60 * 1000;
  window.twAlive = function () { lastOk = Date.now(); }; // [2026-11-03] 광산 WebSocket 응답도 "살아 있음"으로
  if (window.fetch) {
    var of = window.fetch;
    window.fetch = function () {
      return of.apply(this, arguments).then(function (r) { if (r && r.status < 500) lastOk = Date.now(); else lastFail = Date.now(); return r; },
        function (e) { lastFail = Date.now(); throw e; });
    };
  }
  function mineOn() { var o = document.getElementById('mineOverlay'); return !!(o && o.classList.contains('open')); }
  function goUrl(b) {
    var q = new URLSearchParams(location.search);
    q.delete('_b'); q.delete('go');
    if (b) q.set('_b', b);
    if (mineOn()) q.set('go', 'mine'); // 광산에 있었으면 다시 광산으로
    var qs = q.toString();
    return location.pathname + (qs ? '?' + qs : '') + location.hash;
  }
  function lostCheck() {
    if (lost || document.hidden) return;
    var gap = Date.now() - lastOk;
    if (gap <= LOST_MS || !(mineOn() || lastFail > lastOk)) return;
    lost = true;
    var el = document.createElement('div');
    el.style.cssText = 'position:fixed;inset:0;z-index:100000;background:rgba(10,8,20,.82);display:flex;align-items:center;justify-content:center;padding:16px;';
    el.innerHTML = '<div style="background:#221c33;color:#fff;border:2px solid #f4f0e6;border-radius:14px;padding:18px 20px;max-width:320px;text-align:center;box-shadow:0 6px 18px rgba(0,0,0,.5);">'
        + '<div style="font-size:30px;margin-bottom:6px;">🔌</div><div style="font-weight:800;font-size:15px;margin-bottom:6px;">서버와의 접속이 끊겼어요</div>'
        + '<div id="lostSub" style="font-size:13px;color:#cfc8e8;line-height:1.5;">1분 넘게 서버와 통신하지 못했어요.<br>다시 접속하는 중...</div></div>';
    document.body.appendChild(el);
    (function retry() {
      of('/loa/api/tower-page-build?t=' + Date.now(), { cache: 'no-store', credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (d) {
        var sub = document.getElementById('lostSub');
        if (sub) sub.innerHTML = '서버에 다시 연결됐어요.<br>' + (mineOn() ? '광산에 다시 들어가요...' : '화면을 다시 불러와요...');
        setTimeout(function () { location.replace(goUrl(d && d.build)); }, 1500);
      }).catch(function () {
        var sub = document.getElementById('lostSub');
        if (sub) sub.innerHTML = '서버가 아직 응답하지 않아요(점검/업데이트 중일 수 있어요).<br>3초마다 다시 시도하는 중...';
        setTimeout(retry, 3000);
      });
    })();
  }
  setInterval(lostCheck, 5000);
  document.addEventListener('visibilitychange', function () { if (!document.hidden) lostCheck(); });
  /** [2026-11-03] "업데이트가 있을 땐 강력 새로고침" -- 새 빌드 번호 주소(?_b=, 화면 HTML은 저장 안 함 + CSS/JS도 ?v=새 번호)로 다시 받고, 저장해 둔 맵 정보도 비운다. */
  function hardReload(b) {
    try { Object.keys(localStorage).forEach(function (k) { if (k.indexOf('tw-mine-map') === 0) localStorage.removeItem(k); }); } catch (e) { }
    location.replace(goUrl(b));
  }
  function bar(b, auto) {
    if (document.getElementById('newBuildBar')) return;
    var el = document.createElement('div');
    el.id = 'newBuildBar';
    el.style.cssText = 'position:fixed;left:50%;top:8px;transform:translateX(-50%);z-index:99999;background:#2F8F5C;color:#fff;font-weight:800;font-size:13px;padding:8px 14px;border:2px solid #fff;border-radius:999px;box-shadow:0 4px 12px rgba(0,0,0,.35);cursor:pointer;';
    el.textContent = auto ? '🔄 새 버전으로 업데이트돼요 -- 1초 뒤 자동 새로고침' : '🔄 새 버전이 나왔어요 -- 눌러서 새로고침';
    el.onclick = function () { hardReload(b); };
    document.body.appendChild(el);
  }
  function check(first) {
    if (!window.fetch) return;
    fetch('/loa/api/tower-page-build?t=' + Date.now(), { cache: 'no-store', credentials: 'same-origin' })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (!d || !d.build || d.build === MY) return;
        if (location.search.indexOf('_b=' + d.build) >= 0) { bar(d.build); return; } // 같은 번호로 다시 왔는데도 다르면(저장본) 멈추고 띠만
        if (first) { location.replace(freshUrl(d.build)); return; }
        // [2026-10-29] "패치되면 강제로 새로고침" -- 보는 도중 새로 배포되면 5초 안내 뒤 자동으로 다시 불러온다(광산은 pagehide에서 위치를 저장)
        bar(d.build, true);
        setTimeout(function () { hardReload(d.build); }, 1000); // [2026-11-11] 5초 -> 1초, [2026-10-30] 3초 -> 5초, 광산에 있었으면 광산까지(goUrl)
      }).catch(function () { });
  }
  check(true);
  setInterval(function () { if (!document.hidden) check(false); }, 60 * 1000); // [2026-10-29] 5분 -> 1분(배포 뒤 빨리 새로고침)
  document.addEventListener('visibilitychange', function () { if (!document.hidden) check(false); });
})();
</script>
