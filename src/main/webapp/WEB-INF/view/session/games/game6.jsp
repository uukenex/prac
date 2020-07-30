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
		var v_sp_count = 0;
		
		var flag_enter = false; //엔터 눌렸는지 여부 검사
		var flag_chat_float = false;  // 채팅창 떠있는지 검사 
		var flag_bullet_create = false; //공격 (space)
		
		var main_interval ;
		
		var last_key;
		
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
    		
			sub_interval = setInterval(function(){ // 주기적으로 검사
				$charimg.text(last_key);
			
				if(keypress['32']){
    				f_bullet_create(v_sp_count,4,charx,chary,last_key);
    			}
    			
				
    			
    		}, 10); // 매 0.01 초마다 실행
    		
    		//window Event
        	$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
        		var key = e.which.toString();
        	
        		if(key=='13'){
        			f_chatter_box_create(400/2-100, 350);//'enter' chatter box
        		} else if(flag_enter){
        			$('#chat')[0].text += key;
        		} else{
        			keypress[key] = true;
        			if(key == '37' || key =='38' ||key =='39'||key=='40'){
        				last_key = key;	
        			}
        		}
        	});
        	$(document).keyup(function(e){ // 눌렸던 키를 해제
        		var key = e.which.toString();
        		keypress[key] = false;	
        		if(key == '37' || key =='38' ||key =='39'||key=='40'){
    				last_key = key;	
    			}
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
    				 
    			}
    		}
    	}
    	
    	
    	
    	function f_bullet_create(sp_count,e_speed,x,y,l_key){
    		var bullet_move_timer;
    		
    		if(!flag_bullet_create){
    			flag_bullet_create = true;
    			$('#bullet_field').append('<div class="bullet" id="bullet_${userNick}'+sp_count+'"></div>'); 
    			
    			bullet_move_timer = setInterval(function(){
    				
    				bullet_move(sp_count,l_key);
    			}, 10); 
    			
    			setTimeout(function() {
    				flag_bullet_create = false;
    				v_sp_count++;
    			}, 300);	
    		}
    		
    		function bullet_move(sp_count,lkey){
    			switch(lkey){
    				case '37'://left
    					x -= e_speed; 
    					break;
    				case '38'://up
    					y -= e_speed; 
    					break;
    				case '39'://right
    					x += e_speed; 
    					break;
    				case '40'://down
    					y += e_speed; 
    					break;
    			}
    			$('#bullet'+sp_count).css('display','block');
    			
    			$('#bullet_${userNick}'+sp_count).css({top: y, left: x}); 
    			if(x < -30 || x > 430 || y < -30 || y > 430 ){
    				clearTimeout(bullet_move_timer);
    				$('#bullet_${userNick}'+sp_count).remove();
    			}
    		}
    		
    	}
    	
    }
 
	
	
		
	</script>

	<section id="space" class='space'>
		<div id="bullet_field"></div>
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