<%
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Cache-Control", "no-store");
    response.setDateHeader("Expires", 0);
    response.setHeader("Pragma", "no-cache");
%>

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

<title>${comment.commentName} ::: TH-HOME</title>

</head>
<body>

	<!-- Drop Menu Header -->
	<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
	<jsp:include page="../../nonsession/layout/menubar_header.jsp" />

	<!-- jqeury 이후 호출 -->
	<script src="<%=request.getContextPath()%>/game_set/js/comutil.js?v=<%=System.currentTimeMillis()%>"></script>

	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">

					<!-- Board Left Menu -->
						<jsp:include page="../../nonsession/layout/board_left_menu.jsp" />

					<!-- Board body part -->
						<div class="8u 12u(mobile) important(mobile)">
							<section class="middle-content" >
								<form>
									<div>
										<section>
											<input type="button" value="목록" id="listview" class="boardButtonStyle2">
											<c:if test="${comment.userId==Users.userId }">
											<input type="submit" value="수정하기" formaction="/session/secretUpdateForm" formmethod="post">
											<input type="submit" value="삭제하기" formaction="/session/secretDelete" formmethod="post" onclick="return confirm('삭제하시겠습니까?');">
											</c:if>
										</section>
										<hr id="boardTitleHrStyle1">
										<h3 id="boardTitleSytle1">🔒 ${comment.commentName}</h3>
										<input type="hidden" name="commentNo" value="${comment.commentNo }" />
										<hr id="boardTitleHrStyle1">
										<table style="border:2px solid #1DDB16">
											<colgroup>
												<col width="15%" />
												<col width="17%" />
												<col width="15%" />
												<col width="*%" />
											</colgroup>
											<tr>
												<td>작성자</td>
												<input type="hidden" name="userId" value="${comment.userId }" />
												<td class="boardTitleSort boardFontBold">${comment.userNick}</td>
												<td>작성일</td>
												<td class="boardTitleSort boardFontBold2">
													<fmt:formatDate value="${comment.commentDate}"
																			pattern="yyyy-MM-dd HH:mm:ss" var="fmtDate" /> ${fmtDate}
												</td>
											</tr>
												<tr style="border:1px solid #1DDB16;" >
													<td colspan="4" class="boardTitleSort" style="padding-bottom: 50px; height: 150px;">${comment.commentContent}</td>
												</tr>
										</table>
									</div>

									<hr id="boardTitleHrStyle2">
									<div>
										<table class="board_view" border="1">
												<colgroup>
													<col width="15%">
													<col width="17%">
													<col width="15%">
													<col width="*%">
												</colgroup>

												<c:forEach var="reply" items="${replys }">
												<tbody id="replyContentViewTableBody" style="border:2px solid #FFA2A2">
								            	<tr>
								            		<td>작성자</td>
								            		<td class=boardFontBold>${reply.userNick}</td>
								            		<td>작성일</td>
								            		<td class="boardTitleSort">
								            			<fmt:formatDate value="${reply.replyDate }"
														pattern="yy-MM-dd HH:mm:ss" var="fmtDate" /> ${fmtDate}
													</td>
								            	</tr>
												<tr>
													<td colspan="4" class="boardTitleSort" style="border:1px solid #FFA2A2">
													<pre style="padding-bottom: 50px;"> ${reply.replyContent }</pre>
													</td>
												</tr>
										</c:forEach>
										</tbody>
									</table>
									<hr id="boardTitleHrStyle2">

									</div>

									<!-- 답변 작성하는 부분 -->
									<div id="boardReplyWritter">
										<table>
											<colgroup>
												<col width="10%" />
												<col width="*%" />
												<col width="10%" />
											</colgroup>
											<tr>
												<td><label for="replyContent"><i class="fa fa-key fa-2x"></i></label></td>
												<td>
													<textarea cols="75" rows="2" id="replyContent" placeholder="댓글을 입력하세요."></textarea>
												</td>
												<td>
													<input type="button" value="등록" id="replyRegist" class="boardButtonStyle3">
												</td>
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
			location.href = "/session/secret?page=1";
		});

		<c:url value="/session/replyRegist" var="replyRegist" />

		$("#replyRegist").on("click",function(){
			$.ajax({
			type:"post",
			url:"${replyRegist}",
			data:{
				userId:"${Users.userId}",
				replyContent:$("#replyContent").val(),
				commentNo:"${comment.commentNo}"
			},
			success:function(res){
				alert("등록되었습니다.");
				location.reload();
			},
			error:function(request,status,error){
				alert(request.responseText);
			}
			});
		});

		$(document).on("ready", function() {
			resize_event();
		});

		var img = document.getElementsByTagName('img');
		for (var x = 0; x < img.length; x++) {
			img.item(x).onclick=function() {
				var img = new Image;
				img.src = this.src;
				var w = window.open("",'_blank','width=880,height=510');
				w.document.write(img.outerHTML);
			};
		}
	</script>

</body>
</html>
