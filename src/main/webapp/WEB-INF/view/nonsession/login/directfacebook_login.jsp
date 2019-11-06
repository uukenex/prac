<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>

</head>
<body>

	<form name="flogin" method="post" action="/directFloginUser">
		<input type="hidden" value="${fId }" name="facebookId">
	</form>
	<script>document.flogin.submit();</script>
</body>

</html>