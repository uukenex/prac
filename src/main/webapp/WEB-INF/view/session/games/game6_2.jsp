<!-- 미사용 -->

<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>chat-chat</TITLE>
<%-- <link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game6.css?v=<%=System.currentTimeMillis()%>" /> --%>
</HEAD>
<BODY onload="init()">

	<input type="hidden" value='${userNick}' id='chat_id' />
	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<%-- <script src="<%=request.getContextPath()%>/game_set/js/websocket_module2.js?v=<%=System.currentTimeMillis()%>"></script> --%>
	<script src="<%=request.getContextPath()%>/game_set/map/game6_1_map0.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	var db_game_no = 6;
	var map = mapArr0;
	var map_1D_size = map.length; //A one-dimensional array size
	var map_2D_size = map.length*map.length; //A two-dimensional array size
	var map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
	
	function init()
    {
		initMakeCell();
		initWindow();
    }	
	
	function mapChange(mapNo){
		if(mapNo==0){
			map = mapArr0;
		}else{
			map = mapArr1;
		}
		
		map_1D_size = map.length; //A one-dimensional array size
		map_2D_size = map.length*map.length; //A two-dimensional array size
		map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
		
		initMakeCell();
	}
	
	function initMakeCell(){
		$('#memories > tbody').remove();
		$('#memories').append("<tbody>");
		for (var x = 0; x < map_1D_size; x++) {
			$('#memories > tbody:last').append("<tr>");
			for (var y = 0; y < map_1D_size; y++) {
				$('#memories > tbody:last > tr:last').append("<td class='cell' data-value="+map[x][y]+" />");
			}
			$('#memories > tbody:last').append("</tr>");
		}
		$('#memories').append("</tbody>");
		
		$('td').css('width', windowWidth / map_1D_size);
		$('td').css('height', windowWidth / map_1D_size);
	}
	
	
		
	//css only process
	/* 
	function initMakeCellColor(){
		//https://dololak.tistory.com/364
		
		//$('#memories > tbody > tr > td')[i].dataset.value;
		//$('#memories > tbody > tr > td')[i].dataset['value']
		//$('#memories > tbody > tr > td')[i].classList.add('m')
		
	}
	 */
	function initWindow(){

	
		$('#space').append("<input type='button' value='map0' onclick='mapChange(0)'>");
		$('#space').append("<input type='button' value='map1' onclick='mapChange(1)'>");
	}
	 
	 
	</script>

	<section id="space" class='space'>
		<table id="memories"></table>
	</section>
	
	<style type="text/css">
	table {
		padding-right: 15;
	}
	
	table tr td {
		text-align: center;
		border: solid 1px black;
		width: 30px;
		height: 30px;
	}
		
	/* css data-value control 
	https://css-tricks.com/almanac/selectors/a/attribute */
	#memories > tbody > tr > td[data-value="0"] {
		background: white;
	}
	#memories > tbody > tr > td[data-value="1"] {
		background: red;
	}
	#memories > tbody > tr > td[data-value="4"] {
		background: gray;
	}
	#memories > tbody > tr > td[data-value="9"] {
		background: blue;
	}
	</style>
</BODY>
</HTML>