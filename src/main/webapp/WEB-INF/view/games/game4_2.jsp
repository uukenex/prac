<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>원형이동 practice</TITLE>

<link rel="stylesheet"
	href="<%=request.getContextPath()%>/game_set/css/game4_2.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>


	<script language="javascript">
		var is_reverse = false;
		var is_stoped = false;
		
		var rotateVar;
		
		var partition = 20;//원형구체 충돌 방지 간격
		
		var x = 100 // center
		var y = 50 // center
		var r = 50 // radius
		var a = 0 // angle (from 0 to Math.PI * 2)
		
		function init() {
			
			$('#btn_reverse').on('click',click_reverse);
			$('#btn_start').on('click',click_start);
			$('#btn_stop').on('click',click_stop);
			
			//rotation(x,y,r,a,1); //1번의 회전시작 
			rotation(x,y+r*2+partition,r,a,2);//2번의 회전시작
			
		}
		
		
		function click_reverse(){
			is_reverse==true?is_reverse = false: is_reverse = true;
			clearTimeout(rotateVar);
			rotation(x,y,r,a,2);
		}
		function click_start(){
			clearTimeout(rotateVar);
			rotation(x,y,r,a,2);
		}
		function click_stop(){
			clearTimeout(rotateVar);
			
			var prevX = $('#center2')[0].getBoundingClientRect().x;
			var prevY = $('#center2')[0].getBoundingClientRect().y;
			var cursorX = $('#point2')[0].getBoundingClientRect().x;
			var curosrY = $('#point2')[0].getBoundingClientRect().y;
			
			
			var nextX = prevX - (cursorX-prevX);
			var nextY = prevY - (curosrY-prevY);
			document.querySelector('#center2_2').style.left = nextX + "px";
			document.querySelector('#center2_2').style.top = nextY + "px";
			
		}
		
		function rotation(argx,argy,argr,arga,divId){
			
			var center;
			var point;
			var px;
			var py;

			rotateVar = setInterval(function() {
				if(is_reverse){
					arga = (arga - Math.PI / 360) % (Math.PI * 2);
				}else{
					arga = (arga + Math.PI / 360) % (Math.PI * 2);	
				}
				rotate(arga);
			}, 5);
			
			
			function rotate(arga) {
				if(divId == 3){
					center = '#center3';
					point = '#point3';
				}else if(divId ==2){
					center = '#center2';
					point = '#point2';
				}else{
					center = '#center';
					point = '#point';
				} 
				
				document.querySelector(center).style.left = argx + "px";
				document.querySelector(center).style.top = argy + "px";
				document.querySelector(point).style.left = px + "px";
				document.querySelector(point).style.top = py + "px";
				px = argx + argr * Math.cos(arga);
				py = argy + argr * Math.sin(arga);	
				
				x=argx;
				y=argy;
				r=argr;
				a=arga;
				
			}
		}
		
		
		
	</script>

	<div id="center"></div>
	<div id="point"></div>
	
	
	<div id="center2"></div>
	<div id="point2"></div>
	
	<div id="center2_2"></div>
	
	<div>
		<input type="button" id="btn_reverse" value="reverse"/>
		<input type="button" id="btn_start" value="start"/>
		<input type="button" id="btn_stop" value="stop"/>
	</div>
</BODY>
</HTML>