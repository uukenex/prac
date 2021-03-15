<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />
<meta property="og:title" content="알파카 연구소 채팅방">  
<meta property="og:type"  content="website">
<meta property="og:image" content="<%=request.getContextPath()%>/game_set/img/paimon.jpg?v=<%=System.currentTimeMillis()%>">
<meta property="og:description" content="알파카 연구소 채팅방">
<TITLE>알파카연구소 채팅방</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game6.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	<input type="hidden" value='${userNick}' id='chat_id' />
	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/websocket_module5.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/map/game6_1_map0.js?v=<%=System.currentTimeMillis()%>"></script>
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
				id = id.replaceAll('[','').replaceAll(']','')
				$('#chat_id').val(id);	
			}
			
		}
		
	</c:if>
	
	var map = mapArr0;
	var map_1D_size = map.length; //A one-dimensional array size
	var map_2D_size = map.length*map.length; //A two-dimensional array size
	var map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
	
	var map_x_size = 400;
	var map_y_size = 400;
	
	
	
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
		
		
		initMakeCell();
		initWindow();
		
		
		
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 2, 
    		charx_org =0,chary_org=0,
    		//$charimg = $('#char_${userNick}');
    		$charimg = $('#char_'+$('#chat_id').val());
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			
    			if($charimg.length==0){
    				$charimg = $('#char_'+$('#chat_id').val());	
    			}
    		
    			
    			if(charx < 0) charx=0;
    			if(chary < 0) chary=0;
    			if(charx > map_x_size-20) charx=map_x_size-20;
    			if(chary > map_y_size-20) chary=map_y_size-20;
    			
    			if(keypress['37']) {
    				
    				if(!moveable_on_rock()){
    					//벽 불가처리 
    					charx += speed; // left
    				}else{
    					charx -= speed; // left
        				send('02','', charx , chary );	
    				}
    				
    			}
    			if(keypress['38']) {
    				if(!moveable_on_rock()){
    					//벽 불가처리 
    					chary += speed; // up
    				}else{
    					chary -= speed; // up
       					send('02','', charx , chary );	
    				}
    				
    					
    			}
    			if(keypress['39']) {
    				if(!moveable_on_rock()){
    					//벽 불가처리 
    					charx -= speed; // 
    				}else{
    					charx += speed; // right
        				send('02','', charx , chary );	
    				}
    				
    				
    			}
    			if(keypress['40']) {
    				if(!moveable_on_rock()){
    					//벽 불가처리 
    					chary -= speed;
    				}else{
    					chary += speed; // down
        				send('02','', charx , chary );	
    				}
    				
    			}
    			
    			charx_org = charx;
				chary_org = chary;
				
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
        			f_chatter_box_create(map_x_size/2-100, 350);//'enter' chatter box
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
    			}
        	});
    		
    	});
    	
    	function f_plus_count(){
    		send('05','PLUS');
    	}
		function f_minus_count(){
    		send('05','MINUS');
    	}
		
		//지나갈수있는 경우 true
    	function moveable_on_rock(arg){
    		//벽에 닿을때 처리
    		for(var i =0 ; i < $('#memories > tbody > tr > td').length ; i++){
    			var rock_value = $('#memories > tbody > tr > td')[i].dataset.value;
    			var rock_rect = $('#memories > tbody > tr > td')[i].getBoundingClientRect();
    			
    			var char_rect_org = $('#char_'+$('#chat_id').val())[0];
    			if(char_rect_org == null){
    				continue;
    			}
    			var char_rect = char_rect_org.getBoundingClientRect();
    			var rtnValue= true;
    			
    			//rock_rect.left   -= 5;
    			//rock_rect.right  += 5;
    			//rock_rect.top    -= 5;
    			//rock_rect.bottom += 5;
    			
    			if(rock_value == '4' && meetBox(char_rect,rock_rect)){
    				
    				//미리계산 
        			switch(arg){
    					case '37': //left
    						char_rect.left -= 1;
    						break;
    					case '38': //up
    						char_rect.top -= 1;
    						break;
    					case '39': //right
    						char_rect.left += 1;
    						break;
    					case '40': //down
    						char_rect.top += 1;
    						break;
    			
    				}
    				
    				rtnValue = meetBoxPre(char_rect,rock_rect);
    				
    				
    				switch(arg){
	    	    		case '37': //left
	    					char_rect.left += 1;
	    					break;
	    				case '38': //up
	    					char_rect.top += 1;
	    					break;
	    				case '39': //right
	    					char_rect.left -= 1;
	    					break;
	    				case '40': //down
	    					char_rect.top -= 1;
	    					break;
	    		
	    			}
    				
    				return !rtnValue;
    			}
    		}
    		
    		
    		return true;
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
 
	function initMakeCell(){
		$('#memories > tbody').remove();
		$('#memories').append("<tbody>");
		for (var x = 0; x < map_1D_size; x++) {
			$('#memories > tbody:last').append("<tr>");
			for (var y = 0; y < map_1D_size; y++) {
				$('#memories > tbody:last > tr:last').append("<td class='cell' data-value="+map[x][y]+" />");
			}
			$('#memories > tbody:last').append("</tr>");
		}
		$('#memories').append("</tbody>");
		
		$('td').css('width', map_x_size / map_1D_size);
		$('td').css('height', map_y_size / map_1D_size);
		
	}
	
	function initWindow(){
		$('#testPanel').append("<input type='button' value='map0' onclick='mapChange(0)'>");
		$('#testPanel').append("<input type='button' value='map1' onclick='mapChange(1)'>");
	}
	
	function chkNowCell(){
		var meRect = $('#char_'+$('#chat_id').val())[0].getBoundingClientRect();
        var leftValue = $('#char_'+$('#chat_id').val()).css('left');
        var topValue = $('#char_'+$('#chat_id').val()).css('top');
        
        
        
	}
	
	function mapChange(mapNo){
		if(mapNo==0){
			map = mapArr0;
		}else{
			map = mapArr1;
		}
		
		map_1D_size = map.length; //A one-dimensional array size
		map_2D_size = map.length*map.length; //A two-dimensional array size
		map_1D_digit_point = (map.length - 1).toString().length;//D_POINT
		
		initMakeCell();
	}
		
	</script>

	<section id="space" class='space'>
		<div id="bullet_field"></div>
		<table id="memories"></table>
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
    <section id="testPanel" class='half_next'></section>
</BODY>
</HTML>