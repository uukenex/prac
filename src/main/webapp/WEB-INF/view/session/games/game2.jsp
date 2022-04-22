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
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script language="javascript">
	<!-- 전역 변수 -->
		var cellCnt = ${cell};
		var cellLength = (cellCnt - 1).toString().length; //cellCnt의 자릿수 체크 , substr하기 위함
		var cellRockCnt = 2; //벽 갯수
		
		var moveSpeed = 1 * 1 / cellCnt * 1000;//timer 속도, cellCnt가 변경되어도 끝에서 끝까지 속도는 같음  

		function onloadFunc() {
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
			document.oncontextmenu = function(e) {
				return false;
			}
			var cellField = "";

			var xPoint = 0; //for문 변수로 사용되지만, 자릿수에 맞춰 늘어나야함 
			var yPoint = 0; //for문 변수로 사용되지만, 자릿수에 맞춰 늘어나야함 

			for (var yPoint = 0; yPoint < cellCnt; yPoint++) {
				cellField += "<tr>";
				for (var xPoint = 0; xPoint < cellCnt; xPoint++) {
					cellField += "<td id=" + pad(xPoint, cellLength) + pad(yPoint, cellLength) + "></td>";
				}
				cellField += "</tr>";
			}

			$('#space')[0].innerHTML = cellField;
			$('td').css('width', windowWidth / cellCnt);
			$('td').css('height', windowWidth / cellCnt);

			//시작위치 지정
			$('#'+pad('0',cellLength)+pad('0',cellLength)).attr('class', 'position');
			
			//rock생성 
			for (var i = 0; i < cellRockCnt ; i++){
				$('#'+pad(getRandomInt(0, cellCnt),cellLength)+pad(getRandomInt(0, cellCnt),cellLength)).attr('class', 'rock');
			}
			
			//cell생성 
			for (var yPoint = 0; yPoint < cellCnt; yPoint++) {
				for (var xPoint = 0; xPoint < cellCnt; xPoint++) {
					addEvent($('#' + pad(xPoint, cellLength)+ pad(yPoint, cellLength)));
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
			if ($('.preRoute')[0] == null) {
				return findCurrentCellId();
			} else {
				return $('.preRoute')[0].id;
			}
		}

		var moveStartPoint//누른 시점 현재 셀 ID;
		var moveEndPoint//누른 시점 종료 셀 ID;
		var movement;//이동하는 timer 변수

		var moveOrgXpos;//x가야할 거리 원본
		var moveOrgYpos;//y가야할 거리 원본
		var moveXpos;//변경가능한 x가야할 거리
		var moveYpos;//변경가능한 y가야할 거리
		var moveTotalSize;// x+y 이동거리
		var moveMaxSize;// x+y 이동거리
		var moveCurrentSize; //현재까지 이동거리 저장할 변수 
		var moveIdList;// 게산된 route 저장

		var moveRockAvoidCnt;//rock 피하는 카운트 수
		var moveRockAvoidAddRoute;//rock을 피하기위한 route 추가
		
		function moveEvent(arg0) {
			moveStartPoint = findCurrentCellId();
			moveEndPoint = arg0[0].id;

			moveOrgXpos = moveEndPoint.substr(0, cellLength)
					- moveStartPoint.substr(0, cellLength);
			moveOrgYpos = moveEndPoint.substr(cellLength, cellLength)
					- moveStartPoint.substr(cellLength, cellLength);

			moveXpos = moveOrgXpos;
			moveYpos = moveOrgYpos;
			moveTotalSize = Math.abs(moveXpos) + Math.abs(moveYpos);
			moveCurrentSize = 0;
			moveRockAvoidCnt = 0;
			moveRockAvoidAddRoute = 0;

			if (moveTotalSize == 0) {
				moveClear();
				return;
			}

			moveFind();//route 찾기 밑 초기화 기능
			moveMaxSize = moveIdList.length;
			
			if (moveRockAvoidCnt >= 30) {
				return;
			}

			clearTimeout(movement);//이동 timer 중복실행방지
			movement = setInterval(moveFunc, moveSpeed);//이동 timer 실행
		}

		function moveFind() {
			moveClear();//초기화

			$('#' + moveStartPoint).addClass('route');
			$('#' + moveStartPoint).addClass('start');
			$('#' + moveEndPoint).addClass('destination');

			preRouteAlgorithm(moveTotalSize);

			$('.preRoute').removeClass('preRoute');
			if (moveRockAvoidCnt < 30) {//벽 피하기는 30회까지만 재도전
				moveFindRock();
			}else if (moveRockAvoidCnt == 30) {//벽 피하기는 30회까지만 재도전
				moveFindRock();
			}
		}

		function moveFindRock() {
			var rtnValue = true;//정상 루트 찾기 

			for (var i = 0; i < moveTotalSize; i++) {
				if ($('#' + moveIdList[i]).hasClass('rock')) {
					
					rtnValue = false;//비정상 루트	
					moveRockAvoidCnt++;
				}
			}

			if (!rtnValue) {
				//route 재계산을 위한 거리 재계산
				moveXpos = moveOrgXpos;
				moveYpos = moveOrgYpos;

				moveFind();
			}

		}
		

		function moveFunc() {
			
			if(moveMaxSize == moveCurrentSize ){
				moveClear();
				return;
			}
			
			$('.position').removeClass('position');
			$('#' + moveIdList[moveCurrentSize]).addClass('position');

			moveCurrentSize++;
			if (moveCurrentSize == moveTotalSize) {
				moveClear();
				return;
			}

		}

		function preRouteAlgorithm(size) {
			//지정된 횟수 내 모든 경로를 감지하도록 구현
			for (var i = 0; i < size; i++) {
				
				
				var orgId = findRouteCellId();
				var targetXpoint;
				var targetYpoint;
				var targetId;

				if (getRandomInt(0, 1) % 2 == 1) {
					if (moveXpos != '0') {
						if (moveXpos > 0) {
							targetXpoint = Number(orgId.substr(0, cellLength)) + 1;
						} else {
							targetXpoint = Number(orgId.substr(0, cellLength)) - 1;
						}
						targetId = pad(targetXpoint,cellLength)
								+ orgId.substr(cellLength, cellLength);
						moveXpos = moveEndPoint.substr(0, cellLength)
								- targetId.substr(0, cellLength);
					} else {
						if (moveYpos > 0) {
							targetYpoint = Number(orgId.substr(cellLength,
									cellLength)) + 1;
						} else {
							targetYpoint = Number(orgId.substr(cellLength,
									cellLength)) - 1;
						}
						targetId = orgId.substr(0, cellLength) + pad(targetYpoint,cellLength);;
						moveYpos = moveEndPoint.substr(cellLength, cellLength)
								- targetId.substr(cellLength, cellLength);
					}

				} else {
					if (moveYpos != '0') {
						if (moveYpos > 0) {
							targetYpoint = Number(orgId.substr(cellLength,
									cellLength)) + 1;
						} else {
							targetYpoint = Number(orgId.substr(cellLength,
									cellLength)) - 1;
						}
						targetId = orgId.substr(0, cellLength) + pad(targetYpoint,cellLength);
						moveYpos = moveEndPoint.substr(cellLength, cellLength)
								- targetId.substr(cellLength, cellLength);
					} else {
						if (moveXpos > 0) {
							targetXpoint = Number(orgId.substr(0, cellLength)) + 1;
						} else {
							targetXpoint = Number(orgId.substr(0, cellLength)) - 1;
						}
						targetId = pad(targetXpoint,cellLength)
								+ orgId.substr(cellLength, cellLength);
						moveXpos = moveEndPoint.substr(0, cellLength)
								- targetId.substr(0, cellLength);
					}

				}
				if(moveRockAvoidCnt == 20){
					if($('#' + targetId).hasClass('rock')){
						$('.destination').removeClass('destination');
						$('#' + moveIdList[i-1]).addClass('destination');
						break;
					}
				}
				
				$('#' + orgId).removeClass('preRoute');
				$('#' + targetId).addClass('preRoute');
				$('#' + targetId).addClass('route');
				moveIdList.push(targetId);
				
			}
		}
		
		

		function moveClear() {
			clearTimeout(movement);
			moveIdList = [];
			$('.start').removeClass('start');
			$('.destination').removeClass('destination');
			$('.route').removeClass('route');
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
	</script>

	<table id="space">
	</table>

</BODY>
</HTML>