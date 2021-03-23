<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<!-- d -->

	<div class="3u 12u(mobile)">
	
		<!-- Left Board Menu -->
		<section class="boardSection">
			<h2>Menu</h2>
			<ul class="link-list">
				
				<c:url value="/free?page=1" var="free"/>
				<c:url value="/share?page=1" var="share"/>
				<c:url value="/session/game1" var="game1"/>
				<c:url value="/session/game2" var="game2"/>
				<c:url value="/session/game3" var="game3"/>
				<c:url value="/session/game4" var="game4"/>
				<c:url value="/session/game5" var="game5"/>
				<c:url value="/ws5" var="ws5"/>
				<c:url value="/game7" var="game7"/>
				<c:url value="/game/rank" var="rank"/>
				
				<li>
					<a href="${free }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						free board
					</a>
				</li>
				<li>
					<a href="${share }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						share board
					</a>
				</li>
				<li>
					<a href="${game1 }">
						<i class="fa fa-gamepad"></i>
						PIANO(pc/mobile)
					</a>
				</li>
				<li>
					<a href="${game2 }">
						<i class="fa fa-gamepad"></i>
						find path(none game)
					</a>
				</li>
				<li>
					<a href="${game3 }" >
						<i class="fa fa-gamepad"></i>
						worm tail(pc/mobile)
					</a>
				</li>
				<li>
					<a href="${game4 }" >
						<i class="fa fa-gamepad"></i>
						round worm(pc/mobile)
					</a>
				</li>
				<li>
					<a href="${game5 }" >
						<i class="fa fa-gamepad"></i>
						lion ninja(pc only)
					</a>
				</li>
				<li>
					<a href="${game7 }" >
						<i class="fa fa-gamepad"></i>
						dev ing
					</a>
				</li>
				<li>
					<a href="${ws5 }" >
						<i class="fa fa-gamepad"></i>
						chat(pc only)
					</a>
				</li>
				<li>
					<a href="${rank }" >
						<i class="fa fa-comments-o"></i>
						RANK
					</a>
				</li>
			</ul>
		</section>
	</div>