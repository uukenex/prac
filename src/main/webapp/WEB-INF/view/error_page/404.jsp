<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>Error!</title>
</head>
<body>

	<!-- Drop Menu Header -->
		<jsp:include page="/WEB-INF/view/nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="/WEB-INF/view/nonsession/layout/menubar_header.jsp" />
		
		
	<!-- Error page body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<div class="row main-row">
					
						<div class="3u"></div>
						<div class="6u error_page_div">
							<h3><i class="fa fa-times-circle-o"></i></h3>
							<h3>요청하신 페이지가</h3>
							<h3>존재하지 않습니다 !!</h3>
							<h3>404 error</h3>
							<input type="button" value="뒤로 가기" class="labeling1" onclick="history.back()">
						</div>
					</div>
					
					<!-- footer -->
					<%-- <jsp:include page="/WEB-INF/view/nonsession/layout/footer.jsp"></jsp:include> --%>
				</div>
			</div>
		</div>
	
</body>
</html>