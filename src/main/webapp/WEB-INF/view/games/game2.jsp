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
.rock{
	background: gray;
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
			if (windowWidth > 720) {
				windowWidth = 720;
			}
			$('table').css('width', windowWidth-20);
			$('table').css('height', windowWidth);
			$('html, body').css({
				'overflow' : 'hidden',
				'height' : '100%'
			});
			document.oncontextmenu = function(e) {
				return false;
			}

			var cellCnt = 10;
			var cellField ="";
			var cellRockCnt = 2;
			
			for (var j = 0; j < cellCnt; j++) {
				cellField += "<tr>";
		        for (var i = 0; i < cellCnt; i++) {
		        	cellField += "<td id="+i+j+"></td>";
		        }
		        cellField += "</tr>";
		      }
			
			$('#space')[0].innerHTML = cellField;
			$('td').css('width', windowWidth/cellCnt);
			$('td').css('height', windowWidth/cellCnt);
			
			//시작위치 지정
			$('#00').attr('class', 'position');
			$('#33').attr('class', 'rock');
			
			
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
		var moveSpeed = 0.15*1000;//timer 속도
		
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
			
			moveSizeCheck();
			
			moveCurrentSize = 0;
			
			if(moveTotalSize ==0){
				moveClear();
				return;
			}
			
			moveFind();//route 찾기 밑 초기화 기능
			
			clearTimeout(movement);//이동 timer 중복실행방지
			movement = setInterval(moveFunc, moveSpeed);//이동 timer 실행
		}

		
		function moveSizeCheck(){
			return;
		}
		
		function moveFind(){
			moveClear();//초기화
			
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
			moveFindRock();
		}
		function moveFindRock(){
			var rtnValue = true;//정상 루트 찾기 
						
			for(var i = 0 ; i < moveTotalSize ; i++){
				if($('#'+moveIdList[i]).hasClass('rock')){
					rtnValue = false;//비정상 루트	
				}
			}
			
			
			if(!rtnValue){
				//route 재계산을 위한 거리 재계산
				moveXpos = moveEndPoint.substr(0,1) - moveStartPoint.substr(0,1);
				moveYpos = moveEndPoint.substr(1,1) - moveStartPoint.substr(1,1);
				moveFind();	
			}
			
		}
		
		function moveFunc() {
			$('.position').removeClass('position');
			$('#'+moveIdList[moveCurrentSize]).addClass('position');
			
			moveCurrentSize++;
			if(moveCurrentSize == moveTotalSize){
				moveClear();
				return;
			}
			
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
		function moveClear(){
			clearTimeout(movement);
			moveIdList = [];
			$('.start').removeClass('start');
			$('.destination').removeClass('destination');
			$('.route').removeClass('route');
		}
		
		
		
		function getRandomInt(min, max) {
			return Math.floor(Math.random() * (max - min + 1)) + min;
		}
</script>

	<table id="space">
	</table>

</BODY>
</HTML>