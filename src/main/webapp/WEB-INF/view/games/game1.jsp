<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<HEAD>
<TITLE>가메스</TITLE>
<style type="text/css">
table {
	width: 500px;
	height: 500px;
}

table tr td {
	text-align: center;
	border: solid 1px black;
}
</style>
</HEAD>
<BODY onload="onloadFunc()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script language="javascript">
	
		function onloadFunc(){
			$('#focus_here')[0].focus();
		}
	
		var getCnt = 0;
	
		function addGetCnt(){
			getCnt++;
			$('#getCnt')[0].value = getCnt;
		}
		
		
		$(document).keydown(function(e) {
			alert(e.keyCode);
			switch (e.keyCode) {
			case 97:
				if($('#num1')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num1')[0].style.background = 'gray';
				break;
			case 98:
				if($('#num2')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num2')[0].style.background = 'gray';
				break;
			case 99:
				if($('#num3')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num3')[0].style.background = 'gray';
				break;
			case 100:
				if($('#num4')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num4')[0].style.background = 'gray';
				break;
			case 101:
				if($('#num5')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num5')[0].style.background = 'gray';
				break;
			case 102:
				if($('#num6')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num6')[0].style.background = 'gray';
				break;
			case 103:
				if($('#num7')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num7')[0].style.background = 'gray';
				break;
			case 104:
				if($('#num8')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num8')[0].style.background = 'gray';
				break;
			case 105:
				if($('#num9')[0].style.background == 'black' ){
					addGetCnt();
				}
				$('#num9')[0].style.background = 'gray';
				break;

			}
		})
		$(document).keyup(function(e) {
			switch (e.keyCode) {
			case 97:
				$('#num1')[0].style.background = 'white';
				break;
			case 98:
				$('#num2')[0].style.background = 'white';
				break;
			case 99:
				$('#num3')[0].style.background = 'white';
				break;
			case 100:
				$('#num4')[0].style.background = 'white';
				break;
			case 101:
				$('#num5')[0].style.background = 'white';
				break;
			case 102:
				$('#num6')[0].style.background = 'white';
				break;
			case 103:
				$('#num7')[0].style.background = 'white';
				break;
			case 104:
				$('#num8')[0].style.background = 'white';
				break;
			case 105:
				$('#num9')[0].style.background = 'white';
				break;

			}
		})

		
		
		
		
		
		var createTime = 1500;
		var createCnt = 0;
		var timer = setInterval(timerFunc, createTime);
		var endCnt = 0;
		
		function timerFunc(){
			
			var i = getRandomInt(1,9);
			var cnt = 0; //스크립트 팅김방지
			while($('#num' + i)[0].style.background == 'black'){
				i = getRandomInt(1,9);
				cnt++;
				if(cnt >= 9){
					break;
				}
			}
			$('#num' + i)[0].style.background = 'black';
			createCnt++;
			
			if(createCnt == 5){
				createCnt = 0;
				createTime = createTime/2;
				clearTimeout(timer);
				timer = setInterval(timerFunc, createTime);
			}
			
			endCnt=0;
			for(var j=1;j<=9;j++){
				if($('#num' + j)[0].style.background == 'black'){
						endCnt++;
				}	
			}
			if(endCnt==9){
				clearTimeout(timer);
				alert('종료. 점수확인');
			}
			
		}
		
		function getRandomInt(min, max) {
			return Math.floor(Math.random() * (max - min + 1)) + min;
		}
	</script>

	<table>
		<tr>
			<td id="num7">numpad7</td>
			<td id="num8">numpad8</td>
			<td id="num9">numpad9</td>
		</tr>
		<tr>
			<td id="num4">numpad4</td>
			<td id="num5">numpad5</td>
			<td id="num6">numpad6</td>
		</tr>
		<tr>
			<td id="num1">numpad1</td>
			<td id="num2">numpad2</td>
			<td id="num3">numpad3</td>
		</tr>
		<tr>
			<td colspan="3"><input type="text" readonly="readonly" id="getCnt" value="0"></td>
		</tr>
	</table>
	<input type="tel" hidden="true" id="focus_here">


</BODY>
</HTML>