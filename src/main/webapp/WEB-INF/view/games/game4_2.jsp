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
	<script src="<%=request.getContextPath() %>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis() %>"></script>
	<script language="javascript">
		var is_reverse = false;
		var is_stoped = false; 
		var is_dup_speed = false;
		var rotateVar; //타이머 담을 변수 
		var rotateSpeed = 5;
		
		var partition = 20;//원형구체 충돌 방지 간격
		
		var x = 200 // center
		var y = 200 // center
		var r = 50 // radius
		var a = 0 // angle (from 0 to Math.PI * 2)
		
		function init() {
			
			$('#btn_reverse').on('click',click_reverse);
			$('#btn_increase_r').click({param1: true},click_r_incdec);
			$('#btn_decrease_r').click({param1: false},click_r_incdec);
			
			//rotation(x,y,r,a,1); //1번의 회전시작 
			rotation(x,y+r*2+partition,r,a,2);//2번의 회전시작
			
		}
		
		
		function click_r_incdec(event){
			var is_increase = event.data.param1;
			
			if(is_dup_speed){//중복실행방지 
				return;
			} 
			is_dup_speed = true;
			
			for(var i=0;i<25;i++){
				promise_r_incdec(i,is_increase);
			}
			
			
		}
		
		async function promise_r_incdec(i,is_increase){
				return new Promise((resolve, reject) => {
					setTimeout(()=>{
						clearTimeout(rotateVar);
						is_increase?r+=2:r>10?r-=2:r=r;
						rotation(x,y,r,a,2);//2번의 회전시작
						resolve(i);
					}, 15*i); 
				}).then(function(result){
					if(i==24){
						is_dup_speed = false; //마지막 promise가 종료될때 중복플래그를 종료 
					}
				}) ;
		}
		
		function click_reverse(){
			is_reverse==true?is_reverse = false: is_reverse = true;
			clearTimeout(rotateVar);
			
			
			var prevX = $('#center2')[0].getBoundingClientRect().x;
			var prevY = $('#center2')[0].getBoundingClientRect().y;
			var cursorX = $('#point2')[0].getBoundingClientRect().x;
			var curosrY = $('#point2')[0].getBoundingClientRect().y;
			
			
			var nextX = prevX + 2*(cursorX-prevX);
			var nextY = prevY + 2*(curosrY-prevY);
			
			document.querySelector('#center2_2').style.left = nextX + "px";
			document.querySelector('#center2_2').style.top = nextY + "px";
			x= nextX;
			y= nextY;
			a = a >= Math.PI?a-Math.PI:a+Math.PI;	
			
			rotation(x,y,r,a,2);
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
			}, rotateSpeed);
			
			
			function rotate(arga) {
				center = '#center2';
				point = '#point2';
				
				document.querySelector(center).style.left = argx + "px";
				document.querySelector(center).style.top = argy + "px";
				document.querySelector(point).style.left = px + "px";
				document.querySelector(point).style.top = py + "px";
				px = argx + argr * Math.cos(arga);
				py = argy + argr * Math.sin(arga);	
				
				x=argx;
				y=argy;
				a=arga;
				
			}
		}
		
		
		
	</script>
	
	<div id="space">
	
		<div id="center2"></div>
		<div id="point2"></div>
		
		<div id="center2_2"></div>
		
		<div>
			<input type="button" id="btn_reverse" value="reverse"/>
			<input type="button" id="btn_increase_r" value="increase"/>
			<input type="button" id="btn_decrease_r" value="decrease"/>
		</div>
	</div>
	
	
</BODY>
</HTML>