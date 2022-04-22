<!-- 미사용 -->
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
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game6.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	<input type="hidden" value='${userNick}' id='chat_id' />
	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/websocket_module2.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	<c:if test="${(testMode == true)}">
		//TESTMODE
		function testMode(){
			var id = prompt('사용할 닉네임입력');
			if(id ==null || id== undefined || id===null ){
				alert('비정상 접근입니다.');
				$('#chat_space').append('<input type="text" class="chat" id="chat" value="비정상접근상태"></input>');
				//location.href = "free?page=1";
			}else{
				$('#chat_id').val(id);	
			}
			
		}
		
	</c:if>
	
	function init()
    {
		<c:if test="${(testMode == true)}">
			testMode();
		</c:if>
		var db_game_no = 6;
		
		var v_chat_timer;//clear timer variable - chat floating box
		var v_sp_count = 0;
		
		var flag_enter = false; //엔터 눌렸는지 여부 검사
		var flag_chat_float = false;  // 채팅창 떠있는지 검사 
		var flag_bullet_create = false; //공격 (space)
		
		var main_interval ;
		
		var last_key;
		
		if($('#chat_id').val() !='' ){
			onConnect();
		}
		
		
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 2, 
    		//$charimg = $('#char_${userNick}');
    		$charimg = $('#char_'+$('#chat_id').val());
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			
    			if($charimg.length==0){
    				$charimg = $('#char_'+$('#chat_id').val());	
    			}
    		
    			
    			if(charx < 0) charx=0;
    			if(chary < 0) chary=0;
    			if(charx > 400-18) charx=400-18;
    			if(chary > 400-31) chary=400-31;
    			
    			if(keypress['37']) {
    				charx -= speed; // left
    				send('02','', charx , chary );
    			}
    			if(keypress['38']) {
    				chary -= speed; // up
    				send('02','', charx , chary );
    			}
    			if(keypress['39']) {
    				charx += speed; // right
    				send('02','', charx , chary );
    			}
    			if(keypress['40']) {
    				chary += speed; // down
    				send('02','', charx , chary );
    			}
    			
    			$charimg.css({top: chary, left: charx});
    			
    			
    		}, 10); // 매 0.01 초마다 실행
    		
			sub_interval = setInterval(function(){ // 주기적으로 검사
				//$charimg.text(v_atk_count + '/' + v_hit_count);
			
				if(keypress['32']){
    				f_bullet_create(v_sp_count,4,charx,chary,last_key);
    			}
				
				//피격판정
        		for(var i =0 ; i < $('.bullet').length ; i++){
        			var b_class = $('.bullet')[i];
        			var b_rect = b_class.getBoundingClientRect();
					for(var j =0 ; j < $('.char_chat').length ; j++){
						var t_class =  $('.char_chat')[j];
						var t_rect =  t_class.getBoundingClientRect();
						
						if(meetBox(b_rect,t_rect)){
							//본인 총알에 안맞는처리							
	        				if(b_class.classList[1] == t_class.classList[1]){
	            				continue;
	            			}
	        				$('.bullet')[i].remove();
	        				//target(맞은사람)이 본인일때 점수차감 
	        				if(t_class.classList[1]==$('#chat_id').val()){
	        					//v_hit_count--;
	        					f_minus_count();
	        				}
	        				//bullet(총주인)이 본인일때 점수획득 
	        				else if(b_class.classList[1]==$('#chat_id').val()){
	        					//v_atk_count++;
	        					f_plus_count();
	        				}
	        					
	        			}
					}        			
        		}
				
    		}, 10); // 매 0.01 초마다 실행
    		
        	$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
        		var key = e.which.toString();
        	
        		if(key=='13'){
        			f_chatter_box_create(400/2-100, 350);//'enter' chatter box
        		} else if(flag_enter){
        			//$('#chat')[0].text += key;
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
    			}
        	});
    		
    	});
    	
    	function f_plus_count(){
    		send('05','PLUS');
    	}
		function f_minus_count(){
    		send('05','MINUS');
    	}
    	
    	function f_chatter_box_create(x,y){
    		var msg;
    		var chat_float_area = $('#char_'+$('#chat_id').val())[0].getBoundingClientRect();
    		
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
    				send('01', msg, chat_float_area.left , chat_float_area.top );
    				 
    			}
    		}
    	}
    	
    	function f_bullet_create(sp_count,e_speed,x,y,l_key){
    		var bullet_move_timer;
    		
    		if(!l_key){
    			return;
    		}
    		
    		if(!flag_bullet_create){
    			flag_bullet_create = true;
    			
    			send('03',l_key, x, y)
    			setTimeout(function() {
    				flag_bullet_create = false;
    			}, 300);	
    		}
    	}
    }
 
	
	
		
	</script>

	<section id="space" class='space'>
		<div id="bullet_field"></div>
	</section>
	
	
	<section id="chat_space" class='space'>
	</section>
	<section id="chat_user_list" class='half'>
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