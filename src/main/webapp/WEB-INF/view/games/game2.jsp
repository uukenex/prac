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

.position {
	background: black;
}
</style>

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

		
		var moveStartPoint;
		var moveEndPoint;
		var movement;
		var moveDistance;
		var moveXPos;
		var moveYPos;
		
		function moveEvent(arg0) {
			moveStartPoint = findCurrentCellId();
			moveEndPoint = arg0[0].id;
			moveDistance = moveEndPoint - moveStartPoint;
			
			moveXPos = moveEndPoint.substr(0,1) - moveStartPoint.substr(0,1);
			moveYPos = moveEndPoint.substr(1,1) - moveStartPoint.substr(1,1);
			
			//console.log('From : ' + moveStartPoint + ', To : ' + moveEndPoint+', distanceX : '+moveXPos+', distanceY : '+moveYPos);
			
			clearTimeout(movement);
			movement = setInterval(moveFunc, 1000);
		}

		var findCurrentCellId = function() {
			return $('.position')[0].id;
		}
		
		function moveFunc() {
			
			if((String)(new Date().getTime()).substr(-1)%2==1){
				if(moveXPos != '0'){
					moveX();
					clearTimeout(movement);
					movement = setInterval(moveFunc, 1000);
				}else if(moveYPos != '0'){
					moveY();
					clearTimeout(movement);
					movement = setInterval(moveFunc, 1000);
				}else{
					console.log('이동종료');
					clearTimeout(movement);
				}
				
			}else{
				if(moveYPos != '0'){
					moveY();
					clearTimeout(movement);
					movement = setInterval(moveFunc, 1000);
				}else if(moveXPos != '0'){
					moveX();
					clearTimeout(movement);
					movement = setInterval(moveFunc, 1000);
				}else{
					console.log('이동종료');
					clearTimeout(movement);
				}
			}
			
			
			
		}
		
		function moveX(){
			var orgId = findCurrentCellId();
			var targetXpoint;
			var targetId;
			if(moveXPos >0){
				targetXpoint = Number(orgId.substr(0,1))+1;
			}else{
				targetXpoint = Number(orgId.substr(0,1))-1;
			}
			targetId = targetXpoint + orgId.substr(1,1);
			$('#'+orgId).removeAttr('class');
			$('#'+targetId).attr('class', 'position');
			moveXPos = moveEndPoint.substr(0,1) - targetId.substr(0,1);
		}
		function moveY(){
			var orgId = findCurrentCellId();
			var targetYpoint;
			var targetId;
			if(moveYPos >0){
				targetYpoint = Number(orgId.substr(1,1))+1;
			}else{
				targetYpoint = Number(orgId.substr(1,1))-1;
			}
			targetId = orgId.substr(0,1) + targetYpoint;
			$('#'+orgId).removeAttr('class');
			$('#'+targetId).attr('class', 'position');
			moveYPos = moveEndPoint.substr(1,1) - targetId.substr(1,1);
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