<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>ID 찾기 성공</title>
</head>
<body>
	<h1>아이디 찾기</h1>
	<hr>
	<div style="background-color: #95B3D7">
		<img src="<%=request.getContextPath() %>/css_images/img05.png">
		Name 님 가입하신 아이디는
		<p>ID 표시(Email주소 표시)</p>
		<p>입니다.</p>
		<hr>
	</div>
	
	<div>
		<input type="button"  value="확인">
		<input type="button" value="비밀번호 찾기">
	</div>
</body>
</html>