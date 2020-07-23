<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>chat-chat</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<c:if test="${(userId ne '') and !(empty userId)}">
		<input type="hidden" value='${userNick}' id='chat_id' />
	</c:if>

	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/websocket_module2.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	function init()
    {
		var db_game_no = 6;
		
		var v_chat_timer;//clear timer variable - chat floating box
		
		var flag_enter = false; //엔터 눌렸는지 여부 검사
		var flag_chat_float = false;  // 채팅창 떠있는지 검사 
		
		var main_interval ;
		var sub_interval;
		
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 2, 
    		$charimg = $('#char_${userNick}');
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			
    			if($charimg.length==0){
    				$charimg = $('#char_${userNick}');	
    			}
    		
    			
    			if(charx < 0) charx=0;
    			if(chary < 0) chary=0;
    			if(charx > 400-37-18) charx=400-37-18;
    			if(chary > 400-63-31) chary=400-63-31;
    			
    			if(keypress['37']) {
    				charx -= speed; // left
    				send('#chat','', charx , chary );
    			}
    			if(keypress['38']) {
    				chary -= speed; // up
    				send('#chat','', charx , chary );
    			}
    			if(keypress['39']) {
    				charx += speed; // right
    				send('#chat','', charx , chary );
    			}
    			if(keypress['40']) {
    				chary += speed; // down
    				send('#chat','', charx , chary );
    			}
    			
    			
    			
    			$charimg.css({top: chary, left: charx});
    			
    			
    		}, 10); // 매 0.01 초마다 실행
    	 
    		sub_interval = setInterval(function(){
        		//채팅창 
        		
        		/* if(flag_chat_float){
        			var $chat_float = $('.chat_float');
    				var chat_float_area = $('#char_${userNick}')[0].getBoundingClientRect();
    				$chat_float.css({top: chat_float_area.top-30, left: chat_float_area.left-37*2});
    			}else{
    				$('.chat_float').remove();
    			} */
        		
        	}, 10);	
    		
    		
    		//window Event
        	$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
        		var key = e.which.toString();
        	
        		if(key=='13'){
        			f_chatter_box_create(400/2-100, 350);//'enter' chatter box
        		} else if(flag_enter){
        			$('#chat')[0].text += key;
        		} else{
        			keypress[key] = true;
        		}
        	});
        	$(document).keyup(function(e){ // 눌렸던 키를 해제
        		var key = e.which.toString();
        		keypress[key] = false;	
        	});
    		
    		
    	});
    	
    	function f_chatter_box_create(x,y){
    		var msg;
    		var chat_float_area = $('#char_${userNick}')[0].getBoundingClientRect();
    		
    		if(!flag_enter){
    			flag_enter= true;
    			$('#chat_space').append('<input type="text" class="chat" id="chat"></input>');
    			$('#chat').css({top: y, left: x}); 
    			
    			setTimeout(function(){ 
    				$("#chat").focus();
    			}, 1);
    			
    		}else{
    			flag_enter= false;
    			msg = $('#chat').val();
    			$('#chat').remove();
    			
    			if(msg != ''){
    				send('#chat',msg, chat_float_area.left , chat_float_area.top );
    				
    				
    				/* 
    				$('#chat_space').append('<input type="text" class="chat chat_float" id="chat_float'+v_chat_count+'" readonly="true"></input>'); 
    				$('#chat_float'+v_chat_count).css({top: chat_float_area.top-30, left: chat_float_area.left-37*2});
    				$('#chat_float'+v_chat_count).val(msg);
    				 */
    				 /* 
    				flag_chat_float = true;
    				
    				clearTimeout(v_chat_timer);
    				
    				v_chat_timer = setTimeout(function() {
    					flag_chat_float = false;
    				}, 2000);
    				v_chat_count++; */
    			}
    		}
    	}
    }
 
	
	
		
	</script>

	<section id="space" class='space'>
		<%-- <div id='char_${userNick}' class="char_chat"></div> --%>
	</section>
	
	
	<section id="chat_space" class='space'>
	</section>
	
	<section id="chattings">
	    <div id="_chatbox" style="display: inline;">
	        <fieldset>
	            <textarea id="messageWindow" cols="60" rows="6" readonly="true"></textarea>
	        </fieldset>
	    </div>
    </section>
</BODY>
</HTML>