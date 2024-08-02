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
		<title></title>
		<meta property="og:title" content="<%=request.getAttribute("char_info")%>">  
		<meta property="og:type" content="website">
		<meta property="og:image" content="/c/<%=request.getAttribute("imgval")%>.png">
		<meta property="og:description" content="<%=request.getAttribute("title")%>">
	</head> 
	<body>
		<div id="div_load_image">
	      <img id="load_image" style="width='200px' height='200px'" src="/c/<%=request.getAttribute("imgval")%>.png">
	    </div>
	</body>
</html>
