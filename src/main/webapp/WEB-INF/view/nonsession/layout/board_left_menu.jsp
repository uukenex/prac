<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<!-- d -->

	<div class="3u 12u(mobile)">
	<script>
	function toggleGameMenu(){
		$('.hidable').toggleClass('hide');
	}
	</script>
		<!-- Left Board Menu -->
		<section class="boardSection">
			<h2 onclick="toggleGameMenu();">Menu..</h2>
			<ul class="link-list">
				
				<c:url value="/free" var="free"/>
				<c:url value="/share" var="share"/>
				<c:url value="/game1" var="game1"/>
				<c:url value="/game2" var="game2"/>
				<c:url value="/game3" var="game3"/>
				<c:url value="/game4" var="game4"/>
				<c:url value="/game9" var="game9"/>
				<c:url value="/game10" var="game10"/>
				<c:url value="/ws5" var="ws5"/>
				<c:url value="/game7" var="game7"/>
				<c:url value="/rank" var="rank"/>
				
				<li>
					<a href="${free }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						자유게시판
					</a>
				</li>
				<li>
					<a href="${share }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						공유게시판
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game1 }">
						<i class="fa fa-gamepad"></i>
						PIANO(pc/mobile)
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game2 }">
						<i class="fa fa-gamepad"></i>
						find path(none game)
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game3 }" >
						<i class="fa fa-gamepad"></i>
						worm tail(pc/mobile)
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game4 }" >
						<i class="fa fa-gamepad"></i>
						round worm(pc/mobile)
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game10 }" >
						<i class="fa fa-gamepad"></i>
						lion ninja(pc only)
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game9 }" >
						<i class="fa fa-gamepad"></i>
						강화기
					</a>
				</li>
				<li class='hidable hide'>
					<a href="${game7 }" >
						<i class="fa fa-gamepad"></i>
						phaser3
					</a>
				</li>
				<li>
					<a href="${ws5 }" >
						<i class="fa fa-gamepad"></i>
						실시간채팅
					</a>
				</li>
				<%-- 
				<li class='hidable hide'>
					<a href="${rank }" >
						<i class="fa fa-comments-o"></i>
						RANK
					</a>
				</li>
				 --%>
			</ul>
		</section>
	</div>