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
<%-- [2026-09-30] 인벤 스타일 로그인(loginCheck.jsp와 동일 디자인). 폼 action(/loginUser, /join)/필드명/${message}
     알림은 그대로. 이 파일 아래에 있던 페이스북 로그인 스크립트는 #fb-auth 버튼이 이미 주석 처리돼 있어
     실행해도 getElementById 결과가 null이라 에러만 나던 죽은 코드라 함께 제거. --%>
<div class="nb inven" data-room="login">
	<jsp:include page="../newboard/_chrome_top.jsp" />

	<div class="nb-board">
		<div class="ib-login">
			<h1>로그인</h1>
			<p class="ib-login-sub">TH보드 계정으로 로그인하세요</p>
			<sform:form modelAttribute="Users">
				<div class="ib-login-field">
					<label for="id">아이디</label>
					<input type="text" id="id" name="id" placeholder="아이디 입력" maxlength="12" autocomplete="username">
				</div>
				<div class="ib-login-field">
					<label for="password">비밀번호</label>
					<input type="password" id="password" name="password" placeholder="비밀번호 입력" autocomplete="current-password">
				</div>
				<c:url value="/loginUser" var="loginUser" />
				<c:url value="/join" var="join" />
				<input type="submit" class="ib-login-btn" id="submit" value="로그인"
					formaction="${loginUser}" formmethod="post">
				<input type="submit" class="ib-login-btn sub" name="joinus" value="회원가입" formaction="${join}">
				<div class="ib-login-find">
					<c:url value="/findId" var="findId" />
					<c:url value="/findPassword" var="findpw" />
					<a href="${findId}">아이디 찾기</a><span>|</span><a href="${findpw}">비밀번호 찾기</a>
				</div>
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
		})
	</script>
</body>
</html>
