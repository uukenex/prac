<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>


<!-- Fancy Header -->

	<header id="fancyHeader" class="fancyAlt">
		 
		<nav>
			<a href="#fancyMenu">Menu</a>
		</nav>
	</header>


<!-- Fancy Menu Box -->

	<nav id="fancyMenu">
		<div class="fancyInner">
			<h2>Menu</h2>
			<ul class="links">
				<c:url value="/login" var="login" />
				<c:if test="${!empty Users.userId }">
					<li><a href="/logout">Log OUT</a></li>
				</c:if>
				<c:if test="${empty Users.userId}">
					<li><a href="/loginCheck">Log In</a></li>
					<c:url value="/join" var="join" />
					<li>
						<a href="#" onclick="window.open('/join', 'win1', 'width=560, height=680');" onkeypress="this.onclick()">
							사이트 회원 가입
						</a>
					</li>
				</c:if>	
				<%-- [2026-09-16 변경] 뉴게시판을 기본으로, 기존 게시판은 백업 링크로만 유지. --%>
				<c:url value="/newboard/free"  var="free"/>
				<c:url value="/newboard/share" var="share"/>
				<c:url value="/newboard/secret" var="secret"/>
				<c:url value="/free" var="oldFree"/>
					<li><a href="${free } ">자유게시판</a></li>
					<li><a href="${share }">공유게시판</a></li>
					<c:if test="${!empty Users.userId }">
					<li><a href="${secret }">비밀게시판</a></li>
					</c:if>
					<li><a href="${oldFree }">🗄️ 예전 디자인(백업)</a></li>
			</ul>
			<a href="#" class="close">Close</a>
		</div>
	</nav>
	
