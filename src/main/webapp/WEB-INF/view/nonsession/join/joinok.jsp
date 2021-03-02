<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>회원가입 완료  ::: TH-HOME</title>
</head>
<body>



	<h2>잠시후 창이 닫힙니다.</h2>

	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script>
		$(document).on("ready", function() {
			alert("${message }");
			<% session.removeAttribute("message");%>
			//window.opener.location.href="/mainpage2";
			self.close();
			window.opener.location.reload();
			
		})
		
	</script>
</body>
</html>