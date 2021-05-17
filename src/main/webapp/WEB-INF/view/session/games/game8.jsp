<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />
<meta property="og:title" content="알파카 연구소 dev">  
<meta property="og:type"  content="website">
<meta property="og:image" content="<%=request.getContextPath()%>/game_set/img/paimon.jpg?v=<%=System.currentTimeMillis()%>">
<meta property="og:description" content="알파카 연구소 채팅방">
<TITLE>0326 dev</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game8.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	<input type="hidden" value='${userNick}' id='chat_id' />
	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/map/game8_map0.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	
	var map = mapArr0;
	var map_1D_size = map.length; //A one-dimensional array size
	var map_2D_size = map.length*map.length; //A two-dimensional array size
	var map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
	
	var map_x_size = 400;
	var map_y_size = 400;
	
	var db_game_no = 8;
	
	
	function init()
    {
		
		
		
		var v_chat_timer;//clear timer variable - chat floating box
		var v_sp_count = 0;
		
		var main_interval;
		var sub_interval;
		
		var last_key;
		
		initMakeCell();
		
    	$(function(){
    		var keyin = '', // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 2;
    		
    		
    		if(charx < 0) charx=0;
			if(chary < 0) chary=0;
			if(charx > map_x_size-20) charx=map_x_size-20;
			if(chary > map_y_size-20) chary=map_y_size-20;
			
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			
    		}, 10); // 매 0.01 초마다 실행
    		
			sub_interval = setInterval(function(){ // 주기적으로 검사
				
    		}, 10); // 매 0.01 초마다 실행
    		
        	$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
        		var key = e.which.toString();
        		keyin = key;
        		
        		
        		switch (keyin){
					case '37':
						console.log('37');//left
						break;
					case '38':
						console.log('38');//up
						break;
					case '39':
						console.log('39');//right
						break;
					case '40':
						console.log('40');//down
						break;
				}
        	
        	});
        	$(document).keyup(function(e){ // 눌렸던 키를 해제
        		var key = e.which.toString();
        		
        	});
    	});
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
		
		var cell_bitween_size = Number($('.cell').css('padding').replace('px',''))*2;
		
		$('td').css('width', (map_x_size-(map_1D_size*cell_bitween_size-1)) / map_1D_size);
		$('td').css('height', (map_y_size-(map_1D_size*cell_bitween_size-1)) / map_1D_size);
		
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
		
	</script>

	<section id="space" class='space'>
		<div id="bullet_field"></div>
		<table id="memories"></table>
	</section>
	
</BODY>
</HTML>