<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title>로그인 ::: TH보드</title>
</head>

<body>
<%-- [2026-10-08] 아이디/비밀번호 로그인과 회원가입을 없애고 카카오 로그인만 쓴다(BoardKakaoLoginController). 자유/공유/비밀게시판과 같은 인벤 스타일 테마
     (newboard_inven.css, 뉴게시판 공통 상단 _chrome_top)를 그대로 쓰고, 이미 로그인된 경우의 이동은 UserController.loginCheck가 처리한다. --%>
<div class="nb inven" data-room="login">
	<jsp:include page="../newboard/_chrome_top.jsp" />

	<div class="nb-board">
		<div class="ib-login">
			<h1>로그인</h1>
			<p class="ib-login-sub">카카오 계정으로 로그인하세요 (고유번호와 닉네임만 저장됩니다)</p>
			<a class="ib-login-btn" href="<%=request.getContextPath()%>/board/kakao/auth"
				style="background:#FEE500; color:#191919; border-color:#FEE500; text-align:center; text-decoration:none;">
				<svg width="18" height="18" viewBox="0 0 24 24" fill="#191919" style="vertical-align:-3px; margin-right:6px;"><path d="M12 3C6.477 3 2 6.477 2 11c0 2.96 1.68 5.55 4.2 7.1L5.1 21.9a.5.5 0 0 0 .72.56L9.7 19.9A11.3 11.3 0 0 0 12 20c5.523 0 10-3.477 10-8S17.523 3 12 3z"/></svg>카카오로 로그인
			</a>
			<%
				String err = String.valueOf(request.getAttribute("err"));
				if ("cancel".equals(err)) { %><p class="ib-login-find" style="color:#A31F2B;">로그인이 취소되었습니다.</p>
			<% } else if ("token".equals(err)) { %><p class="ib-login-find" style="color:#A31F2B;">인증에 실패했습니다. 다시 시도해주세요.</p>
			<% } else if ("state".equals(err)) { %><p class="ib-login-find" style="color:#A31F2B;">로그인 세션이 만료되었습니다. 다시 시도해주세요.</p><% } %>
		</div>
	</div>
</div>
</body>
</html>
