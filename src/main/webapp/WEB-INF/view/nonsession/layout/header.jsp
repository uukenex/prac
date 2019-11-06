<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<div id="header-wrapper">
	<div class="container">
		<div id="header">

			<!-- Logo -->
			<div	id="logo">
			<span>Make by</span>
				<h1><a href="index.jsp">TRAVEL</a></h1>
			</div>
			
			<!-- Header MenuBar -->
			<div>
			<nav	id="nav2">
				<ul>
					<c:url value="/login" var="login" />
					<c:url value="/join" var="joinUrl" />
					<li><a href="${login }"
							onclick="window.open(this.href, 'win1',
								'width=550, height=480'); return false;"
								onkeypress="this.onclick()">
							Login</a></li>
					<li><a href="${joinUrl }"
							onclick="window.open(this.href, 'win1',
								'width=530, height=630'); return false;"
								onkeypress="this.onclick()">
							회원가입</a></li>
					<li><a href="#">My Page</a></li>
				</ul>
			</nav>
			</div>
			<nav id="nav1">
				<ul>
					<li class="current_page_item"><a href="index.jsp">Homepage</a></li>
					<li><a href="#">지도 만들기</a></li>
					<li><a href="#">후기 등록</a></li>
					<li><a href="#">Photo Book</a></li>
					<li><a href="#">게시판</a></li>
				</ul>
			</nav>
		</div>
	</div>
</div>