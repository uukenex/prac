<%
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Cache-Control", "no-store");
    response.setDateHeader("Expires", 0);
    response.setHeader("Pragma", "no-cache");
%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<html>
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
<title>공유게시판 ::: DEV-APC</title>
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
						<jsp:include page="../layout/board_left_menu.jsp"></jsp:include>
					
					<!-- Board Body part -->
					<div class="8u 12u(mobile) important(mobile)">
						<section class="middle-content">
							<h2 class="board_part_title" onclick="alert( 'Width x Height : '+resizeWindowWidth + ' x '+resizeWindowHeight );">공유게시판</h2>
							<%-- <h3>현재 접속 Nick : "${Users.userNick }"</h3> --%>
							
							<ul id="writeNsearchBar">
								<li>
									<div class="boardSearchBar">
										<select id="searchCategory">
											<option value="제목">제목</option>
											<option value="내용">내용</option>
											<option value="닉네임">닉네임</option>
										</select>
										<input type="search" id="search" />
										
									</div>
								</li>
								<li>
									<a href="/session/sharesign" ><input type="submit" value="글쓰기" class="writeBoard"></a>
									<input type="button" id="searchBtn" value="검색" />
								</li>
							</ul>
							
							<form>
								<table style="border:2px solid #FFA2A2">
									<colgroup>
										<col width="20%" />
										<col width="*" />
										<col width="15%" />
										<col width="35%" />
									</colgroup>
									<thead id="board_table_thead">
									<tr>
										<th scope="col">글번호</th>
										<th scope="col">제목</th>
										<th scope="col">마지막수정</th>
										<th scope="col">수정일</th>
									</tr>
									</thead>
									<tbody>
									<c:forEach var="share" items="${shares }">
										<tr style="border:1px solid #FFA2A2;">
											<td>${share.shareNo }</td>
											<td id="boardTitle"><a href="shareView?shareNo=${share.shareNo} ">${share.shareName }</a></td>
											<td>${share.userNick}</td>
											<td><fmt:formatDate value="${share.modifyDate }"
													pattern="yyyy-MM-dd HH:mm:ss" var="fmtDate" /> ${fmtDate }</td>
										</tr>
									</c:forEach>
									
									<c:if test="${totalPage ==0}">
										<td colspan="4">조회된 결과가 없습니다.</td>
									</c:if>
									</tbody>
								</table>
								
								<p id="pageNumber">
									<%!int i;%>
									<%
										for (int i = 1; i <= Integer.parseInt(request.getAttribute(("totalPage")).toString()); i++) {
									%>
									<a href="/share?page=<%=i%>"><%=i%> </a>
									<%}%>
								</p>
							</form>
						</section>
					</div>
				</div>
				
				<!-- footer -->
					<%-- <jsp:include page="../layout/footer.jsp"></jsp:include> --%>
				
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


