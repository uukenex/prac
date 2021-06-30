<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html lang="ko">
<head>

<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
 	<meta name="author" content="Nathan Searles">
  		
		<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css?v=<%=System.currentTimeMillis() %>" />
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
		<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
		
		<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">

<title>${share.shareName} ::: TH-HOME</title>

</head>
<body>

	<!-- Drop Menu Header -->
	<jsp:include page="../layout/dropMenu_header.jsp" /> 
	<!-- Menu Bar Header -->
		<jsp:include page="../layout/menubar_header.jsp" />
		
	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">
				
					<!-- Board Left Menu -->
						<jsp:include page="../layout/board_left_menu.jsp" />
						
					<!-- Board body part -->
						<div class="8u 12u(mobile) important(mobile)">
							<section class="middle-content" >
								<form>
									<div>
										<section>
											<input type="button" value="목록" id="listview" class="boardButtonStyle2">
											<input type="submit" value="수정하기" formaction="/shareUpdate" formmethod="post">
										</section>
										<hr id="boardTitleHrStyle1">
										<h3 id="boardTitleSytle1">${share.shareName}</h3>
										<input type="hidden" name="shareNo" value="${share.shareNo }" />
										<hr id="boardTitleHrStyle1">
										<table style="border:2px solid #1DDB16">
											<colgroup>
												<col width="15%" />
												<col width="17%" />
												<col width="15%" />
												<col width="*%" />
											</colgroup>
											<tr>
												<td>수정자</td>
												<td class="boardTitleSort boardFontBold">${share.userNick}</td>
												<td>최근수정일</td>
												<td class="boardTitleSort boardFontBold2">
													<fmt:formatDate value="${share.modifyDate}"
																			pattern="yyyy-MM-dd HH:mm:ss" var="fmtDate" /> ${fmtDate}
												</td>
											</tr>
											<tr style="border:1px solid #1DDB16;" >
												<td colspan="4" class="boardTitleSort"  style="padding-bottom: 50px; height: 150px;">${share.shareContent}</td>
											</tr>
										</table>
									</div>
								</form>
							</section>
						</div>
				</div>
			</div>
		</div>
	</div>


<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script>
	
		$("#listview").on("click", function() {
			location.href = "share?page=1";
		});
		
		
		$(document).on("ready", function() {
			if ("${message}" != null && "${message}" != ("")) {
				alert("${message}");
	<%session.removeAttribute("message");%>
		}
			
			
			
		});
		
		var img = document.getElementsByTagName('img'); 
		for (var x = 0; x < img.length; x++) {
			img.item(x).onclick=function() {
				window.open(this.src)
			}; 
		}

		
	</script>
	
	<script>
		//var beforeBoard, nowBoard;
		//beforeBoard = document.getElementById("current");
		//beforeBoard.id = beforeBoard.id.replace("");
		//$(".freeBoard").attr('id', 'current');
	</script>
	
	
</body>
</html>