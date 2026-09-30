<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title>로그인 ::: TH보드</title>
</head>

<body>
<%-- [2026-09-30] 인벤 스타일 로그인(자유/공유/비밀게시판과 동일 테마, newboard_inven.css). 기존 좌측메뉴형 레이아웃
     대신 뉴게시판 공통 상단(_chrome_top)을 쓴다. 폼 action(/directloginUser, /join), 필드명(id/password),
     ${message} 알림, 이미 로그인된 경우 free로 이동하는 동작은 그대로. --%>
<div class="nb inven" data-room="login">
	<jsp:include page="../newboard/_chrome_top.jsp" />

	<div class="nb-board">
		<div class="ib-login">
			<h1>로그인</h1>
			<p class="ib-login-sub">TH보드 계정으로 로그인하세요</p>
			<sform:form modelAttribute="Users">
				<div class="ib-login-field">
					<label for="id">아이디</label>
					<input type="text" id="id" name="id" placeholder="아이디 입력" autocomplete="username">
				</div>
				<div class="ib-login-field">
					<label for="password">비밀번호</label>
					<input type="password" id="password" name="password" placeholder="비밀번호 입력" autocomplete="current-password">
				</div>
				<c:url value="/directloginUser" var="directloginUser" />
				<c:url value="/join" var="join" />
				<input type="submit" class="ib-login-btn" id="submit" value="로그인"
					formaction="${directloginUser}" formmethod="post">
				<input type="button" class="ib-login-btn sub" value="회원가입"
					onclick="window.open('${join}', 'win1', 'width=532, height=475');">
			</sform:form>
		</div>
	</div>
</div>

	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script>
		$(document).on("ready", function() {
			if ("${message}" != null && "${message}" != ("")) {
				alert("${message}");
	<%session.removeAttribute("message");%>
		}

			if( "${Users.userId}" == 'undefined' || "${Users.userId}" == '' || "${Users.userId}" == null ){
			}else{
				location.href = "free?page=1";
			}
		})
	</script>
</body>
</html>
