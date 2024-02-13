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
		<title>이모티콘</title>
		<!-- <meta property="og:url" content="링크걸 주소(ex : http://www.naver.com)"> -->
		<meta property="og:title" content="이모티콘">  
		<meta property="og:type" content="website">
		<meta property="og:image" content="<%=request.getContextPath()%>/img_loa/<%=request.getAttribute("imgval")%>.png">
		<meta property="og:description" content=" 이미지 ">
	</head> 
	<body>
		<div id="div_load_image">
	      <img id="load_image" src="<%=request.getContextPath()%>/img_loa/<%=request.getAttribute("imgval")%>.png">
	    </div>
	    <script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
		<script>
		$('#load_image').css('width','100%');
		$('#load_image').css('height','100%');
		$(document).on("ready", function() {
			//window.location.href = '/free';
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
				margin-top: 0%;
				left: 0px;
			}
			
		</style>
		
	</body>
</html>
