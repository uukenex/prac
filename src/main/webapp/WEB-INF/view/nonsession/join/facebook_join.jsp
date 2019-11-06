<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
   
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/joinstyle.css" />
<title>여행을 부탁해 회원 가입</title>

</head>
<body>

	<c:url value="/joinOk" var="joinOk"></c:url>
	<sform:form action="${joinOk}" method="post" modelAttribute="Users"
		id="myform">
		<fieldset>
			<legend align="center">
				<img id="logo" alt="Logo"
					src="/images/logo.png">
			</legend>
			<table cellspacing="15" class="joinFormTable">
				<tr>
					<th><label for="phone" class="inputLabel"> <img
							alt="PhoneIcon"
							src="<%=request.getContextPath()%>/join_icons/phone_size_tag.png">
					</label></th>
					<td><input type="text" id="phone" name="phone"
						placeholder="전화번호 입력" class="inputTextStyle"></td>
				</tr>
				<tr>
					<th><label for="email" class="inputLabel"> <img
							alt="emailIcon"
							src="<%=request.getContextPath()%>/join_icons/email_size_tag.png">
					</label></th>
					<td><input type="email" id="email" name="email"
						placeholder="이메일 입력 " class="inputTextStyle"></td>
				</tr>
				<tr>
					<th><label for="nickname" class="inputLabel"> <img
							alt="NicknameIcon"
							src="<%=request.getContextPath()%>/join_icons/nickname_size_tag.png">
					</label></th>
					<td><input type="text" id="nickname" name="nickname"
						placeholder="별명 입력" class="inputTextStyle"></td>
					<td><input type="button" value="중복확인" id="checkNick"></td>
				</tr>
								<tr>
					<th><label for="id" class="inputLabel">
					</label></th>
					<td><input type="text" id="id" name="id" placeholder="아이디 입력" value="${fId }" hidden
						class="inputTextStyle"></td>
					
				</tr>
				<tr>
					<th><label for="password" class="inputLabel">
					</label></th>
					<td><input type="password" id="password" name="password"
						placeholder="비밀번호 입력" class="inputTextStyle"  value="facebook" hidden></td>
				</tr>
				<tr>
					<th><label for="passwordCk" class="inputLabel">
					</label></th>
					<td><input type="password" id="passwordCk" name="passwordCk"
						placeholder="비밀번호 확인" class="inputTextStyle"  value="facebook" hidden></td>
				</tr>
				<tr>
					<th><label for="name" class="inputLabel">
					</label></th>
					<td><input type="text" id="name" name="name"
						placeholder="이름 입력" class="inputTextStyle"  value="${fName }" hidden></td>
				</tr>
				<tr>
					<th></th>
					<td><input type="submit" value="가입하기" id="joinButton">
					</td>
				</tr>
			</table>
		</fieldset>
	</sform:form>
		<script src="http://code.jquery.com/jquery.js"></script>
	<script src="validation/dist/jquery.validate.min.js"></script>
	<script>
	
	//중복체크가 되었는지 확인 
	$("#myform").on({
			"submit" : function(e) {
				e.preventDefault();
				if ( checkVal2 == true) {
					//비밀번호 체크(값이 같은지, 값이 들어가있는지 확인)
					if ($("#password").val() == $("#passwordCk").val()) {
						//이름,전화번호,이메일 값이 들어가 있는지 확인)
						if($("#name").val().trim() != ''&& $("#phone").val().trim() != '' && $("#email").val().trim() != ''   ){
							this.submit();
						}
						else{
							alert("비어있는 칸이 있는지 확인해주세요.");
						}
					}else{
						alert("비밀번호를 확인해주세요");
					}
				} else {
					alert("중복 확인을 해주세요");
					console.log("비밀번호 :"+ $("#password").val());
					console.log("비밀번호 확인 :" +$("#passwordCk").val());
				}
			},
			"reset" : function(e) {
				alert("다시작성버튼");
				
			}
		});
		
/* 	//비밀번호가 잘입력되었고, 비밀번호와 비밀번호 확인의 값이 같은지 확인
	$("#myform").on({
		"submit" : function(e) {
			e.preventDefault();
			if ($("#password").val() == $("#passwordCk").val()) {
				this.submit();
			} else {
				alert("비밀번호를 확인해주세요.");
				console.log("비밀번호 :"+ $("#password").val());
				console.log("비밀번호 확인 :" +$("#passwordCk").val());
			}
		},
		"reset" : function(e) {
			alert("다시작성버튼");
			
		}
	}); */

		var checkVal1 = false;
		var checkVal2 = false;

		<c:url value="/checkId" var="checkId" />
		$("#checkId").on("click", function() {

			var userId = $("#id").val().trim();
			if (userId == '') {
				alert("공란입니다");
				return;
			}

			$.ajax({
				type : "get",
				url : "${checkId}",
				data : {
					"userId" : userId
				},
				success : function(res) {
				var regid = /^[A-Za-z0-9]{4,16}$/;
				if($("#id").val().trim().match(regid)){
				/* if($("#id").val().length>3 && $("#id").val().length<12){ */
					if (res == 0) {
						alert("사용가능한 아이디입니다");
						checkVal1 = true;
					} else {
						alert("이미사용중인 아이디입니다");
						checkVal1 = false;
					}
				}else{
					console.log($("#id").val().length);
					alert("아이디를 확인해주세요.");
				}
				},
				error : function(xhr, status, error) {
					alert(error);
				}
			});
		});

		<c:url value="/checkNick" var="checkNick" />
		$("#checkNick").on("click", function() {

			var userNick = $("#nickname").val().trim();
			if (userNick == '') {
				alert("공란입니다");
				return;
			}

			$.ajax({
				type : "get",
				url : "${checkNick}",
				data : {
					"userNick" : userNick
				},
				success : function(res) {
					if (res == 0) {
						alert("사용가능한 별명입니다.");
						checkVal2 = true;
					} else {
						alert("이미사용중인 별명입니다.");
						checkVal2 = false;
					}
				},
				error : function(xhr, status, error) {
					alert(error);
				}
			});
		});

		$("#myform").validate({
			rules : {
				id : "required",
				password : "required",
				passwordCk : "required",
				name : "required",
				phone : {
					required : true,
					number : true
				},
				email : {
					required : true,
					email : true
				},

				nickname : "required"

			},
			messages : {
				id : "id는 필수입력",
				name : "이름은 필수입력",
				password : "비밀번호는 필수입력",
				passwordCk : "비밀번호확인은 필수입력",
				phone : {
					required : "전화번호는 필수입력",
					number : "전화번호 양식확인 01000000000"
				},
				email : {
					required : "이메일 필수입력",
					email : "이메일 양식 확인 name@domain.com"
				},
				nickname : "별명은 필수입력",
			}

		});

		$("#id").on("keyup",function(){
			var regid = /^[A-Za-z0-9]{4,12}$/;
			if($(this).val().trim().match(regid)){
				$(this).css("background", "rgb(120,255,255)");
			}else {
				$(this).css("background", "rgb(255,150,150)");

			}
		})
		
		$("#password,#passwordCk").on("keyup", function() {
			if ($("#password").val() != $("#passwordCk").val()) {
				$("#passwordCk").css("background", "rgb(255,150,150)");
			} else {
				$("#passwordCk").css("background", "rgb(120,255,255)");
			}
		})

		$("#email").on("keyup", function() {
			var regEmail = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,3}$/i;
			if ($(this).val().trim().match(regEmail)) {
				$(this).css("background", "rgb(120,255,255)");
			} else {
				$(this).css("background", "rgb(255,150,150)");
			}
		})
		$("#phone").on("keyup", function() {
			var regPhone = /^((010))[0-9]{8}$/;
			if ($(this).val().trim().match(regPhone)) {
				$(this).css("background", "rgb(120,255,255)");
			} else {
				$(this).css("background", "rgb(255,150,150)");
			}
		})
		
		$(document).on("ready",function(){
			
		
			    window.resizeTo(580,520);
			    window.focus();
		})
		
	</script>
</body>
</html>