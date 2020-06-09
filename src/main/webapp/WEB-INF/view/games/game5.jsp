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
<BODY onload="init()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
		
	
	//https://bssow.tistory.com/129 timer int로 조정하기 
	var v_sp_count= 3;
	var v_chat_count = 0;
	
	var flag_char_attack = false;
	var flag_char_attack_delay = false;
	
	var flag_enemy_attack = false;
	
	var flag_enter = false;
	var flag_chat_float = false;
	
	var key_press_list = ['65','83','68','37','38','39','40','32'];
	
	function init()
    {
		$('section_detect').css('width', 400 - 10);
		$('button.detect').css('width', 400 / 10);
		$('button.detect').css('height', 400 / 10);
		$('button.detect').css('background','#FFFFCB'); 
		$('button#btn_blank').css('display','hidden');
		
		$('#weapon').css('transform','rotate(180deg)');
		
        output = document.getElementById("output");
    }
 
	function f_detect(key_number,bool_value){
		if(bool_value){
			$('#btn_'+key_number).css('background','#C4B73B');	
		}else{
			$('#btn_'+key_number).css('background','#FFFFCB');
		}
		
	}
    
	$(function(){
		var keypress = {}, // 어떤 키가 눌려 있는지 저장
		charx = 0, chary = 0, speed = 1, 
		$char = $('#char'),
		$weapon = $('#weapon'),
		$weapon_motion = $('#weapon_motion');
		
		
		setInterval(function(){ // 주기적으로 검사
			if(charx < 0) charx=0;
			if(chary < 0) chary=0;
			if(charx > 400-37-18) charx=400-37-18;
			if(chary > 400-63-31) chary=400-63-31;
			
			speed = keypress['65']?3:1
			if(keypress['83']) f_char_weapon_motion_create();//'s' short attack
			if(keypress['68']) f_char_weapon_motion_create();//'d' range attack
			
			if(keypress['37']) charx -= speed; // left
			if(keypress['38']) chary -= speed; // up
			if(keypress['39']) charx += speed; // right
			if(keypress['40']) chary += speed; // down
			
			if(keypress['32']) f_enemy_create(v_sp_count,2,400-20-10,getRandomInt(0, 400-20-10));//'space' enemy create
			
			$weapon.css({top: chary+63-18, left: charx+37*2});
			$weapon_motion.css({top: chary, left: charx+37*2});
			$char.css({top: chary, left: charx});
			
		}, 10); // 매 0.01 초마다 실행
	 
		$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
			if(e.which.toString()=='13'){
				f_chatter_box_create(400/2-100, 350);//'enter' chatter box
			}else if(flag_enter){
				$('#chat')[0].text += e.which.toString();
			}else{
				keypress[e.which.toString()] = true;
			}
		});
		$(document).keyup(function(e){ // 눌렸던 키를 해제
			f_detect(e.which.toString(),false);
			keypress[e.which.toString()] = false;	
		});
		
		
		
		
		setInterval(function(){
    		var e_rect;
    		var c_rect =  $('#charimg')[0].getBoundingClientRect();
    		var c_a_rect = $('#weapon_motionimg')[0].getBoundingClientRect();
    		
    		//부딪힘판정
    		for(var i =0 ; i < $('.enemy').length ; i++){
    			e_rect = $('.enemy')[i].getBoundingClientRect();
    			
    			if(e_rect.left < c_rect.right && e_rect.right > c_rect.left
    	          && e_rect.top < c_rect.bottom && e_rect.bottom > c_rect.top
    			){
    				alert('꽝');
    				clearTimeout(v_sp_count);
    				$('.enemy')[i].remove();
    				keypress = {};
    				$('button.detect').css('background','#FFFFCB'); 
    			}
    			
    			if(e_rect.left < c_a_rect.right && e_rect.right > c_a_rect.left
	   	          && e_rect.top < c_a_rect.bottom && e_rect.bottom > c_a_rect.top
	   			){
	   				console.log('어택');
	   				clearTimeout(v_sp_count);
	   				$('.enemy')[i].remove();
	   			}
    		}
    		
    		//채팅창 
    		if(flag_chat_float){
    			var $chat_float = $('.chat_float');
				var chat_float_area = $('#charimg')[0].getBoundingClientRect();
				$chat_float.css({top: chat_float_area.top-30, left: chat_float_area.left-37*2});
				//$chat_float.css({top: chary-30, left: charx-37*2 });
			}else{
				$('.chat_float').remove();
			}
    		
    	}, 10);	
	});
	
	
	function f_char_weapon_motion_create(){

		if(!flag_char_attack && !flag_char_attack_delay){
			flag_char_attack = true;
			
			$('#weapon').css('display','none');
			$('#weapon_motion').css('display','block');
			
			//공격은 0.5초 지속, 공격 후 딜레이는 1초 지속
			//공격 후에 다시 공격을 누를 경우 1초동안 공격하지 말아야함
			
			
			setTimeout(function(){
				flag_char_attack = false;
				$('#weapon_motion').css('display','none'); 
				$('#weapon').css('display','block');
				flag_char_attack_delay = true;
				
			}, 500);
			
			setTimeout(function(){
				flag_char_attack_delay = false;
			}, 1500);
			
		}
		
	}
	
	function f_enemy_create(sp_count,e_speed,x,y){
		var enemy_move_timer;
		
		if(!flag_enemy_attack){
			flag_enemy_attack = true;
			$('#enemy_field').append('<div class="enemy" id="enemy'+sp_count+'"></div>'); 
			enemy_move_timer = setInterval(function(){
				enemy_move(sp_count);
			}, 10); 
			
			setTimeout(function() {
				flag_enemy_attack = false;
				v_sp_count++;
			}, 1000);	
		}
		
		function enemy_move(sp_count){
			x -= e_speed; 
			$('#enemy'+sp_count).css({top: y, left: x}); 
			if(x < -30 ){
				clearTimeout(enemy_move_timer);
				$('#enemy'+sp_count).remove();
			}
		}
		
	}
	
	function f_chatter_box_create(x,y){
		var msg;
		var chat_float_area = $('#charimg')[0].getBoundingClientRect();
		
		if(!flag_enter){
			flag_enter= true;
			$('#chat_space').append('<input type="text" class="chat" id="chat"></input>');
			$('#chat').css({top: y, left: x}); 
			
			$('#chat')[0].focus();
			
		}else{
			flag_enter= false;
			msg = $('#chat').val();
			$('#chat').remove();
			
			if(msg != ''){
				$('#chat_space').append('<input type="text" class="chat chat_float" id="chat_float'+v_chat_count+'" readonly="true"></input>'); 
				$('#chat_float'+v_chat_count).css({top: chat_float_area.top-30, left: chat_float_area.left-37*2});
				$('#chat_float'+v_chat_count).val(msg);
				flag_chat_float = true;
				
				setTimeout(function() {
					flag_chat_float = false;
					v_sp_count++;
				}, 2000);
				v_chat_count++;
			}
		}
	}
	
	
		
	</script>

	<section id="space" class='space'>
		<div id="char"><img id="charimg" src="<%=request.getContextPath()%>/game_set/img/lion.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="weapon"><img id="weaponimg" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="weapon_motion" style="display:none" ><img id="weapon_motionimg" src="<%=request.getContextPath()%>/game_set/img/knife_motion.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="enemy_field"></div>
	</section>
	<section id="chat_space" class='space'>
	</section>
	
	<section id="section_detect">
		<button class='detect' id="btn_blank"></button>
		<button class='detect' id="btn_38">↑</button><br>
		<button class='detect' id="btn_37">←</button>
		<button class='detect' id="btn_40">↓</button>
		<button class='detect' id="btn_39">→</button><br>
		
		<button class='detect' id="btn_65">A Boost</button>
		<button class='detect' id="btn_83">S ShortAtk</button>
		<button class='detect' id="btn_68">D RangeAtk</button>
	</section>
	
	
	
</BODY>
</HTML>