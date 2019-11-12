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
				<%-- <c:url value="/notice?page=1" var="notice"></c:url>
				<li>
					<a href="${notice }" class="noticeBoard" id="current">
						<i class="fa fa-bullhorn"></i>
						공 지 사 항
					</a>
				</li> --%>
				<%-- <c:url value="/qna?page=1" var="qna" />
				<li>
					<a href="${qna }" class="qnaBoard">
						<i class="fa fa-hand-paper-o"></i>
						Q & A
					</a>
				</li> --%>
				<c:url value="/free?page=1" var="free"/>
				<li>
					<a href="${free }" class="freeBoard">
						<i class="fa fa-comments-o"></i>
						자 유 게 시 판
					</a>
				</li>
				<!-- <li>
					<a href="/help_main" class="howuseBoard">
						<i class="fa fa-child"></i>
						사이트 이용방법
					</a>
				</li> -->
			</ul>
		</section>
	</div>