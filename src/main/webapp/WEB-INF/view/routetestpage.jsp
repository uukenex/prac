<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Insert title here</title>
</head>
<style>
	td{
		border : solid 2px;
	}

</style>
<body>
	
	<form>
	<c:forEach var="Route" items="${Route }">
	<input type="hidden" value="${Route.routeNo }" name="routeNo">
	<table>
		
		<tr>
			<td>${Users.userNick}님의 ${Route.routeName }</a></td>
		</tr>
		<tr>
			<td><input type="submit" formaction=/testpage value="여행 계획 보기">
			<input type="button" value="여행 계획 수정">
			<input type="button" value="여행 계획 삭제"></td>
		</tr>
	</table>
	</c:forEach>
	</form>
</body>
<script src="http://code.jquery.com/jquery.js"></script>
<script>

</script>
</html>