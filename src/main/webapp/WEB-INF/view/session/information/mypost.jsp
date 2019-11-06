<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>${Users.userNick}님의 후기</title>
</head>

<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
		
	<!-- My Page Post view -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
				<h2 class="mypage_mypost_title">My Page _ ${Users.userNick }님의 여행 후기</h2>
					<div class="row main-row">
						
						<div class="1u"></div>
						<div class="10u 12u(moblie) important(moblie)">
						
							
							<c:forEach var="Review" items="${Review }">
							<form>
								<div id="mypage_mypost_viewDiv">
									<table class="mypage_mypost_viewTable">
										<colgroup>
											<col width="10%">
											<col width="*%">
										</colgroup>
										<tr>
											<th rowspan="2">
												<input type="checkbox" name="mypage_mypost_chk" id="${Review.reviewNo }">
												<label for="${Review.reviewNo }"></label>
											</th>
											<td>
												<b class="mypage_mypost_postTitle">${Users.userNick}님의 ${Review.reviewTitle }</b>
												&emsp;
												<fmt:formatDate value="${Review.reviewDate}"
													pattern="yy-MM-dd hh:mm:ss" var="fmtDate" /> ${fmtDate}
											</td>
										</tr>
										<tr>
											<td>
												<input type="hidden" value="${Review.reviewNo }" name="reviewNo" >
												<a href="/postView?reviewNo=${Review.reviewNo }" class="mypage_mypost_buttonStyle1">여행 후기 보기</a>
												<input type="submit" formmethod="post" formaction="/session/postUpdate" value="여행 후기 수정"class="mypage_mypost_buttonStyle1">
												<input type="submit" formmethod="post" formaction="/mypageReviewDelete"value="여행 후기 삭제" class="mypage_mypost_buttonStyle1">
											</td>
										</tr>
									</table>
									</div>
									</form>
								</c:forEach>
							<c:if test="${empty Review}">
							<div style="height: 5em;"></div>
							<h3>등록한 후기가 없습니다!!</h3>
							</c:if>
							<div id="posts_paging">
								<%!int i;%>
								<%
									for (int i = 1; i <= Integer.parseInt(request.getAttribute(("totalPage")).toString()); i++) {
								%>
								<a href="/session/mypageReview?page=<%=i%>"><%=i%> </a>
								<%
									}
								%>
							</div>
						</div>
						<div class="1u"></div>
					</div>
					
					<!-- footer -->
						<jsp:include page="../../nonsession/layout/footer.jsp" />
				</div>
			</div>
		</div>
		
<%-- 	
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
--%>
			
</body>
<script src="http://code.jquery.com/jquery.js"></script>
<script>

</script>
</html>