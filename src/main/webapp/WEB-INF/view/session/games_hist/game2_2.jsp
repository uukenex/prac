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
		var cellCnt = 3;
		var cellLength = (cellCnt - 1).toString().length; //cellCnt의 자릿수 체크 , substr하기 위함
		var cellRockCnt = 2; //벽 갯수
		var moveSpeed = 1 * 1 / cellCnt * 1000;//timer 속도, cellCnt가 변경되어도 끝에서 끝까지 속도는 같음  

		var array;
		var MAX_SIZE = 100;//queue size
		var isDebug = true;
		
		function onloadFunc() {
			array = initMakeCell(); //cell table생성
			initStartPos(0,0);//startPos생성
			initRock();//rock 생성
			initWindow();//윈도우 크기 등 css설정
			initAddCellEvent();//cell event추가 -> moveFunction 구현
			//initEndPos(2,2);//imsi EndPos생성
			//anotherProcess();
		}

		function moveFunction(arg0) {
			arg0.mousedown(function(e) {
				getClickArray(arg0);
			});
		}

		function getClickArray(arg0){
			var x = arg0[0].id.substr(0, cellLength);
			var y = arg0[0].id.substr(cellLength, cellLength);
			if(isDebug){
				//console.log(y+x+' clicked : array val='+array[y][x]);
			}
			
			if(array[y][x] == 'r'){
				//console.log('rock');
				return;
			}
			
			$('.route').removeClass('route');
			$('.destination').removeClass('destination');
			array[y][x] = 'e';
			$('#'+pad(x,cellLength)+pad(y,cellLength)).attr('class', 'destination');
			
			anotherProcess(y,x);
			
		}
		
		function anotherProcess(arg0,arg1){
			function Stack() {    
				//스택의 요소가 저장되는 배열    
				var dataStore = new Pos;   
				this.dataStore = [];    
				//스택의 위치    
				this.top = -1;    
				//함수정의    
				this.push   = push;    
				this.pop    = pop;
			}
			function Pos(x,y) {
			 	this.x = x;    
			 	this.y = y;
			}
			function Init() {   
			 	this.top = -1;
			}
			
			//가득 찼을때
			function Is_full() {
			 	return(this.top == MAX_SIZE-1);
			}
			//비었을 때
			function Is_empty(){
			 	return(this.top == -1);
			}
			//스택에 요소를 추가
			function push(element) {
				if(Is_full()) { 
				 	console.log("Stack is full"); 
				} else{
				 	this.top = this.top +1;
				 	this.dataStore[this.top] = element;
				}
			}
			//스택 최상층의 요소를 반환한다.
			function pop() { 
			 //Stack underflow   
			 	if(this.top<=-1) {  
			 		console.log("Stack underflow!!!");
			 		return; 
			 	} else {     
				 	var popped = this.dataStore[this.top];
				 	//top을 1 감소시킨다.     
				 	this.top = this.top -1;     
			 		return popped; 
			 	}
			}
			
			//동서남북 이동 가능한가 확인 Movable함수
			function Movable(x,y) {
				if(x<0||y<0||x>=cellCnt||y>=cellCnt) {
					return;
				}
				if (array[x][y] != 'r' && array[x][y] != '.') {
					var next = new Pos();    
					next.x = x;   
					next.y = y;  
					stackObj.push(next); 
				}
			}
			//스택 객체 생성
			var stackObj = new Stack();
			var here = new Pos();
			//시작점 탐색
			for (var i = 0; i < array.length; i++) {
				for (var j = 0; j < array[i].length; j++) {
					if (array[i][j] == 's') {  
						here.x = i;      
						here.y = j;        
					}  
				}
			}
			
			$('#' + here.y+''+ here.x).addClass('start');
			console.log("시작점:" + "(" + here.x + "," + here.y + ")");
			 
			//시작점에서 출구까지 반복문
			while(array[here.x][here.y] != 'e') {
				var x = here.x;   
				var y = here.y;   
				array[x][y]='.'; 
				
				Movable(x + 1, y);  
				Movable(x, y + 1);
				Movable(x - 1, y);  
				Movable(x, y - 1);
				
				if(Is_empty()) {     
					console.log("failed");
					return;   
				} else { 
					here = stackObj.pop();
					console.log("(" + here.x + "," + here.y + ")");
					$('#'+here.y+here.x).addClass('route');
				}
			}
			console.log("도착점:"+ "(" + here.x + "," + here.y + ")");
			console.log("탈출 성공!!!!!!!!");
			
			array[here.x][here.y] = 's';
			clearCell();
			$('.start').removeClass('start');
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

</BODY>
</HTML>