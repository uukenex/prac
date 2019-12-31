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

<title>자유 게시판: ${comment.commentName}</title>

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
										</section>
										<hr id="boardTitleHrStyle1">
										<h3 id="boardTitleSytle1">${comment.commentName}</h3>
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
													<td colspan="4" class="boardTitleSort"  style="padding-bottom: 50px; height: 150px;">${comment.commentContent1}</td>
												</tr>
											<tr>
												<td colspan="4">
													<ul class="boardButtonList">
														<li>
																												<c:choose>
													 		<c:when test="${comment.userId==Users.userId }">
													 		<!-- <input type="submit" value="수정" formaction="/session/freeUpdate" formmethod="post" class="boardButtonStyle1"> -->
																<!-- <input type="submit" value="삭제" formaction="/freeDelete" formmethod="post" class="boardButtonStyle1"> -->
																
															</c:when>
																<c:when test="${'THJEON'==Users.userId }">
																<!-- <input type="submit" value="수정" formaction="/session/freeUpdate" formmethod="post" class="boardButtonStyle1"> -->
																<!-- <input type="submit" value="삭제" formaction="/freeDelete" formmethod="post" class="boardButtonStyle1"> -->
																
															</c:when>
															</c:choose>
													 <%-- 		<c:if test="${comment.userId==Users.userId }">
																<input type="submit" value="삭제" formaction="/freeDelete"
																			formmethod="post" class="boardButtonStyle1">
															</c:if>
															<c:if test="${'THJEON'==Users.userId }">
																<input type="submit" value="삭제" formaction="/freeDelete"
																			formmethod="post" class="boardButtonStyle1">
															</c:if>
													 	</li>
													 	<li>
													 		<c:if test="${comment.userId==Users.userId }">
																<input type="submit" value="수정" formaction="/session/freeUpdate"
																			formmethod="post" class="boardButtonStyle1">
															</c:if>
															<c:if test="${'THJEON'==Users.userId }">
																<input type="submit" value="수정" formaction="/session/freeUpdate"
																			formmethod="post" class="boardButtonStyle1">
															</c:if> --%>
													 	</li>
													 </ul>
												</td>
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
													<%--
													<td class="board_reply_button_view">
														<c:choose>
													 		<c:when test="${reply.userId==Users.userId }">
																														<input type="hidden" name="replyNo" value="${ reply.replyNo}">
																<!-- <input type="submit" value="삭제" formaction="/replyDelete" formmethod="post" class="boardButtonStyle1"> -->					
															</c:when>
																<c:when test="${'THJEON'==Users.userId }">
																														<input type="hidden" name="replyNo" value="${ reply.replyNo}">
																<!-- <input type="submit" value="삭제" formaction="/replyDelete" formmethod="post" class="boardButtonStyle1"> -->	
																
															</c:when>
															</c:choose>
													</td>
													 --%>
								            	</tr>
												<tr>
													<td colspan="4" class="boardTitleSort" style="border:1px solid #FFA2A2">
													<pre style="padding-bottom: 50px;"> ${reply.replyContent1 }</pre> 
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
												<c:if test="${!empty Users.userId }">
												<td>
													<textarea cols="75" rows="2" id="replyContent" placeholder="댓글을 입력하세요."></textarea>
												</td>
												<td>
													<input type="button" value="등록" id="replyRegist" class="boardButtonStyle3">
												</td>
												</c:if>
												<c:if test="${empty Users.userId }">
												<td>
													<textarea cols="75" rows="2" id="replyContent" placeholder="로그인 해주세요" readonly="readonly"></textarea>
												</td>
				
												</c:if>
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


<script src="http://code.jquery.com/jquery.js"></script>
	<script>
	
		$("#listview").on("click", function() {
			location.href = "free?page=1";
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
				/*
				console.log(res);
				$("#replyContentViewTableBody").empty();
				console.log($("#replyContentViewTableBody")[0]);
				$("#replyContent").val("");
				alert("등록완료");
				$(res).each(function(idx,item){
					console.log(item);
				
					console.log(item.replyCommentNo);
					
					var replyId = item.userId;
					var date=new Date(item.replyDate);
					var year = date.getFullYear().toString();
					var month = (date.getMonth()+1).toString();
					var day = date.getDate().toString();
					var hour = date.getUTCHours();
					var minute= date.getMinutes();
					var second= date.getSeconds();
					if(day<10){
						day="0"+day;
					}
					year=year.substr(2,2);
					var newDate = year+"-"+month+"-"+day+" "+hour+":"+minute+":"+second;
					if(replyId=='${Users.userId }'){
						$("#replyContentViewTableBody")[0].innerHTML+=
							"<tr> <td class='boardFontBold'>" + item.userNick 
							+"</td> <td> <i class='fa fa-ellipsis-v'> </i> </td>"
							+"<td class='boardTitleSort'>"
							+newDate + "</td> <td>" 
							*/
							/* 
							알고보니없음...
							+"<input type='submit' value='수정' formaction='/session/replyUpdate'"
							+"formmethod='post' class='boardButtonStyle1'>" 
							+"</td> <td>" 
							*/
							/* +"<input type='hidden' name='replyNo' value='${ reply.replyNo}'>"
							+"<input type='submit' value='삭제' formaction='/replyDelete'"
							+"formmethod='post' class='boardButtonStyle1'>" */ 
					/*
							+"</td> </tr>" 
							+"<tr> <td colspan='4' class='boardTitleSort'>" 
							+"<pre>"+item.replyContent1 + "</pre>"+"</td> </tr>";
					}
					else{
						$("#replyContentViewTableBody")[0].innerHTML+=
							"<tr> <td class='boardFontBold'>" + item.userNick 
							+"</td> <td> <i class='fa fa-ellipsis-v'> </i> </td>"
							+"<td class='boardTitleSort'>"
							+newDate + "</td> <td>" 
							+"</td> </tr>" 
							+"<tr> <td colspan='4' class='boardTitleSort'>" 
							+"<pre>"+item.replyContent1 + "</pre>"+ "</td> </tr>";
					}
					
				});*/
			},
			error:function(request,status,error){
				alert(request.responseText);
			}
			});
		});
		
		
		
		
		$(document).on("ready", function() {
			if ("${message}" != null && "${message}" != ("")) {
				alert("${message}");
	<%session.removeAttribute("message");%>
		}
		});
	</script>
	
	<script>
		//var beforeBoard, nowBoard;
		//beforeBoard = document.getElementById("current");
		//beforeBoard.id = beforeBoard.id.replace("");
		//$(".freeBoard").attr('id', 'current');
	</script>
	
	
</body>
</html>