<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1" />
		
	<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
	<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
	
<title>회원 정보 수정 페이지</title>
</head>
<body>


	<!-- Menubar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp"></jsp:include>
		
	<!-- dropMenu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp"></jsp:include>
		
		
	<!-- Change Information body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<h2 class="mypage_mypost_title">My Page _ ${Users.userNick }님의 회원 정보 수정</h2>
					<div class="row main-row">
						<div class="2u"></div>
						<div class="8u 12u(moblie) important(moblie) info_change_body">
							
							<form method="post" id="myform" action="/updateUser">
								<table id="info_change_table">
									<colgroup>
										<col width="20%">
										<col width="20%">
										<col width="*%">
									</colgroup>
									
									<input type="hidden"  value="${Users.userId }" name="changeId" readonly>
									<tr>
										<th>이름</th>
										<td colspan="2" class="table_use_underline">
											<input type="text" value="${Users.userName }" readonly>
											
										</td>
									</tr>
									<tr>
										<th rowspan="2">비밀번호 변경</th>
										<td>신규 비밀번호:</td>
										<td><input type="password" placeholder="변경 할 비밀번호 입력" name=changePass id=changePass></td>
									</tr>
									<tr>
										<td class="table_use_underline">비밀번호 확인:</td>
										<td class="table_use_underline"><input type="password" placeholder="비밀번호 확인" id=changePassCk></td>
									</tr>
									<tr>
										<th rowspan="2">E-Mail 변경</th>
										<td colspan="2"><input type="email" value="${Users.userEmail }" name="currentEmail" readonly></td>
									</tr>
									<tr>
										<td colspan="2" class="table_use_underline"><input type="email" placeholder="변경 이메일 입력" name="changeEmail" id="changeEmail"></td>
									</tr>
									<tr>
										<th rowspan="2">전화번호 변경</th>
										<td colspan="2"><input type="text" value="${Users.userPhone }" name="currentPhone" readonly></td>
									</tr>
									<tr>
										<td colspan="2" class="table_use_underline"><input type="text" placeholder="변경할 전화번호 입력" name="changePhone" id="changePhone"></td>
									</tr>
									<tr>
										<th rowspan="2">별명 변경</th>
										<td colspan="2"><input type="text" value="${Users.userNick }" name="currentNick"  readonly></td>
									</tr>
									<tr>
										<td colspan="2">
											<input type="text" placeholder="별명 입력" name="changeNick" id="changeNick">
											<input type="button" value="중복확인" id="checkNick">
										</td>
									</tr>
								</table>
								<input type="button" value="취소" class="mypage_info_buttonStyle3" onclick="location.href='/session/mypageMain'" style="float: right;">
								<input type="submit" value="저장" class="mypage_info_buttonStyle3" style="float: right;">
							</form>
						</div>
					</div>
					
					<!-- footer -->
						<jsp:include page="../../nonsession/layout/footer.jsp"></jsp:include>
				</div>
			</div>
		</div>


<%-- 
								<form method="post" id="myform" action="/updateUser">
								<table>
								
									<tr>
										<td><input type="text"  value="${Users.userId }" name="changeId" readonly></td>
									</tr>
									<tr>
										<th rowspan="3">비밀번호 변경</th>
									
									</tr>
									<tr>
										<td>신규 비밀번호 : <input type="password" placeholder="변경 할 비밀번호 입력" name=changePass id=changePass></td>
									</tr>
									<tr>
										<td>비밀번호 확인 : <input type="password" placeholder="비밀번호 확인" id=changePassCk></td>
									</tr>
									<tr>
										<th>이  름</th>
										<td><input type="text" value="${Users.userName }" readonly></td>
									</tr>
									<tr>
										<th rowspan="2">E-Mail 변경</th>
										<td><input type="email" value="${Users.userEmail }" name="currentEmail" readonly> </td>
									</tr>
									<tr>
										<td><input type="email" placeholder="변경 이메일 입력" name="changeEmail" id="changeEmail"></td>
									</tr>
									<tr>
										<th rowspan="2">전화번호 변경</th>
										<td><input type="text" value="${Users.userPhone }" name="currentPhone" readonly></td>
									</tr>
									<tr>
										<td><input type="text" placeholder="전화번호 입력" name="changePhone" id="changePhone"></td>
									</tr>
									<tr>
										<th rowspan="2">별명 변경</th>
										<td><input type="text" value="${Users.userNick }" name="currentNick"  readonly> </td>
									</tr>
									<tr>
										<td><input type="text" placeholder="별명 입력" name="changeNick" id="changeNick"></td>
										<td><input type="button" value="중복확인" id="checkNick"></td>
									</tr>
									<tr >
										<td><input type="submit" value="변경하기"></td>
									</tr>
								</table>
								</form>
								
 --%>								

	
		
					
</body>

	<!-- Scripts -->
	<script src="http://code.jquery.com/jquery.js"></script>
	<script src="validation/dist/jquery.validate.min.js"></script>
	<script>
	
	//비밀번호 변경 시 입력 되었는지 체크
		$("#myform").on({
			"submit" : function(e){
				e.preventDefault();
				if($("#changePassCk").val() == $("#changePass").val()){
						if($("#changeNick").val()!='' && checkVal==false){
							alert("중복확인을 해주세요");
							}else{
								console.log(this);
								this.submit();
							}
					
				}else{
					alert("비밀번호와 비밀번호 확인의 값이 다릅니다.");
				}
			}
				
		});
	
		
		$("#changePass,#changePassCk").on("keyup", function() {
			if ($("#changePass").val() != $("#changePassCk").val()) {
				$("#changePassCk").css("background", "rgb(255,150,150)");
			} else {
				$("#changePassCk").css("background", "rgb(120,255,255)");
			}
		})

		$("#changeEmail").on("keyup", function() {
			var regEmail = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,3}$/i;
			if ($(this).val().trim().match(regEmail)) {
				$(this).css("background", "rgb(120,255,255)");
			} else {
				$(this).css("background", "rgb(255,150,150)");
			}
		})
		$("#changePhone").on("keyup", function() {
			var regPhone = /^((010))[0-9]{8}$/;
			if ($(this).val().trim().match(regPhone)) {
				$(this).css("background", "rgb(120,255,255)");
			} else {
				$(this).css("background", "rgb(255,150,150)");
			}
		})
		
	//벨리데이션
	
	
		var checkVal = false;
		
		
	<c:url value="/checkNick" var="checkNick" />
		$("#checkNick").on("click", function() {
			var userNick = $("#changeNick").val().trim();	
			$.ajax({
				type : "get",
				url : "${checkNick}",
				data : {
					"userNick" : userNick
				},
				success : function(res) {
					if (res == 0) {
						alert("사용가능한 별명입니다.");
						checkVal = true;
					} else {
						alert("이미사용중인 별명입니다.");
						checkVal = false;
					}
				},
				error : function(xhr, status, error) {
					alert(error);
				}
			});
		});
	</script>

</html>