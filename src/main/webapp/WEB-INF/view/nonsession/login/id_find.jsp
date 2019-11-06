<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>아이디 / 비밀번호 찾기</title>
<style>
	fieldset {
		width: 550px;
		border-color: #D99694;
	}
	.inputTextStyle {
		width: 300px;
		height: 35px;
		font-size: 13pt;
		font-family: 나눔고딕;
		margin-left: 1.3em;
		
	}
	label {
		text-align: center;
		font-weight: bold;
		font-size: 15pt;
		font-family: 나눔고딕;
	}
	.button2 {
		background-color: #D99694;
		width: 100px;
		height: 35px;
		font-size: 15pt;
		font-weight: bold;
		margin-left: .5em;
		display: inline-block;
		border-radius: 5px;
		text-shadow: none;
		text-decoration: none;
		text-transform: none;
	}
</style>
</head>
<body>
	<img alt="IdFind" src="<%=request.getContextPath()%>/join_icons/id_find.png">
	<c:url value="/searchId" var="searchId" />
	<form action="${searchId}">
	<fieldset>
		<table cellpadding="7.5">
			<tr>
				<th><label for="name">이름</label></th>
				<td><input type="text" id="name" name="name"
								placeholder="이름 입력" class="inputTextStyle">
				</td>
			</tr>
			<tr>
				<th><label for="email">이메일</label></th>
				<td>
					<input type="email" id="email" name="email"
							placeholder="이메일 입력" class="inputTextStyle">
				</td>
				<td>
					<input type="submit" value="다음" class="button2"
				>
				</td>
			</tr>
		</table>
	</fieldset>
	</form>
	<img alt="" src="<%=request.getContextPath()%>/images/div_bar_pink.png">
</body>
<script src="http://code.jquery.com/jquery.js"></script>
<script>
	$(document).on("ready",function(){
    window.resizeTo(650,400);
    window.focus();
    
})	
</script>
</html>