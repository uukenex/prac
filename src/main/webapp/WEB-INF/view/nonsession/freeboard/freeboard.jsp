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
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css?v=20190808_01" />
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
		<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
		
		<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">

<title>자유 게시판</title>
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
						<jsp:include page="../layout/board_left_menu.jsp"></jsp:include>
					
					<!-- Board Body part -->
					<div class="8u 12u(mobile) important(mobile)">
						<section class="middle-content">
							<h2 class="board_part_title">자유 게시판</h2>
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
									<a href="/session/boardsign" ><input type="submit" value="글쓰기" class="writeBoard"></a>
									<input type="button" id="searchBtn" value="검색" />
								</li>
							</ul>
							
							<form>
								<table>
									<colgroup>
										<col width="20%" />
										<col width="*" />
										<col width="15%" />
										<col width="20%" />
										<col width="15%" />
									</colgroup>
									<thead id="board_table_thead">
									<tr>
										<th scope="col">글번호</th>
										<th scope="col">제목</th>
										<th scope="col">작성자</th>
										<th scope="col">작성일</th>
										<th scope="col">조회수</th>
									</tr>
									</thead>
									<tbody>
									<c:forEach var="comment" items="${comments }">
										<tr>
											<td>${comment.commentNo }</td>
											<td id="boardTitle"><a href="freeView?commentNo=${comment.commentNo} ">${comment.commentName }(${comment.replyCnt })</a></td>
											<td>${comment.userNick}</td>
											<td><fmt:formatDate value="${comment.commentDate }"
													pattern="yy-MM-dd" var="fmtDate" /> ${fmtDate }</td>
											<td>${comment.commentCount }</td>
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
									<a href="/free?page=<%=i%>"><%=i%> </a>
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
	
	
	<script src="http://code.jquery.com/jquery.js"></script>
	<script>
		$(document).on("ready", function() {
			if ("${message}" != null && "${message}" != ("")) {
				alert("${message}");
	<%session.removeAttribute("message");%>
		}
		})
		
		
		<c:url value="/search" var="search" />
		$("#searchBtn").on("click",function(){
			$.ajax({
			type:"post",
			url:"${search}",
			data:{
				category:$("#searchCategory").val(),
				keyword:$("#search").val()
			},
			success:function(res){
				$("tbody")[0].innerHTML="";
				$(res).each(function(idx,item){
				//item이 comment객체(user정보도 포함)
				console.log(item);
				console.log(item.commentDate);
				var date=new Date(item.commentDate);
				var year = date.getFullYear().toString();
				var month = (date.getMonth()+1).toString();
				var date = date.getDate().toString();
				if(date<10){
					date="0"+date;
				}
				year=year.substr(2,2);
				var newDate = year+"-"+month+"-"+date;
				
				$("tbody")[0].innerHTML+=
		"<tr>"
		+"<td>"+item.commentNo+"</td>"
		+"<td id='boardTitle'><a href='freeView?commentNo="+item.commentNo+"' >"+item.commentName +" </a></td>"
		+"<td>"+item.userNick+"</td>"
		+"<td>"+newDate+"</td>"
		+"<td>"+item.commentCount +"</td>"
		+"</tr>"
				});
			},
			error:function(xhr,status,error){
				alert(error);
			}
			}); 
		});
	</script>
	
	<script>
		var beforeBoard, nowBoard;
		beforeBoard = document.getElementById("current");
		beforeBoard.id = beforeBoard.id.replace("");
		$(".freeBoard").attr('id', 'current');
	</script>
	
</body>
</html>


