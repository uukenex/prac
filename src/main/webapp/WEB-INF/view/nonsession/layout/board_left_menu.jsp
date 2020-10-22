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
				<c:url value="/session/game1" var="game1"/>
				<c:url value="/session/game2" var="game2"/>
				<c:url value="/session/game3" var="game3"/>
				<c:url value="/session/game4" var="game4"/>
				<c:url value="/session/game5" var="game5"/>
				<c:url value="/session/game6" var="game6"/>
				<c:url value="/game7" var="game7"/>
				<c:url value="/game/rank" var="rank"/>
				
				<li>
					<a href="${free }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						커뮤니티
					</a>
				</li>
				
				<li>
					<a href="${game1 }">
						<i class="fa fa-gamepad"></i>
						Game1(pc/mobile)
						</br>
						피아노게임
					</a>
				</li>
				<li>
					<a href="${game2 }">
						<i class="fa fa-gamepad"></i>
						Game2(none game)
						</br>
						경로찾기알고리즘
					</a>
				</li>
				<li>
					<a href="${game3 }" >
						<i class="fa fa-gamepad"></i>
						Game3(pc/mobile)
						</br>
						벽피하는지렁이게임
					</a>
				</li>
				<li>
					<a href="${game4 }" >
						<i class="fa fa-gamepad"></i>
						Game4(pc/mobile)
						</br>
						회전하는지렁이게임
					</a>
				</li>
				<li>
					<a href="${game5 }" >
						<i class="fa fa-gamepad"></i>
						Game5(pc only)
						</br>
						생존왕라이언
					</a>
				</li>
				<li>
					<a href="${game6 }" >
						<i class="fa fa-gamepad"></i>
						Game6(pc only)
						</br>
						채팅불릿
					</a>
				</li>
				<li>
					<a href="${game7 }" >
						<i class="fa fa-gamepad"></i>
						Game7(dev ing...)
						</br>
					</a>
				</li>
				<li>
					<a href="${rank }" >
						<i class="fa fa-comments-o"></i>
						RANK
						</br>
						순위보기 
					</a>
				</li>
			</ul>
		</section>
	</div>