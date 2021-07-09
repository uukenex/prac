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
 	<meta property="og:title" content="알파카 연구소">  
	<meta property="og:type" content="website">
	<meta property="og:image" content="<%=request.getContextPath()%>/game_set/img/paimon.jpg?v=<%=System.currentTimeMillis()%>">
	<meta property="og:description" content="알파카 연구소 - 공유게시판">
  		
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
	
	<!-- jqeury 이후 호출 -->
	<script src="<%=request.getContextPath()%>/game_set/js/comutil.js?v=<%=System.currentTimeMillis()%>"></script>
		
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
											<input type="submit" value="수정하기" formaction="/s/shareUpdate" formmethod="post">
										</section>
										<hr id="boardTitleHrStyle1">
										<h3 id="boardTitleSytle1">${share.shareName} ver.${share.version}</h3>
										<input type="hidden" name="shareNo" value="${share.shareNo }" />
										
										<hr id="boardTitleHrStyle1">
										
										<section class="inline">
											<c:if test="${share.version > 5}">
											
											<select id="selectVersion"  onChange="location.href='shareView?shareNo=${share.shareNo}&version='+this.value">
												<option value="0">과거버전으로
												<c:forEach var="version" items="${shareHist}">
													<option value="${version}  ">과거버전 ${version}  </option>
												</c:forEach>
											</select>
											<input type="button" class="boardButtonStyle2" onclick="location.href='shareView?shareNo=${share.shareNo}&version=0';"
											value="최신버전으로" />
											</c:if>
										</section>
										
										<table style="border:2px solid #1DDB16">
											<colgroup>
												<col width="15%" />
												<col width="17%" />
												<col width="15%" />
												<col width="*%" />
											</colgroup>
											<tr>
												<td>수정자</td>
												<c:if test="${share.modifyId == '999999'}"> 
													<td class="boardTitleSort boardFontBold">${share.userNick} (${share.ipAddr})</td>
												</c:if>
												<c:if test="${share.modifyId != '999999'}">
													<td class="boardTitleSort boardFontBold">${share.userNick}</td> 
												</c:if>
												
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
				
				var img = new Image;
				img.src = this.src;
				var w = window.open("",'_blank','width='+img.width+',height='+img.height);
				w.document.write(img.outerHTML);
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