<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%-- [2026-09-16 신설] 뉴게시판 공통 상단(헤더+드롭다운+방탭+숨김 더보기 목록).
     기존 dropMenu_header.jsp/menubar_header.jsp/board_left_menu.jsp를 건드리지 않고
     새로 만든 대응 컴포넌트 -- 톤만 바뀌었을 뿐 "드롭다운 메뉴"/"숨겨진 다른 게임 링크"라는
     기능은 그대로 유지한다. PC 전용 좌우 2단 배치 대신 폭과 무관한 단일 세로 배치라
     기존 .boardSection{display:none} 같은 모바일 전용 버그가 구조적으로 없다.
     포함하는 페이지는 반드시 ${room}("free"/"share"/"secret") 모델 속성을 넣어줘야 하고,
     바깥 div는 <div class="nb" data-room="${room}"> 로 감싸야 한다.
     [2026-09-16 버그 수정] "게시판 한글이 다 깨졌어" 신고 -- 이 파일에만 pageEncoding=
     "UTF-8" 지정이 빠져 있었다. <jsp:include>는 include 대상 페이지를 별도 서블릿
     요청으로 실행하기 때문에(정적 include와 달리) 이 파일 자신의 pageEncoding이 그대로
     적용되는데, 지정이 없으면 JSP 기본값(ISO-8859-1)으로 소스를 읽어 컴파일 시점에
     UTF-8 한글 바이트가 깨진 문자로 굳어버린다 -- 이 파일에서 출력하는 텍스트(상단
     브랜드/드롭다운/방탭/더보기 목록)만 깨지고 이 파일을 include하는 페이지 자신의
     텍스트는 멀쩡했던 이유. --%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@400;700&family=IBM+Plex+Sans+KR:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap">
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/newboard.css?v=<%=System.currentTimeMillis()%>">
<!-- Fancybox (기존 사이트와 동일한 라이브러리, 이미지 클릭 확대용) -->
<link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/assets/css/jquery.fancybox.css" media="screen" />

<div class="nb-topbar">
  <div class="nb-topbar-inner">
    <a class="nb-brand" href="<%=request.getContextPath()%>/newboard/free">TH보드<span class="dot">·</span></a>
    <div class="nb-topnav">
      <div class="nb-dd" id="nbBoardDd">
        <button type="button" class="nb-dd-btn" id="nbBoardDdBtn" aria-expanded="false">게시판</button>
        <div class="nb-dd-menu">
          <a href="<%=request.getContextPath()%>/newboard/free"><i class="fa fa-comments-o"></i> 자유게시판</a>
          <a href="<%=request.getContextPath()%>/newboard/share"><i class="fa fa-comments-o"></i> 공유게시판</a>
          <c:if test="${!empty Users.userId}">
          <a href="<%=request.getContextPath()%>/newboard/secret"><i class="fa fa-lock"></i> 비밀게시판</a>
          </c:if>
          <a href="<%=request.getContextPath()%>/rank"><i class="fa fa-trophy"></i> 랭크게시판</a>
        </div>
      </div>
      <a class="nb-authlink" href="<%=request.getContextPath()%>/free" title="기존 게시판(백업)으로 이동">🗄️ 백업</a>
      <c:if test="${empty Users.userId}">
      <a class="nb-authlink" href="<%=request.getContextPath()%>/loginCheck">로그인</a>
      </c:if>
      <c:if test="${!empty Users.userId}">
      <a class="nb-authlink" href="<%=request.getContextPath()%>/logout">로그아웃</a>
      </c:if>
    </div>
  </div>
</div>

<div class="nb-rooms">
  <a class="nb-room-tab" data-current="${room=='free'}" href="<%=request.getContextPath()%>/newboard/free">자유게시판</a>
  <a class="nb-room-tab" data-current="${room=='share'}" href="<%=request.getContextPath()%>/newboard/share">공유게시판</a>
  <c:if test="${!empty Users.userId}">
  <a class="nb-room-tab" data-current="${room=='secret'}" href="<%=request.getContextPath()%>/newboard/secret">🔒 비밀게시판</a>
  </c:if>
</div>

<%-- [기존 board_left_menu.jsp의 hidable/hide 게임 링크 대응] --%>
<div class="nb-more" id="nbMore">
  <button type="button" class="nb-more-toggle" id="nbMoreToggle">다른 페이지 더보기</button>
  <div class="nb-more-list">
    <a href="<%=request.getContextPath()%>/game1">PIANO (pc/mobile)</a>
    <a href="<%=request.getContextPath()%>/game2">find path</a>
    <a href="<%=request.getContextPath()%>/game3">worm tail (pc/mobile)</a>
    <a href="<%=request.getContextPath()%>/game4">round worm (pc/mobile)</a>
    <a href="<%=request.getContextPath()%>/game10">lion ninja (pc only)</a>
    <a href="<%=request.getContextPath()%>/game9">강화기</a>
    <a href="<%=request.getContextPath()%>/game7">phaser3</a>
    <a href="<%=request.getContextPath()%>/ws5">실시간채팅</a>
  </div>
</div>

<script>
(function(){
  var dd = document.getElementById('nbBoardDd');
  var ddBtn = document.getElementById('nbBoardDdBtn');
  ddBtn.addEventListener('click', function(e){
    e.stopPropagation();
    var open = dd.getAttribute('data-open') === 'true';
    dd.setAttribute('data-open', String(!open));
    ddBtn.setAttribute('aria-expanded', String(!open));
  });
  document.addEventListener('click', function(){ dd.removeAttribute('data-open'); ddBtn.setAttribute('aria-expanded','false'); });

  var more = document.getElementById('nbMore');
  document.getElementById('nbMoreToggle').addEventListener('click', function(){
    more.setAttribute('data-open', String(more.getAttribute('data-open') !== 'true'));
  });
})();
</script>
