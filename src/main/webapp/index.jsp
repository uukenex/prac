<!DOCTYPE html>
<%
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Cache-Control", "no-store");
    response.setDateHeader("Expires", 0);
    response.setHeader("Pragma", "no-cache");
%>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>

<html>
	<head>
		<meta charset="utf-8">
		<title>now loading....</title>
		<!-- <meta property="og:url" content="링크걸 주소(ex : http://www.naver.com)"> -->
		<meta property="og:title" content="알파카 연구소">  
		<meta property="og:type" content="website">
		<meta property="og:image" content="<%=request.getContextPath()%>/game_set/img/paimon.jpg?v=<%=System.currentTimeMillis()%>">
		<meta property="og:description" content="알파카 연구소 메인페이지로 이동합니다.">
	</head> 
	<body>
		<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
		<script src="<%=request.getContextPath()%>/game_set/js/comutil.js?v=<%=System.currentTimeMillis()%>"></script>
		<div id="div_load_image">
	      <img id="load_image" src="<%=request.getContextPath()%>/game_set/img/loading.gif?v=<%=System.currentTimeMillis()%>">
	    </div>
		<script>
		$('#load_image').css('width',resizeWindowHeight/3);
		$('#load_image').css('height',resizeWindowHeight/3);
		$(document).on("ready", function() {
			window.location.href = '/free';
		});
		</script>
		<style>
			#div_load_image {
				position:absolute;
				width:100%;
				height:70%; 
				z-index:9999; 
				background:#FFFFFF; 
				padding:0px; 
				text-align:center;
				margin-top: 30%;
				left: 0px;
			}
			
		</style>
		<%--
		<c:url value="/hello" var="messageUrl" />
		<a class="fbx" href="/images/pics02.jpg">Click to enter</a>
		<br>
		<c:url value="/hello" var="messageUrl" />
		<a  class="fancybox"  href="${messageUrl}">Click to enter</a>
		<br>
		<c:url value="/join" var="joinUrl" />
		<a href="${joinUrl}">Enter Join Form</a>
		<br>
		<c:url value="/id_check" var="idCheck" />
		<a href="${idCheck }">Enter Id Check Form</a>
		<br>
		<c:url value="/mainpage" var="mainpage" />
		<a href="${mainpage }">Enter Homepage Main</a>
		<br>
		<c:url value="/mypageMain" var="mypageMain" />
		<a href="${mypageMain }">Enter My Page Main</a>
		<br>
		<c:url value="/informChange" var="informChange" />
		<a href="${informChange }">Enter inform_change Main</a>
		<br>
		<c:url value="/qnaView" var="qnaView" />
		<a href="${qnaView }">Enter Q&A Writter Main</a>
		<br>
		<c:url value="/login" var="login" />
		<a href="${login }">Enter Login Form</a>
		<br>
		<c:url value="/findId" var="findId" />
		<a href="${findId }">Enter FindID Form</a>
		<br>
		<c:url value="/findPassword" var="findpw" />
		<a href="${findpw }">Enter FindPass Form</a>
		<br>
		<c:url value="/findIdSuccess" var="findIdsuc" />
		<a href="${findIdsuc }">Enter FindIDSuc Form</a>
		<br>
		--%>
		<%-- 
		<c:url value="/notice?page=1" var="notice" />
		<a href="${notice }">공지사항 1페이지</a>
		<br>
		<c:url value="/free?page=1" var="free" />
		<a href="${free }">자유게시판 1페이지</a>
		<br>
		 --%>
		<%--
		<c:url value="/post?page=1" var="post" />
		<a href="${post }">후기게시판 1페이지</a>
		<br>
		<c:url value="/qna?page=1" var="qna" />
		<a href="${qna }">QNA 게시판 1페이지</a>
		<br>
		<c:url value="/mapapi" var="api" />
		<a href="${api }">map api</a>
		<br>
		<c:url value="/mapRightMenu" var="mapRightMenu" />
		<a href="${mapRightMenu }">map mapRightMenu</a>
		<br>
		<c:url value="/session/route?routeNo=63" var="api63" />
		<a href="${api63 }">63번 루트보기</a> 
		<br>
		<c:url value="/mainpage2" var="m2" />
		<a href="${m2 }">쓰던메인</a>
		<br>
		<c:url value="/mainpage123213212" var="m2" />
		<a href="${m2 }">쓰던메인</a>
		<br>
		<br>
		<c:url value="/session/myPhoto?userId=${Users.userId }&folderName" var="m2" />
		<a href="${m2 }">myPhoto</a>
		<br>
		--%>
	</body>
</html>
