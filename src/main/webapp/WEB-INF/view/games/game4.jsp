<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>따뜻한 가메2</TITLE>

<link rel="stylesheet"
	href="<%=request.getContextPath()%>/game_set/css/game4.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="onloadFunc()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>


	<script language="javascript">
		function onloadFunc() {

		}
	</script>

	<section id='field'>

		<div id="circle1">원 만들기</div>
		<br>
		<div id="circle2">원 만들기2</div>

	</section>
</BODY>
</HTML>