<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<!DOCTYPE html>
<html lang="ko">
<head>

<meta name="viewport" content="width=device-width, initial-scale=1" />
	<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
 	<meta name="author" content="Nathan Searles">
  		
		<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css" />
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
		<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
		
		<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">

<title>공지사항: ${comment.commentName}</title>
</head>
<body>
	<!-- Drop Menu Header -->
		<jsp:include page="../layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../layout/menubar_header.jsp" />
		
	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">
				
					<!-- Board Left Menu -->
						<jsp:include page="../layout/board_left_menu.jsp" />
						
					<!-- Board body part -->
						<div class="8u 12(moblie) important(moblie)">
							<section class="middle-content">
								<form>
								
									<!-- 질문 내용 자세히 보기 부분 -->
									<div>
										<section>
											<input type="button" value="목록" id="listview" class="boardButtonStyle2">
										</section>
										<hr id="boardTitleHrStyle1">
										<h3 id="boardTitleSytle1">${comment.commentName}</h3>
										<input type="hidden" name="commentNo" value="${comment.commentNo }" />
										<hr id="boardTitleHrStyle1">
										<table>
											<colgroup>
												<col width="10%" />
												<col width="*%" />
												<col width="10%" />
												<col width="20%" />
											</colgroup>
											<tr>
												<th>작성자</th>
												<input type="hidden" name="userId" value="${comment.userId }" />
												<td class="boardTitleSort boardFontBold">${comment.userNick}</td>
												<th>작성일</th>
												<td class="boardTitleSort boardFontBold">
													<fmt:formatDate value="${comment.commentDate}"
																			pattern="yy-MM-dd hh:mm:ss" var="fmtDate" /> ${fmtDate}
												</td>
											</tr>
											<tr>
												<td> </td>
												<td colspan="3" class="boardTitleSort noticeContentView">${comment.commentContent}</td>
											</tr>
											<tr>
												<td colspan="4">
													<ul class="boardButtonList">
														<li>
													 		<c:if test="${'THJEON'==Users.userId }">
																<input type="submit" value="삭제" formaction="/commentDelete"
																			formmethod="post" class="boardButtonStyle1">
															</c:if>
													 	</li>
													 	<li>
													 		<c:if test="${'THJEON'==Users.userId }">
																<input type="submit" value="수정" formaction="/session/noticeUpdate"
																			formmethod="post" class="boardButtonStyle1">
															</c:if>
													 	</li>
													 </ul>
												</td>
											</tr>
										</table>
									</div>
									
									<hr id="boardTitleHrStyle2">
								</form>
							</section>
						</div>
				</div>
				
				<!-- footer -->
					<%-- <jsp:include page="../layout/footer.jsp"></jsp:include> --%>
			</div>
		</div>
	</div>
	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script>
		$("#listview").on("click", function() {
			location.href = "notice?page=1";
		})
	</script>


</body>
</html>