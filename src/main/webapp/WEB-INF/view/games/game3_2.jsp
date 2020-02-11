<%@page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>PROJECT2</TITLE>

<style type="text/css">
table {
	margin-top: 15px;
	margin-right: 15px;
	border: solid 1px black;
}

table tr td {
	text-align: center;
	/* border: solid 1px black; */
	background: #FFEEFF;
	color: #FFFFFF;
}

.route {
	background: yellow;
}

.destination {
	background: #0000FB;
}

.start {
	background: red;
}

.position {
	background: black;
}

.rock {
	background: gray;
}

.r0 {
	background: #720000;
}

.r1 {
	background: #72FF00;
}
.red {
	background: #72FF00;
}
</style>
<!-- 
style은 늦게 서술한것으로 덮어씌워짐
route < destination =start < position

-->

</HEAD>
<BODY onload="onloadFunc()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script language="javascript">
	<!-- 전역 변수 -->
		var cellCnt = 4;
		var cellLength = (cellCnt - 1).toString().length; //cellCnt의 자릿수 체크 , substr하기 위함
		var cellRockCnt = 2; //벽 갯수
		var moveSpeed = 1 * 1 / cellCnt * 1000;//timer 속도, cellCnt가 변경되어도 끝에서 끝까지 속도는 같음  

		var array;
		var MAX_SIZE = 100;//queue size
		var isDebug = true;
		
		var direct = 'right';//방향 right left up down 의 약자 사용 
		var x = 0;
		var y=  0;
		
		var movement;//timer 변수
		var moveSpeed = 1*1000; //1초마다 한칸 이동 
		
		//지렁이 게임 
		function onloadFunc() {
			array = initMakeCell(); //cell table생성
			initStartPos(x,y);//startPos생성
			initRock();//rock 생성
			initWindow();//윈도우 크기 등 css설정
			initEndPos(2,2);//imsi EndPos생성
			//anotherProcess();
			//addCellEvent();
			initKeyEvent();
			initTimer();
		}

		function initKeyEvent(){
			$(document).keydown(function(e) {
				//좌 37 상 38 우 39 하 40
				switch(e.keyCode){
					case 37:
						direct ='left';
						$('.position')[0].innerHTML = '←';
						break;
					case 38:
						direct ='up';
						$('.position')[0].innerHTML = '↑';
						break;
					case 39:
						direct ='right';
						$('.position')[0].innerHTML = '→';
						break;
					case 40:
						direct ='down';
						$('.position')[0].innerHTML = '↓';
						break;
				}
			});
			
		}
		
		function changeFunc(arg){
			console.log(arg);
		}
		
		function addCellEvent(arg_x,arg_y){
			var id = arg_x+arg_y;
			
			
			$('#'+id)[0].addEventListener('change', function() {
				console.log(id+'의 값 변경 감지');
				if($('#'+id).val()=='9'){
					console.log('끝');
					clearTimeout(movement);
					$('#'+id).addClass('red');
				}
			});
			
			$('#'+id).change(function(){
				console.log(id+'의값변경');
			});
			//console.log(id+'이벤트등록됨');
			/*
			addListener($('#'+x+y)[0],'change', function() {
				
				if(array[x][y]=='9'){
					console.log('끝');
					$('#'+x+y).addClass('red');
				}
			});
			*/
			/*
			array.addEventListener('change',function(){
				console.log('array change');
			});
			*/
			
			/*
			$('#'+id)[0].addEventListener('observe', function() {
				
				if(array[arg_x][arg_y]=='9'){
					console.log('끝');
					clearTimeout(movement);
					$('#'+id).addClass('red');
				}
			});
			
			*/
			/*
			
			$('#num1')[0].addEventListener('mousedown', function() {
				if ($('#num1')[0].style.background == 'black') {
					addGetCnt();
				}
				$('#num1')[0].style.background = 'gray';
			});
			*/
		}
		
		function initTimer(){
			var point = 0 ;
			
			clearTimeout(movement);//이동 timer 중복실행방지
			movement = setInterval(moveFunc, moveSpeed);//이동 timer 실행
			
			
			
			
			
			function moveFunc(){
				
				point++;
				console.log(point);
				
				
				
				switch(direct){
					case 'left':
						if(x-1 < 0){
							//console.log('꽝');
							//clearTimeout(movement);
							$('#'+y+x)[0].value ='9'
							//array[y][x] = '9';
							//$('#'+x+y).addClass('red');
							return;
						}
						array[y][x] = '0'; 
						$('.position').removeClass('position');
						x--;
						console.log('x--');
						break;
					case 'up':
						if(y-1 < 0){
							//console.log('꽝');
							//clearTimeout(movement);
							//array[y][x] = '9';
							$('#'+y+x)[0].value ='9'
							//$('#'+x+y).addClass('red');
							return;
						}
						array[y][x] = '0'; 
						$('.position').removeClass('position');
						y--;
						console.log('y--');
						break;
					case 'right':
						if(Number(x)+1 >= cellCnt){
							//console.log('꽝');
							//clearTimeout(movement);
							//array[y][x] = '9';
							$('#'+y+x)[0].value ='9'
							//$('#'+x+y).addClass('red');
							return;
						}
						array[y][x] = '0'; 
						$('.position').removeClass('position');
						x++;
						console.log('x++');
						break;
					case 'down':
						if(Number(y)+1 >= cellCnt){
							//console.log('꽝');
							//clearTimeout(movement);
							//array[y][x] = '9';
							$('#'+y+x)[0].value ='9'
							//$('#'+x+y).addClass('red');
							return;
						}
						array[y][x] = '0'; 
						$('.position').removeClass('position');
						y++;
						console.log('y++');
						break;

				}
				
				array[y][x] = '1';
				$('#'+x+y).addClass('position');
				
				
				if(point==100){
					clearTimeout(movement);
					console.log('종료');
				}
				
				if(point==10 || point==20 ||point==30 ||point==40){
					clearTimeout(movement);
					moveSpeed = moveSpeed*0.8;
					console.log('속도 증가.. 현재 moveSpeed : '+moveSpeed);
					movement = setInterval(moveFunc, moveSpeed);
				}

			}
			
			
			
			
			function abc(){
				return;
			}
			
			
		}
		
		
		
		function clearCell(){
			var x;
			var y;
			for ( x = 0 ;  x < cellCnt ; x++ ){
				for ( y = 0 ; y < cellCnt ; y++ ){
					if( array[y][x]=='r' || array[y][x]=='s' ){
						continue;
					}else{
						array[y][x] = '0';
					}
				}
			}
			
			return null;
		}
		
		
		function getCell(arg0){
			var x;
			var y;
			for ( x = 0 ;  x < cellCnt ; x++ ){
				for ( y = 0 ; y < cellCnt ; y++ ){
					if(array[y][x]==arg0){
						return y+""+x;
					}
				}
			}
			
			return null;
		}
		
		function getRandomInt(min, max) {
			return Math.floor(Math.random() * (max - min + 1)) + min;
		}
		function pad(n, width) {
			n = n + '';
			return n.length >= width ? n : new Array(width - n.length + 1)
					.join('0')
					+ n;
		}
		function initWindow(){
			var windowWidth = $(window).width();
			if (windowWidth > 720) {
				windowWidth = 720;
			}
			$('table').css('width', windowWidth - 20);
			$('table').css('height', windowWidth);
			$('html, body').css({
				'overflow' : 'hidden',
				'height' : '100%'
			});
			$('td').css('width', windowWidth / cellCnt);
			$('td').css('height', windowWidth / cellCnt);
			
			$('td').css('font-size','10px');
			
			document.oncontextmenu = function(e) {
				return false;
			}
		}
		
		function initMakeCell(){
			var cellField = "";
			var cellArr = Array(cellCnt).fill(null).map(() => Array());
			
			var createY = 0;
			var createX = 0;
			
			for (createY = 0; createY < cellCnt; createY++) {
				cellField += "<tr>";
				for (createX = 0; createX < cellCnt; createX++) {
					if(isDebug){
						cellField += "<td id=" + pad(createX, cellLength) + pad(createY, cellLength) + " value='0' onchange='changeFunc(this)' >"+createY+createX+"</td>";
						//cellField += "<input type='hidden' id=" + pad(createX, cellLength) + pad(createY, cellLength) + " value='0' onchange='changeFunc(this)' >"+createY+createX+"</td>";
						
					}else{
						cellField += "<td id=" + pad(createX, cellLength) + pad(createY, cellLength) + " value='0' onchange='changeFunc(this)' ></td>";	
					}
					
					cellArr[pad(createX, cellLength)][pad(createY, cellLength)] = '0' ;
					
				}
				cellField += "</tr>";
			}

			$('#space')[0].innerHTML = cellField;
			
			if(isDebug){
				console.log(cellArr);	
			}
			for (createY = 0; createY < cellCnt; createY++) {
				for (createX = 0; createX < cellCnt; createX++) {
					addCellEvent(pad(createX, cellLength),pad(createY, cellLength));
				}
			}
			
			return cellArr;
			
		}
		function initStartPos(x,y){
			//시작위치 지정
			$('#'+pad(x,cellLength)+pad(y,cellLength)).attr('class', 'position');
			array[y][x] = 's';
		}
		function initEndPos(x,y){
			//시작위치 지정
			$('#'+pad(x,cellLength)+pad(y,cellLength)).attr('class', 'destination');
			array[x][y] = 'e';
		}
		
		function initRock(){
			var i = 0;
			while(i < cellRockCnt){
				var x = pad(getRandomInt(0, cellCnt-1),cellLength);
				var y = pad(getRandomInt(0, cellCnt-1),cellLength);
				
				if(array[x][y] == 's' || array[x][y] == 'r'){
					if(isDebug){
						console.log(y+x+' started or already rock retry');
					}
					continue;
				}
				if(isDebug){
					console.log(y+x+' rock created');
				}
				
				$('#'+y+x).attr('class', 'rock'); // y가 세로축임 
				array[x][y] = 'r';
				i++;
			}
			
		}
	</script>

	<table id="space">
	</table>
	<div id="score">
		
	</div>
	<div id="stop">
		<input type="button" value="stop" id="btn_stop">
	</div>
	<div id="restart">
		<input type="button" value="restart" id="btn_restart">
	</div>
</BODY>
</HTML>