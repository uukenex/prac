<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>따뜻한 가메</TITLE>

<link rel="stylesheet" href="<%=request.getContextPath() %>/game_set/css/game3.css?v=<%=System.currentTimeMillis() %>" />
</HEAD>
<BODY onload="onloadFunc()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script language="javascript">
	<!-- 전역 변수 -->
		var cellCnt = ${cell};
		var cellLength = (cellCnt - 1).toString().length; //cellCnt의 자릿수 체크 , substr하기 위함
		var cellRockCnt = 2; //벽 갯수
		var moveSpeed = 1 * 1 / cellCnt * 1000;//timer 속도, cellCnt가 변경되어도 끝에서 끝까지 속도는 같음  

		var MAX_SIZE = 100;//queue size
		var isDebug = true;
		
		var direct = 'right';//방향 right left up down 의 약자 사용 
		var x = 0;
		var y=  0;
		var gameOverFlag = false;
		
		
		var movement;//timer 변수
		var moveSpeed = 0.8*1000 * (10 / cellCnt); //0.8초마다 한칸 이동 

		function Queue(){
	        this.dataStore = [];
	        this.push = push;
	        this.pop = pop;
	        this.toString = toString;
	        this.first = first;
	        this.last = last;
	        this.isEmpty = isEmpty;
	        this.indexOf = indexOf;
	        this.length = length;
	    }
		function push(element){
	        this.dataStore.push(element);
	    }
	    function pop(){
	        this.dataStore.shift();
	    }

	    function first(){
	        return this.dataStore[0];
	    }
	    function last(){
	        return this.dataStore[this.dataStore.length-1];
	    }

	    function toString(){
	        var str=""
	        for(var i = 0; i<this.dataStore.length; i++){
	            str += this.dataStore[i] + " ";
	        }
	        return str;
	    }
	    function isEmpty(){
	        if(this.dataStore.length == 0){
	            return true;
	        }else{
	            return false;
	        }
	    }
	    function indexOf(element){
	    	if(this.dataStore.indexOf(element) >= 0){
	    		return true;
	    	}else{
	    		return false;	
	    	}
	    }
	    function length(){ 
	    	return this.dataStore.length;
	    }

		var q = new Queue();
		
		//지렁이 게임 
		function onloadFunc() {
			initMakeCell(); //cell table생성
			initStartPos(x,y);//startPos생성
			initRock();//rock 생성
			initWindow();//윈도우 크기 등 css설정
			initKeyEvent();
			initMouseEvent();
		}

		function gameStart(){
			$('button#start').css('display','none');
			if(!gameOverFlag) initTimer();	
		}
		
		
		function initKeyEvent(){
			$(document).keydown(function(e) {
				//좌 37 상 38 우 39 하 40
				switch(e.keyCode){
					case 37:
						if(direct != 'right') direct ='left';
						//$('#'+x+''+y).parent()[0].innerHTML = '←';
						break;
					case 38:
						if(direct != 'down') direct ='up';
						//$('#'+x+''+y).parent()[0].innerHTML = '↑';
						break;
					case 39:
						if(direct != 'left') direct ='right';
						//$('#'+x+''+y).parent()[0].innerHTML = '→';
						break;
					case 40:
						if(direct != 'up') direct ='down';
						//$('#'+x+''+y).parent()[0].innerHTML = '↓';
						break;
				}
			});
			
		}
		function initMouseEvent(){
			$('#left').click(function(){
				if(direct != 'right') direct ='left';
			});
			$('#up').click(function(){
				if(direct != 'down') direct ='up';
			});
			$('#right').click(function(){
				if(direct != 'left') direct ='right';
			});
			$('#down').click(function(){
				if(direct != 'up') direct ='down';
			});
		}
		
		
		function addCellEvent(arg_x,arg_y){
			var id = arg_x+arg_y;
			
			$('#'+id).on("change", function() {
				if($('#'+id).val()=='9'){
					console.log('끝');
					clearTimeout(movement);
					$('#'+id).parent().addClass('over');
					$('.cell').fadeOut(1500);
				}
				
				if($('#'+id).val()=='2'){//tail
					
				}
				if($('#'+id).val()=='1'){//head
					q.push(id);
				}
				if($('#'+id).val()=='0'){
					q.pop();
				}
				
				if($('#'+id).val()=='s'){
					q.push(id);
				}
				
				if($('#'+id).val()=='e'){
					$('.destination').removeClass('destination');
					$('#'+id).parent().addClass('destination');
				}
				if($('#'+id).val()=='r'){
					$('#'+id).parent().addClass('rock');
				}
				
				$('input:hidden').each(function(){
					if($(this).val()=='r'){
						$(this).parent().addClass('rock');
					}
					
					if(!q.indexOf(this.id)){
						$(this).parent().removeClass('position');
						if($(this).val()!='e' && $(this).val()!='s' && $(this).val()!='r'){
							$(this).val('0');	
						}
						
					}
					if(q.indexOf(this.id)){
						$(this).parent().addClass('position');
						$(this).val('2');
					}
					
				});
				
			});

		}
		
		
		function initTimer(){
			var point = 0 ;
			var tailCount = 0;
			
			createEndPoint();
			clearTimeout(movement);//이동 timer 중복실행방지
			movement = setInterval(moveFunc, moveSpeed);//이동 timer 실행
			
			var tmp_x;
			var tmp_y;
			
			function moveFunc(){
				point++;
				//console.log(point);
				if( point > 1000 ){
					gameOver(4);
					console.log('1000점 도달 종료');
				}
				
				
				switch(direct){
					case 'left':
						move (x-1,y);
						break;
					case 'up':
						move (x,y-1);
						break;
					case 'right':
						move (Number(x)+1,y);
						break;
					case 'down':
						move (x,Number(y)+1);
						break;

				}
				
				if($('#'+pad(x,cellLength)+pad(y,cellLength)).val()=='e'){
					createEndPoint();
					
				}
				$('#'+pad(x,cellLength)+pad(y,cellLength)).val('1').trigger('change');
				
				
				if(point==10 || point==20 ||point==30 ||point==40){
					clearTimeout(movement);
					moveSpeed = moveSpeed*0.8;
					console.log('속도 증가.. 현재 moveSpeed : '+moveSpeed);
					if(!gameOverFlag){
						movement = setInterval(moveFunc, moveSpeed);	
					}
					
				}

			}
			
			
			var filter = "win16|win32|win64|mac";
			var mediaCode = "";
			if(navigator.platform){
				if(0 > filter.indexOf(navigator.platform.toLowerCase())){
					mediaCode ="MOBILE";
				}else{
					mediaCode ="PC";
				}
			}
			
			
			function gameOver(arg0){
				$('#'+x+''+y).val('9').trigger('change');
				gameOverFlag = true;
				setTimeout(function(){
					var cnt = Number(point)*Number(q.length()); 
					
					if(location.href.indexOf('localhost')>0){
						arg0 = 'testMode';
					}
					
					var promptVal =null;
					switch(arg0){
						case 1:
							promptVal = prompt('벽에 부딪힘..'+cnt+'점 \n이름을 입력해주세요.');
							break;
						case 2:
							promptVal = prompt('장애물에 부딪힘..'+cnt+'점\n이름을 입력해주세요.');
							break;
						case 3:
							promptVal = prompt('꼬리에 부딪힘..'+cnt+'점\n이름을 입력해주세요.');
							break;
						case 4:
							promptVal = prompt('max점수..'+cnt+'점\n이름을 입력해주세요.');
							break;
						default:
							console.log('testMode');
							return;
					}
					
					if(promptVal==null){
						return;
					}
					
					$.ajax({
						type : "post",
						url : "/game3/saveGame3Cnt",
						data : {
							userName : promptVal,						
							mediaCode: mediaCode,
							ip : ip(),
							cnt : cnt
						},
						success : function(res) {
							alert(cnt+"점수가 저장되었습니다");
							
						},
						error : function(request, status, error) {
							alert(request);
						}
					});
					
				},3000);
				
				
			}
			
			function move(tmp_x,tmp_y){
				//tmp_x,tmp_y 는 이동 할 좌표
				
				//벽에 부딪힘
				if(tmp_x < 0 || tmp_y < 0 || tmp_x >= cellCnt || tmp_y >= cellCnt ){
					gameOver(1);
					return;
				}
				//장애물에 부딪히는 경우
				if($('#'+pad(tmp_x,cellLength)+pad(tmp_y,cellLength)).val()=='r' ){
					gameOver(2);
					return;
				}
				//꼬리에 부딪히는 경우
				if($('#'+pad(tmp_x,cellLength)+pad(tmp_y,cellLength)).val()=='2' ){
					gameOver(3);
					
					return;
				}
				//먹이를 먹는 경우 //이전 지나온 좌표를 0 처리 해줌
				if($('#'+pad(tmp_x,cellLength)+pad(tmp_y,cellLength)).val()!='e'){
					$('#'+pad(x,cellLength)+pad(y,cellLength)).val('0').trigger('change');
				}
				
				x = tmp_x;
				y = tmp_y;
			}
			
			
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
				//'overflow' : 'hidden',
				'height' : '100%'
			});
			$('td').css('width', windowWidth / cellCnt);
			$('td').css('height', windowWidth / cellCnt);
			
			$('td').css('font-size','10px');
			
			$('section_direction').css('width', windowWidth - 20);
			
			$('button.direction').css('width', windowWidth / 5);
			$('button.direction').css('height', windowWidth / 5);
			
			$('button#start').css('width', windowWidth / 3);
			$('button#start').css('height', windowWidth / 3);
			
			var rect = $('table#space')[0].getBoundingClientRect();
			var startBtn_xPoint = (rect.right - rect.left) / 2 + (rect.x - windowWidth / 3 /2 );
			var startBtn_yPoint = (rect.bottom - rect.top) / 2 + (rect.y - windowWidth / 3 /2 );
			
			$('button#start').css('position', 'absolute');
			$('button#start').css('left',startBtn_xPoint);
			$('button#start').css('top', startBtn_yPoint);
			$('button#start').css('display', 'block');
			$('button#start').css('z-index', 1);
			
			
			
			document.oncontextmenu = function(e) {
				return false;
			}
			
			jQuery(document).keydown(function(e){
				if(e.target.nodeName != "INPUT" && e.target.nodeName != "TEXTAREA"){
					if(e.keyCode === 37 || e.keyCode === 38 || e.keyCode === 39 || e.keyCode === 40){
						return false;
					}
				}
			});
		}
		
		function initMakeCell(){
			var cellField = "";
				cellField += "<button id='start' onclick='gameStart();' style='display:none'>start</button>";
			
				
			var cellArr = Array(cellCnt).fill(null).map(() => Array());
			
			var createY = 0;
			var createX = 0;
			
			for (createY = 0; createY < cellCnt; createY++) {
				cellField += "<tr>";
				for (createX = 0; createX < cellCnt; createX++) {
					cellField += "<td class='cell'>";
					cellField += "<input type='hidden' id=" + pad(createX, cellLength) + pad(createY, cellLength) + " value='0' />";
					cellField += "</td>";
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
			$('#'+pad(x,cellLength)+pad(y,cellLength)).val('s').trigger('change');
		}
		function createEndPoint(){
			var i = 0;
			while(i < 1){
				var x = pad(getRandomInt(0, cellCnt-1),cellLength);
				var y = pad(getRandomInt(0, cellCnt-1),cellLength);
				var id = x+''+y; 
				if( q.indexOf(id) || $('#'+id).val() == 'r' || $('#'+id).val() == '2'|| $('#'+id).val() == '1'){
					continue;
				}
				
				$('#'+pad(x,cellLength)+pad(y,cellLength)).val('e').trigger('change');
				i++;
			}
		}
		
		function initRock(){
			var i = 0;
			while(i < cellRockCnt){
				var x = pad(getRandomInt(0, cellCnt-1),cellLength);
				var y = pad(getRandomInt(0, cellCnt-1),cellLength);
				
				if($('#'+pad(x,cellLength)+pad(y,cellLength)).val() == 's' || $('#'+pad(x,cellLength)+pad(y,cellLength)).val() == 'r' ||
					$('#'+pad(x,cellLength)+pad(y,cellLength)).val() == 'e' || $('#'+pad(x,cellLength)+pad(y,cellLength)).val() == '2'){
					continue;
				}
				
				$('#'+pad(x,cellLength)+pad(y,cellLength)).val('r');
				i++;
			}
			
		}
		
		function rank_info(){ //직급직무 접기/펼치기
		    $('#rank_table').toggleClass("hide");
		    if ($('#rank_btn').text() == "펼치기"){
		        $('#rank_btn').text("닫기");
		    }
		    else {
		        $('#rank_btn').text("펼치기");
		    }    
		}
		
	</script>

	<section id="section_space">
	<table id="space">
	</table>
	</section>
	<div id="score">
		
	</div>
	
	<!-- 
	<div id="stop">
		<input type="button" value="stop" id="btn_stop">
	</div>
	<div id="restart">
		<input type="button" value="restart" id="btn_restart">
	</div> 
	-->
	<section id="section_direction">
		<button class='direction' id="up">↑</button><br>
		<button class='direction' id="left">←</button>
		<button class='direction' id="down">↓</button>
		<button class='direction' id="right">→</button><br>
	</section>
	
	<section>
		</br>
		</br>
	</section>
	
	<section id='rank'>
		<div onclick="rank_info()">
		<strong>랭크 정보</strong><font id="rank_btn">펼치기</font>
		</div>
		<div id="rank_table" class="hide">
			<table>
			<tr>
				<td><strong>인입</strong></td> 
				<td><strong>점수</strong></td>
				<td><strong>ID</strong></td>
			</tr>
			<c:forEach var="ranks" items="${rank}">
			<tr>
				<td>${ranks.MEDIA_CODE}</td> 
				<td>${ranks.CNT}	   </td>
				<td>${ranks.USER_ID}  </td>
			</tr>
			</c:forEach>
			</table>
		</div>
		 
	</section>
	
</BODY>
</HTML>