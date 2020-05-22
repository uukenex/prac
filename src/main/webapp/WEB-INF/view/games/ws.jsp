<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>new titles..</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY>
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.1.5/sockjs.min.js"></script>
	<script language="javascript">
		
	//var webSocket = new WebSocket("ws://54.180.82.173:80/websocket2");
	//var sock = new SockJS("http://54.180.82.173:80/websocket2");
	var sock = isLocal? new SockJS("http://locahost/websocket2") : new SockJS("http://54.180.82.173/websocket2");
	
	// 서버로부터 메시지를 받았을 때
	sock.onopen = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	$("#txtarea")[0].value += "알파카 채팅서버에 접속했습니다!\n";
	};
	
	sock.onmessage = function(msg) {
		$("#txtarea")[0].value += "받은메시지 :"+msg.data+"\n";
		$("#txtarea")[0].scrollTop = $("#txtarea")[0].scrollHeight;
	};
	// 서버와 연결을 끊었을 때
	sock.onclose = function(evt) {
		$("#txtarea")[0].value += "채팅서버가 종료되었습니다!\n";
	};
	sock.onerror = function(message) {
		// 콘솔 텍스트에 메시지를 출력한다.
		$("#txtarea")[0].value += "에러가 발생하였습니다!\n";
	};
	
	/*
	// WebSocket 서버와 접속이 되면 호출되는 함수
	webSocket.onopen = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	$("#txtarea")[0].value += "알파카 채팅서버에 접속했습니다!\n";
	};
	// WebSocket 서버와 접속이 끊기면 호출되는 함수
	webSocket.onclose = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	$("#txtarea")[0].value += "채팅서버가 종료되었습니다!\n";
	};
	// WebSocket 서버와 통신 중에 에러가 발생하면 요청되는 함수
	webSocket.onerror = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	$("#txtarea")[0].value += "에러가 발생하였습니다!\n";
	};
	// WebSocket 서버로 부터 메시지가 오면 호출되는 함수
	webSocket.onmessage = function(message) {
	// 콘솔 텍스트에 메시지를 출력한다.
	$("#txtarea")[0].value += "받은메시지 :"+message.data+"\n";
	$("#txtarea")[0].scrollTop = $("#txtarea")[0].scrollHeight;
	};

    */
	
	
    function sendMsg(){
    	var msg = $('#msg').val();
    	sock.send(msg);
    	$("#txtarea")[0].value += "보낸메시지 :"+msg+"\n";
    	$('#msg').val('');
    	$("#txtarea")[0].scrollTop = $("#txtarea")[0].scrollHeight;
    }
	
	
		
	</script>

	<section id ="txtbx"><textarea id="txtarea" cols="80" rows="4" > </textarea> </section>
	<section id="input"> <input type="text" id="msg" placeholder="메시지 입력"></input><input type="button" id= "submit_msg" value="전송" onclick="sendMsg()"></section> </div>
	<section id="output"></section>
	
	
	
</BODY>
</HTML>