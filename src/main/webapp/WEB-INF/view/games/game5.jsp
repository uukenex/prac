<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>ROUND WORM GAME</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
		
	var webSocket = new WebSocket("ws://localhost:80/websocket");

	var output;
	
	var sp_count= 0;
	
	function init()
    {
		key_event();
        output = document.getElementById("output");
    }
 
 
	// 콘솔 텍스트 에리어 오브젝트
	var messageTextArea = $("#output");
	// WebSocket 서버와 접속이 되면 호출되는 함수
	webSocket.onopen = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	messageTextArea.value += "Server connect...\n";
	};
	// WebSocket 서버와 접속이 끊기면 호출되는 함수
	webSocket.onclose = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	messageTextArea.value += "Server Disconnect...\n";
	};
	// WebSocket 서버와 통신 중에 에러가 발생하면 요청되는 함수
	webSocket.onerror = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	messageTextArea.value += "error...\n";
	};
	// WebSocket 서버로 부터 메시지가 오면 호출되는 함수
	webSocket.onmessage = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	messageTextArea.value += "Recieve From Server => "+message.data+"\n";
	};

    
    function sendMsg(){
    	var msg = $('#msg').val();
    	webSocket.send(msg);
    }
	
	
	$(function(){
		var keypress = {}, // 어떤 키가 눌려 있는지 저장
		charx = 0, chary = 0, speed = 1, $char = $('#char');
		
		setInterval(function(){ // 주기적으로 검사
			
			if(charx < 0) charx=0;
			if(chary < 0) chary=0;
			if(charx > 400-37-18) charx=400-37-18;
			if(chary > 400-63-31) chary=400-63-31;
			
			speed = keypress['65'] ? 3 : 1
			if(keypress['37']) charx -= speed; // left
			if(keypress['38']) chary -= speed; // up
			if(keypress['39']) charx += speed; // right
			if(keypress['40']) chary += speed; // down
			
			$char.css({top: chary, left: charx});
		}, 10); // 매 0.01 초마다 실행
	 
		$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
			keypress[e.which.toString()] = true;
		});
		$(document).keyup(function(e){ // 눌렸던 키를 해제
			keypress[e.which.toString()] = false;
		});
	});
	
	
	function key_event(){
		$(document).keydown(function(e) {
			//좌 37 상 38 우 39 하 40
			switch(e.keyCode){
				case 32:
					create_enemy(sp_count,2,400-20-10,getRandomInt(0, 400-20-10));
					sp_count++;
					break;
			}
		});
	}
	
	
	function create_enemy(sp_count,e_speed,x,y){
		
		$('#enemy_field')[0].innerHTML += '<div class="enemy" id="enemy'+sp_count+'"></div>'; 
		
		var move_timer = setInterval(function(){
			enemy_move(sp_count);
		}, 10); 
		
		
		function enemy_move(sp_count){
			x -= e_speed; 
			$('#enemy'+sp_count).css({top: y, left: x}); 
			if(x < -30 ){
				clearTimeout(move_timer);
			}
		}
		
	}
	
	
	
		
	</script>

	<section id="space">
		<div id="char"><img id="charimg" src="<%=request.getContextPath()%>/game_set/img/lion.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="enemy_field"></div>
	</section>

	<section id="desc">
		방향키: 조작 / A: 달리기 / S: 베기  / D: 미사일 
	</section>
	<section id="input"> <input type="text" id="msg" placeholder="메시지 입력"></input><input type="button" id= "submit_msg" value="전송" onclick="sendMsg()"></section> </div>
	<section id="output"></section>
</BODY>
</HTML>