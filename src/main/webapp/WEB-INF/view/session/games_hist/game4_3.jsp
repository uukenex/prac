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

	<c:set var="reverse" value="false" />
	<c:set var="range" value="50" />
	<c:set var="speed" value="1" />

	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script
		src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
		var is_reverse = false;
		var is_stoped = false; 
		var is_dup_speed = false;
		var rotateVar; //타이머 담을 변수 
		//var rotateSpeed = 5;
		var rotate_rate = 1;//배속
		
		var partition = 20;//원형구체 충돌 방지 간격
		
		var x = 200 // center
		var y = 200 // center
		var r = 50 // radius
		var a = 0 // angle (from 0 to Math.PI * 2)
		
		function init() {
			
			$('#btn_reverse').on('click',click_reverse);
			$('#btn_increase_r').click({param1: true}, click_r_incdec);
			$('#btn_decrease_r').click({param1: false},click_r_incdec);
			$('#btn_speed_fast').click({param1: true}, click_speed_control);
			$('#btn_speed_slow').click({param1: false},click_speed_control);
			
			//rotation(x,y,r,a,1); //1번의 회전시작 
			rotation(x,y+r*2+partition,r,a,2);//2번의 회전시작
			
			
			
		}
		
		
		
		function click_speed_control(event){
			var is_speed_up = event.data.param1;
			is_speed_up ? rotate_rate<10?rotate_rate+=1:rotate_rate=rotate_rate:rotate_rate>1?rotate_rate-=1:rotate_rate=rotate_rate;
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
		
		async function promise_r_incdec(seq,is_increase){
				return new Promise((resolve, reject) => {
					setTimeout(()=>{
						clearTimeout(rotateVar);
						is_increase?r+=2:r>10?r-=2:r=r;
						rotation(x,y,r,a,2);//2번의 회전시작
						resolve(seq);
					}, 15*seq); 
				}).then(function(result){
					if(seq==24){
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
				for(var rate=0;rate<rotate_rate;rate++){
					if(is_reverse){
						arga = (arga - Math.PI / 360) % (Math.PI * 2);
					}else{
						arga = (arga + Math.PI / 360) % (Math.PI * 2);	
					}
				}
				rotate(arga);
			}, 5);
			
			
			function rotate(arga) {
				center = '#center2';
				point = '#point2';
				
				document.querySelector(center).style.left = argx + "px";
				document.querySelector(center).style.top = argy + "px";
				document.querySelector(point).style.left = px + "px";
				document.querySelector(point).style.top = py + "px";
				px = argx + argr * Math.cos(arga);
				py = argy + argr * Math.sin(arga);	
				
				var rect = $('#enemy')[0].getBoundingClientRect();
				if(px > rect.left && px <rect.right && py > rect.top && py < rect.bottom){
					console.log('detected');
				}
				
				x=argx;
				y=argy;
				a=arga;
				
			}
		}
		
		
		
	</script>

	<section id="space">

		<div id="center2"></div>
		<div id="point2"></div>

		<div id="center2_2"></div>

		
		
		<div id="enemy"></div>
	</section>

	<section id="controll">
		<input type="button" id="btn_reverse" value="reverse" />
		</br> 
		reverse : <c:out value="${reverse}" /> 
		</br>
		<input type="button" id="btn_increase_r" value="range_up" /> 
		<input type="button" id="btn_decrease_r" value="range_down" />
		</br>
		range : <c:out value="${range}" /> 
		</br>
		<input type="button" id="btn_speed_fast" value="speed_up" /> 
		<input type="button" id="btn_speed_slow" value="speed_down" />
		</br>
		speed : <c:out value="${speed}" /> 
		</br> 
	</section>
</BODY>
</HTML>