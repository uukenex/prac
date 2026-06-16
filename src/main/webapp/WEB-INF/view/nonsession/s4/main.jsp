<%@ page pageEncoding="UTF-8" %>
<%@ page import="java.util.HashMap" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>람쥐봇 낚시</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    min-height: 100vh;
    background: linear-gradient(180deg, #0d1b2a 0%, #1a3a4a 60%, #0d2030 100%);
    font-family: 'Noto Sans KR', sans-serif;
    color: #e0e8f0;
  }
  /* 헤더 */
  header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 14px 20px;
    background: rgba(0,0,0,0.3);
    border-bottom: 1px solid rgba(255,255,255,0.08);
  }
  header .logo { color: #c9a96e; font-weight: 900; font-size: 18px; }
  header .user { font-size: 13px; color: #8090a0; }
  header .logout-btn {
    background: none; border: 1px solid #334; color: #7a8ba0;
    border-radius: 8px; padding: 5px 12px; cursor: pointer; font-size: 12px;
  }
  header .logout-btn:hover { color: #c0d0e0; border-color: #556; }

  /* 탭 */
  .tabs {
    display: flex;
    border-bottom: 1px solid rgba(255,255,255,0.08);
    background: rgba(0,0,0,0.2);
  }
  .tab-btn {
    flex: 1; padding: 13px; background: none; border: none; color: #7a8ba0;
    font-size: 14px; font-weight: 600; cursor: pointer; transition: all .15s;
    border-bottom: 2px solid transparent;
  }
  .tab-btn.active { color: #c9a96e; border-bottom-color: #c9a96e; }

  /* 탭 패널 */
  .tab-panel { display: none; padding: 24px 20px; }
  .tab-panel.active { display: block; }

  /* 낚시 패널 */
  .fishing-scene {
    text-align: center; padding: 32px 0 24px;
  }
  .water {
    font-size: 80px; display: block; margin-bottom: 8px;
    animation: bob 3s ease-in-out infinite;
  }
  @keyframes bob { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-6px)} }
  .fish-btn {
    background: linear-gradient(135deg, #1e6fa0, #2a9fd6);
    color: #fff; border: none; border-radius: 16px;
    padding: 16px 48px; font-size: 18px; font-weight: 800;
    cursor: pointer; margin-top: 24px;
    box-shadow: 0 4px 20px rgba(42,159,214,.4);
    transition: transform .1s, box-shadow .1s;
  }
  .fish-btn:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 24px rgba(42,159,214,.5); }
  .fish-btn:disabled { background: #334; color: #556; cursor: default; box-shadow: none; }
  .equip-info { font-size: 12px; color: #5a7080; margin-top: 12px; }

  /* 결과 박스 */
  .result-box {
    display: none;
    background: rgba(255,255,255,0.05);
    border: 1px solid rgba(201,169,110,0.3);
    border-radius: 16px; padding: 20px; margin-top: 20px;
    white-space: pre-line; font-size: 15px; line-height: 1.7;
    color: #d0e8ff; text-align: left;
    animation: fadeIn .4s ease;
  }
  @keyframes fadeIn { from{opacity:0;transform:translateY(8px)} to{opacity:1;transform:translateY(0)} }
  .result-box.show { display: block; }

  /* 낚시가방 */
  .bag-loading { text-align: center; color: #5a7080; padding: 40px; }
  .grade-section { margin-bottom: 20px; }
  .grade-title {
    font-size: 13px; font-weight: 700; color: #c9a96e;
    margin-bottom: 8px; border-bottom: 1px solid rgba(201,169,110,.2); padding-bottom: 6px;
  }
  .fish-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 8px 12px; border-radius: 8px; margin-bottom: 4px;
    background: rgba(255,255,255,0.04);
  }
  .fish-item .name { font-size: 14px; }
  .fish-item .qty { font-size: 13px; color: #8090a0; }
  .empty-msg { text-align: center; color: #5a7080; padding: 40px 0; font-size: 14px; }

  /* 업적 */
  .ach-section { margin-top: 20px; }
  .ach-title { font-size: 13px; font-weight: 700; color: #8090a0; margin-bottom: 8px; }
  .ach-item {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 12px; font-size: 13px; color: #a0b0c0;
  }
  .spinner {
    display: inline-block; width: 18px; height: 18px;
    border: 2px solid rgba(255,255,255,0.2); border-top-color: #2a9fd6;
    border-radius: 50%; animation: spin .7s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>
<%
  String nickname = (String) request.getAttribute("nickname");
  String userName = (String) request.getAttribute("userName");
  boolean todayFished = Boolean.TRUE.equals(request.getAttribute("todayFished"));
  HashMap<?,?> equip = (HashMap<?,?>) request.getAttribute("equip");
  int rodGrade    = equip != null && equip.get("ROD_GRADE")    != null ? ((Number)equip.get("ROD_GRADE")).intValue()    : 1;
  int bobberGrade = equip != null && equip.get("BOBBER_GRADE") != null ? ((Number)equip.get("BOBBER_GRADE")).intValue() : 1;
  String[] rodNames    = {"","일반낚시대","고급낚시대","희귀낚시대","영웅낚시대","전설낚시대"};
  String[] bobberNames = {"","일반찌","고급찌","희귀찌","영웅찌"};
  String rodName    = rodGrade    < rodNames.length    ? rodNames[rodGrade]    : "낚시대";
  String bobberName = bobberGrade < bobberNames.length ? bobberNames[bobberGrade] : "찌";
%>

<header>
  <span class="logo">🎣 람쥐봇 낚시</span>
  <span class="user"><%= nickname %>님</span>
  <button class="logout-btn" onclick="location.href='<%=request.getContextPath()%>/s4/logout'">로그아웃</button>
</header>

<div class="tabs">
  <button class="tab-btn active" data-tab="fishing">🎣 낚시하기</button>
  <button class="tab-btn" data-tab="bag" id="bagTabBtn">🐟 낚시가방</button>
</div>

<!-- 낚시 탭 -->
<div class="tab-panel active" id="tab-fishing">
  <div class="fishing-scene">
    <span class="water" id="fishIcon">🌊</span>
    <br>
    <button class="fish-btn" id="fishBtn" <%= todayFished ? "disabled" : "" %>>
      <%= todayFished ? "오늘 낚시 완료 ✔" : "낚시하기 🎣" %>
    </button>
    <p class="equip-info">장비: <%= rodName %> / <%= bobberName %></p>
  </div>
  <div class="result-box" id="resultBox"></div>
</div>

<!-- 낚시가방 탭 -->
<div class="tab-panel" id="tab-bag">
  <div id="bagContent">
    <p class="bag-loading">탭을 클릭하면 가방을 불러옵니다.</p>
  </div>
</div>

<script>
var ctx = '<%=request.getContextPath()%>';

// 탭 전환
document.querySelectorAll('.tab-btn').forEach(function(btn) {
  btn.addEventListener('click', function() {
    document.querySelectorAll('.tab-btn').forEach(function(b) { b.classList.remove('active'); });
    document.querySelectorAll('.tab-panel').forEach(function(p) { p.classList.remove('active'); });
    btn.classList.add('active');
    var tab = btn.getAttribute('data-tab');
    document.getElementById('tab-' + tab).classList.add('active');
    if (tab === 'bag') loadBag();
  });
});

// 낚시하기
document.getElementById('fishBtn').addEventListener('click', function() {
  var btn = this;
  btn.disabled = true;
  btn.textContent = '🎣 낚시 중...';
  document.getElementById('fishIcon').textContent = '🌀';

  fetch(ctx + '/s4/api/fishing', { method: 'POST' })
    .then(function(r) { return r.json(); })
    .then(function(data) {
      var box = document.getElementById('resultBox');
      if (data.error) {
        box.textContent = data.error;
      } else {
        // NL 구분자(♬)를 줄바꿈으로 변환
        box.textContent = data.message ? data.message.replace(/♬/g, '\n') : '';
        document.getElementById('fishIcon').textContent = '🐟';
        btn.textContent = '오늘 낚시 완료 ✔';
      }
      box.classList.add('show');
    })
    .catch(function() {
      document.getElementById('resultBox').textContent = '오류가 발생했습니다. 다시 시도해주세요.';
      document.getElementById('resultBox').classList.add('show');
      document.getElementById('fishIcon').textContent = '🌊';
      btn.disabled = false;
      btn.textContent = '낚시하기 🎣';
    });
});

// 낚시가방 로드
var bagLoaded = false;
function loadBag() {
  if (bagLoaded) return;
  bagLoaded = true;
  var content = document.getElementById('bagContent');
  content.innerHTML = '<p class="bag-loading"><span class="spinner"></span></p>';

  fetch(ctx + '/s4/api/bag')
    .then(function(r) { return r.json(); })
    .then(function(data) {
      if (data.error) { content.innerHTML = '<p class="empty-msg">' + data.error + '</p>'; return; }
      var inv = data.inv || [];
      if (inv.length === 0) {
        content.innerHTML = '<p class="empty-msg">아직 잡은 물고기가 없습니다.<br>/낚시하기 버튼으로 낚시를 시작하세요!</p>';
        return;
      }
      var html = '';
      var EMOJI_MAP = {
        101:'🐟',102:'🐠',103:'🐍',104:'🐟',105:'🐡',106:'🐟',
        201:'🐟',202:'🐈',203:'❄',204:'🐊',205:'🦪',206:'🦪',
        301:'🌊',302:'🐟',303:'🐟',304:'🦪',305:'🐚',306:'🦀'
      };
      var curGrade = -1;
      var starMap = ['','★','★★','★★★','★★★★','★★★★★','★★★★★★','★★★★★★★','★★★★★★★★'];
      inv.forEach(function(f) {
        var grade = parseInt(f.FISH_GRADE) || 1;
        if (grade !== curGrade) {
          if (curGrade !== -1) html += '</div>';
          html += '<div class="grade-section"><div class="grade-title">' + (starMap[grade]||grade) + ' 등급</div>';
          curGrade = grade;
        }
        var id = parseInt(f.FISH_ID);
        var emoji = EMOJI_MAP[id] || '🐟';
        html += '<div class="fish-item"><span class="name">' + emoji + ' ' + f.FISH_NAME + '</span><span class="qty">x' + f.QTY + '</span></div>';
      });
      if (curGrade !== -1) html += '</div>';

      var achs = data.achs || [];
      if (achs.length > 0) {
        html += '<div class="ach-section"><div class="ach-title">🏆 달성 업적</div>';
        achs.forEach(function(a) {
          html += '<div class="ach-item">✅ ' + a.ACH_NAME + '</div>';
        });
        html += '</div>';
      }
      content.innerHTML = html;
    })
    .catch(function() {
      content.innerHTML = '<p class="empty-msg">불러오기 실패. 새로고침해주세요.</p>';
    });
}
</script>
</body>
</html>
