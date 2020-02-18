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
		var reverse = false;
		
		function init() {
			var partition = 20;//원형구체 충돌 방지 간격
			
			var x = 100 // center
			var y = 50 // center
			var r = 50 // radius
			var a = 0 // angle (from 0 to Math.PI * 2)
			
			
			$('#reverse').on('click',click);
			
			rotation(x,y,r,a,1); //1번의 회전시작 
			rotation(x,y+r*2+partition,r,a,2);//2번의 회전시작
			
		}
		
		
		function click(){
			reverse==true?reverse = false: reverse = true;
		}
		
		function rotation(argx,argy,argr,arga,divId){
			function rotate(arga) {

				var px;
				var py;
				
				if(reverse && divId ==2){
					/*reverse 구현이 이상하게됨 */
					px = argx - argr * Math.cos(Math.PI-arga);
					py = argy - argr * Math.sin(Math.PI-arga);
				}else{
					px = argx + argr * Math.cos(arga);
					py = argy + argr * Math.sin(arga);	
				}
				
				var center;
				var point;
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
				
			}

			setInterval(function() {
				arga = (arga + Math.PI / 360) % (Math.PI * 2);
				rotate(arga);
			}, 5);
		}
	</script>

	<div id="center"></div>
	<div id="point"></div>
	
	
	<div id="center2"></div>
	<div id="point2"></div>
	
	<div>
		<input type="button" id="reverse" value="reverse"/>
	</div>
</BODY>
</HTML>