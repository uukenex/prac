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
	<c:forEach var="Review" items="${Review }">
	<table>
		
		<tr align="center">
			<td width="300px">${Users.userNick}님의 ${Review.reviewTitle }
			</td>
			<td width="100px"><fmt:formatDate value="${Review.reviewDate}"
							pattern="yy-MM-dd" var="fmtDate" /> ${fmtDate}</td>
			
		</tr>
		<tr align="center">
			<td colspan="2" >
			<input type="hidden" value="${Review.reviewNo }">
			<a href="/postView?reviewNo=${Review.reviewNo }">여행 후기 보기</a>
			<input type="button" value="여행 후기 삭제"></td>
			
		</tr>
	</table>
	</c:forEach>
	</form>
</body>
<script src="http://code.jquery.com/jquery.js"></script>
<script>

</script>
</html>