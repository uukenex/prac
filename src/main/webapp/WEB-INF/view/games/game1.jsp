<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<HEAD>
<TITLE>가메스</TITLE>
<style type="text/css">
	table {
		width:  500px;
		height: 500px;
		
	}
	
	table tr td{
		text-align: center;
		border: solid 1px black;		
	}
	
</style>
</HEAD>
<BODY>
<script src="http://code.jquery.com/jquery.js"></script>
<script language="javascript">
	function btnClick( arg0 ){
		console.log(arg0);
	}
	
	$(document).keydown(function(e){
		switch(e.keyCode){
			case 97: $('#num1')[0].style.background = 'black'; break;
			case 98: $('#num2')[0].style.background = 'black'; break;
			case 99: $('#num3')[0].style.background = 'black'; break;
			case 100: $('#num4')[0].style.background = 'black'; break;
			case 101: $('#num5')[0].style.background = 'black'; break;
			case 102: $('#num6')[0].style.background = 'black'; break;
			case 103: $('#num7')[0].style.background = 'black'; break;
			case 104: $('#num8')[0].style.background = 'black'; break;
			case 105: $('#num9')[0].style.background = 'black'; break;
		
		} 
	})
	$(document).keyup(function(e){
		switch(e.keyCode){
		case 97: $('#num1')[0].style.background = 'white'; break; 
		case 98: $('#num2')[0].style.background = 'white'; break;
		case 99: $('#num3')[0].style.background = 'white'; break;
		case 100: $('#num4')[0].style.background = 'white'; break;
		case 101: $('#num5')[0].style.background = 'white'; break;
		case 102: $('#num6')[0].style.background = 'white'; break;
		case 103: $('#num7')[0].style.background = 'white'; break;
		case 104: $('#num8')[0].style.background = 'white'; break;
		case 105: $('#num9')[0].style.background = 'white'; break;
		
		} 
	})
	
	
</script>
	
	<table>
		<tr>
			<td id="num7" onclick="btnClick(num7)">7</td>
			<td id="num8">8</td>
			<td id="num9">9</td>
		</tr>
		<tr>
			<td id="num4">4</td>
			<td id="num5">5</td>
			<td id="num6">6</td>
		</tr>
		<tr>
			<td id="num1">1</td>
			<td id="num2">2</td>
			<td id="num3">3</td>
		</tr>
	</table>	
	
	
	
</BODY>
</HTML>