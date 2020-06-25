<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<TITLE>순위 정보</TITLE>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
 	<meta name="author" content="Nathan Searles">
	<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
	<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css?v=<%=System.currentTimeMillis() %>" />
	<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
	<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
	<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
	<%-- <link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/rank.css?v=<%=System.currentTimeMillis()%>" /> --%>

</HEAD>
<BODY onload="init()">
	
	
	<!-- Drop Menu Header -->
	<jsp:include page="../nonsession/layout/dropMenu_header.jsp" /> 
	<!-- Menu Bar Header -->
	<jsp:include page="../nonsession/layout/menubar_header.jsp" />
		
	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">
				
					<!-- Board Left Menu -->
						<jsp:include page="../nonsession/layout/board_left_menu.jsp" />
						
					<!-- Board body part -->
						<div class="8u 12u(mobile) important(mobile)">
							<section class="middle-content" >
								<form>
									<div>
										<section id='rank_form' class='rank'>
											<select id='game_no_select' onchange="select_action()">
											<option value="1">Game1</option>
											<option value="2">Game2</option>
											<option value="3">Game3</option>
											<option value="4">Game4</option>
											<option value="5">Game5</option>
											</select>
										</section>
										<section id='rank_info' class='rank'>
										
										</section>
										
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
	
	var selectUrl = "/gameUtil/selectGameCnt";
	var arg_game_no;
	
	function init(){
		console.log('init');
		select_action();
	}
	 
	function select_action(){
		arg_game_no = $("#game_no_select").val();
		$.ajax({
			type : "get",
			url : selectUrl,
			data : {
				gameNo : arg_game_no
			},
			success : function(res) {
				var innerHTML = '<table class="rank" style="border:2px solid #FFA2A2"><thead id="board_table_thead"><tr><td><strong>순위</strong></td><td><strong>인입</strong></td><td>ID</td><td><strong>점수</strong></td><tr></thead>';
				$(res.RESULT).each(function(idx,item){
					innerHTML += '<tr style="border:2px solid #FFA2A2"><td>'+item.ROWNUM+'</td><td>'+item.MEDIA_CODE+'</td><td>'+item.USER_ID+'</td><td>'+item.CNT+'</td><tr>';
				});
				innerHTML += '</table>';
				
				$('#rank_info')[0].innerHTML = innerHTML;
				
			},
			error : function(request, status, error) {
				alert(request);
			}
		});
	}
	
	</script>
	
</BODY>
</HTML>