<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>따뜻한 가메2</TITLE>

<link rel="stylesheet"
	href="<%=request.getContextPath()%>/game_set/css/game4_1.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="onloadFunc()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>


	<script language="javascript">
		function onloadFunc() {
			init();
		}
	
		
		function init(){
			var canvas = document.getElementById("canvas");
			var ctx = canvas.getContext("2d");

			// set context styles
			ctx.lineWidth = 15;
			ctx.strokeStyle = '#85c3b8';
			ctx.shadowColor = "black"
			ctx.shadowOffsetX = 0;
			ctx.shadowOffsetY = 0;
			ctx.shadowBlur = 0;
			ctx.font = "18px verdana";

			var quart = Math.PI / 2;
			var PI2 = Math.PI * 2;
			var percent = 0;

			var guages = [];
			guages.push({
			    x: 50,
			    y: 100,
			    radius: 40,
			    start: 0,
			    end: 100,
			    color: "blue"
			});
			
			<%--
			guages.push({
			    x: 200,
			    y: 100,
			    radius: 40,
			    start: 0,
			    end: 90,
			    color: "green"
			});
			guages.push({
			    x: 50,
			    y: 225,
			    radius: 40,
			    start: 0,
			    end: 35,
			    color: "gold"
			});
			guages.push({
			    x: 200,
			    y: 225,
			    radius: 40,
			    start: 0,
			    end: 55,
			    color: "purple"
			});
			--%>
			animate();

			function drawAll(percent) {

			    // clear the canvas

			    ctx.clearRect(0, 0, canvas.width, canvas.height);

			    // draw all the guages

			    for (var i = 0; i < guages.length; i++) {
			        render(guages[i], percent);
			    }

			}

			function render(guage, percent) {
				
			    var pct = percent / 100;
			    var extent = parseInt((guage.end - guage.start) * pct);
			    var current = (guage.end - guage.start) / 100 * PI2 * pct - quart;
			    ctx.beginPath();
			    ctx.arc(guage.x, guage.y, guage.radius, -quart, current);
			    ctx.strokeStyle = guage.color;
			    ctx.stroke();
			    ctx.fillStyle = guage.color;
			    ctx.fillText(extent, guage.x - 15, guage.y + 5);
			}


			function animate() {

			    // if the animation is not 100% then request another frame

			    if (percent < 100) {
			        requestAnimationFrame(animate);
			    }

			    // redraw all guages with the current percent

			    drawAll(percent);

			    // increase percent for the next frame

			    percent += 1;
			    
			    if(percent == 100){
			    	percent = 0;
			    }
			    

			}

			$("#again").click(function () {
			    percent = 0;
			    animate();
			});
		}
		
	</script>
	<button id="again">Again</button>
	<br>
	<canvas id="canvas" width=300 height=300></canvas>
	<section id='field'>

		<!-- <div id="circle1">원 만들기</div>
		<br>
		<div id="circle2">원 만들기2</div>
		<br>
		<div id="circle3">원 만들기3</div> -->
	</section>
</BODY>
</HTML>