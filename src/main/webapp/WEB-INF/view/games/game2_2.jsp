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
}

.route {
	background: yellow;
}

.destination {
	background: blue;
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
		var isDebug = true;
		
		function onloadFunc() {
			array = initMakeCell(); //cell table생성
			initStartPos(0,0);//startPos생성
			initRock();//rock 생성
			initWindow();//윈도우 크기 등 css설정
			initAddCellEvent();//cell event추가 -> moveFunction 구현
		}

		function moveFunction(arg0) {
			arg0.mousedown(function(e) {
				getClickArrayInfo(arg0);
			});
		}

		function getClickArrayInfo(arg0){
			var x = arg0[0].id.substr(0, cellLength);
			var y = arg0[0].id.substr(cellLength, cellLength);
			console.log(y+x+' clicked : array val='+array[y][x]);
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
			
			for (var createY = 0; createY < cellCnt; createY++) {
				cellField += "<tr>";
				for (var createX = 0; createX < cellCnt; createX++) {
					if(isDebug){
						cellField += "<td id=" + pad(createX, cellLength) + pad(createY, cellLength) + ">"+createY+createX+"</td>";
					}else{
						cellField += "<td id=" + pad(createX, cellLength) + pad(createY, cellLength) + "></td>";	
					}
					
					cellArr[pad(createX, cellLength)][pad(createY, cellLength)] = '0' ;
				}
				cellField += "</tr>";
			}

			$('#space')[0].innerHTML = cellField;
			
			if(isDebug){
				console.log(cellArr);	
			}
			return cellArr;
			
		}
		
		function initAddCellEvent(){
			for (var createY = 0; createY < cellCnt; createY++) {
				for (var createX = 0; createX < cellCnt; createX++) {
					moveFunction($('#' + pad(createX, cellLength)+ pad(createY, cellLength)));
				}
			}
		} 
		
		function initStartPos(x,y){
			//시작위치 지정
			$('#'+pad(x,cellLength)+pad(y,cellLength)).attr('class', 'position');
			array[x][y] = '3';
		}
		function initRock(){
			var i = 0;
			while(i < cellRockCnt){
				var x = pad(getRandomInt(0, cellCnt-1),cellLength);
				var y = pad(getRandomInt(0, cellCnt-1),cellLength);
				
				if(array[x][y] == 3 || array[x][y] == 2){
					if(isDebug){
						console.log(y+x+' started or already rock retry');
					}
					continue;
				}
				if(isDebug){
					console.log(y+x+' rock created');
				}
				
				$('#'+y+x).attr('class', 'rock'); // y가 세로축임 
				array[x][y] = '2';
				i++;
			}
			
		}
	</script>

	<table id="space">
	</table>

</BODY>
</HTML>