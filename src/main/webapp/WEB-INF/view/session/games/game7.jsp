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
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game6.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	<input type="hidden" value='${userNick}' id='chat_id' />
	
	<script src="http://code.jquery.com/jquery.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<%-- <script src="<%=request.getContextPath()%>/game_set/js/websocket_module2.js?v=<%=System.currentTimeMillis()%>"></script> --%>
	<script src="<%=request.getContextPath()%>/game_set/map/game7_map0.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	var db_game_no = 7;
	var map = mapArr0;
	var map_1D_size = map.length; //A one-dimensional array size
	var map_2D_size = map.length*map.length; //A two-dimensional array size
	var map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
	
	function init()
    {
		initMakeCell();
		initMakeCellColor();
    }	
	
	function initMakeCell(){
		$('#memories').append("<tbody>");
		for (var x = 0; x < map_1D_size; x++) {
			$('#memories > tbody:last').append("<tr>");
			for (var y = 0; y < map_1D_size; y++) {
				$('#memories > tbody:last > tr:last').append("<td class='cell' data-value="+map[x][y]+" />");
			}
			$('#memories > tbody:last').append("</tr>");
		}
		$('#memories').append("</tbody>");
	}
		
	function initMakeCellColor(){
		//https://dololak.tistory.com/364
		//$('#memories > tbody > tr > td')[i].dataset.value;
		for(var i=0;i<map_2D_size;i++){
			switch($('#memories > tbody > tr > td')[i].dataset['value']){
				case '0': 
					$('#memories > tbody > tr > td')[i].classList.add('m');
					break;
				case '1': 
					$('#memories > tbody > tr > td')[i].classList.add('s');
					break;
				case '4': 
					$('#memories > tbody > tr > td')[i].classList.add('r');
					break;
				case '9': 
					$('#memories > tbody > tr > td')[i].classList.add('e');
					break;
			}
		}
		
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
	}
	</style>
</BODY>
</HTML>