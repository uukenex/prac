<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/joinstyle.css?v=<%=System.currentTimeMillis() %>" />
<title>로그인</title>
</head>

<body>

	<!-- Drop Menu Header -->
		 <jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
	<!-- Login Check Body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<div class="row main-row">
					
						<div class="4u"></div>
						<div class="login_check_div">
						
							<sform:form modelAttribute="Users">
								<fieldset id="login_fieldset">
									<div>
										<input type="text" id="id" name="id" placeholder="아이디 입력"
											class="inputTextStyle2"> <br />
										<input type="password"
											id="password" name="password" placeholder="비밀번호 입력"
											class="inputTextStyle2"> <br />
									</div>
									<div id="banner">
										<c:url value="/directloginUser" var="directloginUser" />
										<c:url value="/join" var="join" />
										<input type="submit" class="button1" id="submit" value="LOGIN"
											formaction="${directloginUser}" formmethod="post"> 
											<input type="button" onclick="window.open('${join}', 'win1',
									'width=532, height=475');" class="button1" value="회원가입">
										<br />
									</div>
									
									<div id="fb-root"></div>
									<!-- <a title="페이스북 아이디로 로그인" id="fb-auth" href="javascript:;">
										<img alt="facebookLogin" src="/images/facebookLogin.png">
									</a> -->
									<%--
									<div id="findLink">
										<c:url value="/findId" var="findId" />
										<c:url value="/findPassword" var="findpw" />
										<a href="${findId }" class="login_finder">아이디 찾기</a>
										<a href="${findpw }" class="login_finder">비밀번호 찾기</a>
									</div>
									 --%>
									
								</fieldset>
							</sform:form>
						</div>
					</div>
					
					<!-- footer -->
						<%-- <jsp:include page="../../nonsession/layout/footer.jsp" /> --%>
				</div>
			</div>
		</div>

	
	<script src="http://code.jquery.com/jquery.js"></script>
	<script>
		$(document).on("ready", function() {
			if ("${message}" != null && "${message}" != ("")) {
				alert("${message}");
	<%session.removeAttribute("message");%>
		}
			
			if( "${Users.userId}" == 'undefined' || "${Users.userId}" == '' || "${Users.userId}" == null ){
				console.log(Users.userId);
			}else{
				console.log(Users.userId);
				location.href = "free?page=1";
			}
		})
		
	</script>
	
</body>
	<script language=javascript>
		window.fbAsyncInit = function() {
			FB.init({
				appId : '1808644559350607',
				status : true,
				cookie : true,
				xfbml : true,
				oauth : true
			});
		}
		
			
			
		function updateButton(response) {
			var button = document.getElementById('fb-auth');

			if (response.authResponse) {

				FB.api('/me', function(response) {
			   		console.log("아이디 : " +response.id);
			   		console.log("이름 : " + response.name);
			   		console.log(typeof response.name);
			   		
			   		<c:url value="/checkfId" var="checkfId"/>
			   		var fId = response.id;
			   		var fName=response.name;
			   		
					if (confirm('facebookID:' + response.name
							+ ' 로 로그인하시겠습니까?') == true) {
						// 확인을 선택했을 경우의 처리.
						
						//페이스북 아이디로 로그인 한적 있는지 없는지 확인
						 $.ajax({
							type : "get",
							url : "${checkfId}",
							data : {
								"fId" : fId,
								"fName" : fName
							},
							success : function(res){
								if(res == 0){
									alert("등록되지 않은 아이디입니다. 정보를 등록합니다");
									window.open('/facebookjoin?fId='+fId+'&fName='+fName, 'win1',
											'width=532, height=475');  
									console.log(fId);
									
								
								}else{
									
									
									window.location.replace('/directFacebooklogin?fId='+fId+'&fName='+fName);
									console.log(fId);
								}
							},
								error : function(xhr, status, error) {
									alert(error);
								}
							
						});
						 
						
					} else {
						// 취소를 선택했을 경우의 처리(아래는 페이스북 로그아웃 처리)
					      checkbox.checked = false;

					}
				});

			} else {
				FB.login(function(response) {
					if (response.authResponse) {
						FB.api('/me', function(response) {

						});
					} else {

					}
				}, {
					scope : 'email'
				});
			}
		}

		document.getElementById('fb-auth').onclick = function() {

			FB.Event.subscribe('auth.statusChange', updateButton);
			FB.getLoginStatus(updateButton);
		};

		(function() {
			var e = document.createElement('script');
			e.async = true;
			e.src = document.location.protocol
					+ '//connect.facebook.net/ko_KR/all.js';
			document.getElementById('fb-root').appendChild(e);
		}());
		
		$("#id").on("keyup", function() {
			 if($("#id").val().length>12){ 
				$(this).val($(this).val().substring(0,12));
			 } 
		})
	</SCRIPT>
</html>