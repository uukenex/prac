<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>


<!-- Fancy Header -->

	<header id="fancyHeader" class="fancyAlt">
		 
		<h1>현재 접속 :  &emsp;<a href="/session/mypageMain">${Users.userNick }</a></h1>
		<nav>
			<a href="#fancyMenu">Menu</a>
		</nav>
	</header>


<!-- Fancy Menu Box -->

	<nav id="fancyMenu">
		<div class="fancyInner">
			<h2>Menu</h2>
			<ul class="links">
				<%-- <c:url value="/mainpage" var="mainpage" />
				<li><a href="${mainpage }">Home</a></li> --%>
				<c:url value="/login" var="login" />
				<c:if test="${!empty Users.userId }">
				<li><a href="/logout">Log OUT</a></li>
				<%-- <c:url value="/session/mypageMain" var="mypageMain" /> --%>
				<%-- <li><a href="${mypageMain }">My Page</a></li> --%>
				<%-- <li><a href="/session/myPhoto?userId=${Users.userId }&folderName">Photo Book</a></li> --%>
				</c:if>
				<c:if test="${empty Users.userId}">
				<!-- <li><a href="#" onclick="window.open('/pop_login', 'win1', 'width=532, height=475');" onkeypress="this.onclick()">Log In</a></li> -->
				<li><a href="/loginCheck">Log In</a></li>
				<%-- <c:url value="/session/mypageMain" var="mypageMain" /> --%>
				
				<!-- <li><a href="#">Photo Book</a></li> -->
				<c:url value="/join" var="join" />
				<li>
					<a href="#" onclick="window.open('/join', 'win1', 'width=560, height=680');" onkeypress="this.onclick()">
						사이트 회원 가입
					</a>
				</li>
				</c:if>	

			</ul>
			<a href="#" class="close">Close</a>
		</div>
	</nav>
	
