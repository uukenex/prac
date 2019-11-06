<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
</head>
<body>
	
	<h2>잠시후 창이 닫힙니다.</h2>

	<script src="http://code.jquery.com/jquery.js"></script>
	<script>
		$(document).on("ready", function() {
			alert("${Users.userNick}님 ${message }");
			<% session.removeAttribute("message");%>
			 window.opener.location.reload(); 
			self.close();
		})
	</script>
</body>
</html>