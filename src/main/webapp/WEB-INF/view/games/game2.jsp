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
	padding-right: 15;
}

table tr td {
	text-align: center;
	border: solid 1px black;
}

.route{
	background: yellow;
}
.destination{
	background: blue;
}
.start {
	background: red;
}
.position {
	background: black;
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
		function onloadFunc() {
			var windowWidth = $(window).width();
			if (windowWidth > 640) {
				windowWidth = 640;
			}
			$('table').css('width', windowWidth);
			$('table').css('height', windowWidth);
			$('html, body').css({
				'overflow' : 'hidden',
				'height' : '100%'
			});
			document.oncontextmenu = function(e) {
				return false;
			}

			var cellCnt = 5;
			var cellField ="";
			
			for (var j = 0; j < cellCnt; j++) {
				cellField += "<tr>";
		        for (var i = 0; i < cellCnt; i++) {
		        	cellField += "<td id="+i+j+">"+i+j+"</td>";
		        }
		        cellField += "</tr>";
		      }
			
			$('#space')[0].innerHTML = cellField;
			
			//시작위치 지정
			$('#00').attr('class', 'position');
			
			for(var i=0 ; i < cellCnt ; i++){
				for(var j=0 ; j < cellCnt ; j++){
					addEvent($('#'+i+j));
				}	
			}
		}

		function addEvent(arg0) {
			arg0.mousedown(function(e) {
				moveEvent(arg0);
			});
		}
		var findCurrentCellId = function() {
			return $('.position')[0].id;
		}
		var findRouteCellId = function() {
			if($('.preRoute')[0]==null){
				return findCurrentCellId();
			}else{
				return $('.preRoute')[0].id;	
			}
		}
		
		
		var moveStartPoint//누른 시점 현재 셀 ID;
		var moveEndPoint//누른 시점 종료 셀 ID;
		var movement;//이동하는 timer 변수
		var moveSpeed = 1*1000;//timer 속도
		
		var moveXpos;//x좌표 이동거리
		var moveYpos;//y좌표 이동거리
		var moveTotalSize;// x+y 이동거리
		var moveCurrentSize; //현재까지 이동거리 저장할 변수 
		var moveIdList;// 게산된 route 저장
		
		
		function moveEvent(arg0) {
			moveStartPoint = findCurrentCellId();
			moveEndPoint = arg0[0].id;
			
			moveXpos = moveEndPoint.substr(0,1) - moveStartPoint.substr(0,1);
			moveYpos = moveEndPoint.substr(1,1) - moveStartPoint.substr(1,1);
			moveTotalSize = Math.abs(moveXpos)+Math.abs(moveYpos);
			moveCurrentSize = 0;
			moveFind();//route 찾기 밑 초기화 기능

			clearTimeout(movement);//이동 timer 중복실행방지
			movement = setInterval(moveFunc, moveSpeed);//이동 timer 실행
		}

		
		function moveFind(){
			moveIdList = [];
			$('.start').removeClass('start');
			$('.destination').removeClass('destination');
			$('.route').removeClass('route');
			
			$('#'+moveStartPoint).addClass('route');
			$('#'+moveStartPoint).addClass('start');
			$('#'+moveEndPoint).addClass('destination');
			
			for(var i = 0 ; i < moveTotalSize ; i++){
				if(getRandomInt(0,1)%2 == 1){
					if(moveXpos != '0'){
						moveX();
					}else if(moveYpos != '0'){
						moveY();
					}
				}else{
					if(moveYpos != '0'){
						moveY();
					}else if(moveXpos != '0'){
						moveX();
					}
				}
			}
			$('.preRoute').removeClass('preRoute');
			
			//초기셀까지 선택
			/* 
			for(var i=0;i<moveTotalSize+1;i++){
				moveIdList.push($('.route')[i].id);	
			} 
			*/
			
		}
		
		function moveFunc() {
			var orgId = findCurrentCellId();
			
			
			console.log(moveCurrentSize +' / '+moveTotalSize);
			
			$('.position').removeClass('position');
			$('#'+moveIdList[moveCurrentSize]).addClass('position');
			
			moveCurrentSize++;
			if(moveCurrentSize == moveTotalSize){
				clearTimeout(movement);//지정된 카운트 이동시 타이머 종료
				return;
			}
			
			/* 
			if($("#"+(Number(orgId.substr(0,1))+1)+orgId.substr(1,1)).hasClass('route')){
				console.log('x+1');
			}else if($("#"+(Number(orgId.substr(0,1))+1)+orgId.substr(1,1)).hasClass('route')){
				console.log('x-1');
			}else if($("#"+orgId.substr(0,1)+(Number(orgId.substr(1,1))+1)).hasClass('route')){
				console.log('y+1');
			}else if($("#"+orgId.substr(0,1)+(Number(orgId.substr(1,1))-1)).hasClass('route')){
				console.log('y-1');
			} */
			/* 
			if($('#21').hasClass('route'))
			
				if(moveXpos != '0'){
					moveX();
					clearTimeout(movement);
					movement = setInterval(moveFunc, moveSpeed);
				}else if(moveYpos != '0'){
					moveY();
					clearTimeout(movement);
					movement = setInterval(moveFunc, moveSpeed);
				}else{
					console.log('이동종료');
					clearTimeout(movement);
				}
				 */
			
		}
		
		function moveX(){
			var orgId = findRouteCellId();
			var targetXpoint;
			var targetId;
			if(moveXpos >0){
				targetXpoint = Number(orgId.substr(0,1))+1;
			}else{
				targetXpoint = Number(orgId.substr(0,1))-1;
			}
			targetId = targetXpoint + orgId.substr(1,1);
			$('#'+orgId).removeClass('preRoute');
			$('#'+targetId).addClass('preRoute');
			$('#'+targetId).addClass('route');
			moveIdList.push(targetId);	
			moveXpos = moveEndPoint.substr(0,1) - targetId.substr(0,1);
		}
		function moveY(){
			var orgId = findRouteCellId();
			var targetYpoint;
			var targetId;
			if(moveYpos >0){
				targetYpoint = Number(orgId.substr(1,1))+1;
			}else{
				targetYpoint = Number(orgId.substr(1,1))-1;
			}
			targetId = orgId.substr(0,1) + targetYpoint;
			$('#'+orgId).removeClass('preRoute');
			$('#'+targetId).addClass('preRoute');
			$('#'+targetId).addClass('route');
			moveIdList.push(targetId);
			moveYpos = moveEndPoint.substr(1,1) - targetId.substr(1,1);
		}
		
		
		
		function getRandomInt(min, max) {
			return Math.floor(Math.random() * (max - min + 1)) + min;
		}
	</script>

	<table id="space">
		<!-- <tr>
			<td id="00">00</td>
			<td id="10">10</td>
			<td id="20">20</td>
		</tr>
		<tr>
			<td id="01">01</td>
			<td id="11">11</td>
			<td id="21">21</td>
		</tr>
		<tr>
			<td id="02">02</td>
			<td id="12">12</td>
			<td id="22">22</td>
		</tr> -->
	</table>
	<!-- <div id="char">0</div> -->

</BODY>
</HTML>