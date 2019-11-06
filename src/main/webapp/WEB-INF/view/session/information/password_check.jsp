<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>회원 정보 수정</title>
</head>
<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
	<!-- Password Check body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<h2 class="mypage_mypost_title">My Page _ ${Users.userNick }님의 회원 정보 수정</h2>
					<div class="row main-row">
					
						<div class="4u"></div>
						<div class="4u info_input_pass">
							<form method="post" action="/informChange">
								<h4>비밀번호를 입력해주세요.</h4>
								<input type="password" name="passwordCheck" id="passwordCheck">
								<input type="submit" value="확인" class="mypage_info_buttonStyle3">
								<input type="button" value="뒤로가기" onclick="history.back()" class="mypage_info_buttonStyle3">
							</form>
						</div>
					
					</div>
					
					<!-- footer -->
						<jsp:include page="../../nonsession/layout/footer.jsp" />
				</div>
			</div>
		</div>
	
</body>
</html>