<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>라이언칼잡이2</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://bernii.github.io/gauge.js/dist/gauge.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	function init()
    {
		var db_game_no = 10;
		//https://bssow.tistory.com/129 timer int로 조정하기 
		var v_sp_count= 3;//clear timer variable - enemy
		
		var flag_start_create_range_atk = false;
		
		var flag_char_short_attack = false; //근거리 공격
		var flag_char_range_atk = false; // 원거리 공격중
		var flag_char_attack_delay = false;//공격 딜레이 
		var flag_char_hit_delay = false; //피격 딜레이
		
		var flag_enemy_create = false; // 적 자동생성
		var flag_enemy_create2 = false; // 적 자동생성
		var flag_boss_attack = false;//보스 존재여부 
		var flag_life_attack = false;//라이프 생성여부
		var flag_life_attack2= false;//100점라이프
		
		var flag_item_attack = false; 
		var flag_item_attack2 = false; 
		var flag_item_mode = 1; //어택 줄기수  
		
		var v_boss_tot_energy 	  = 45;
		var v_boss_tot_energy_max = 45;
		
		var flag_boss_end = false; //보스 잡았는지 여부 
		var flag_boss_hit_delay = false;//보스 피격 딜레이  
		
		var flag_start_create_enemy = false; 
		
		var v_score_life = 0;// init life
		var v_score_hit = 0; //init hit enemy
		var v_score_max = 0; //init hit enemy
		
		
		var main_interval; //게임종료시 종료할 interval 선언
		var sub_interval; //게임종료시 종료할 interval 선언
		var g_interval; //gauge interval
		var v_gauge = 100; //gauge 변수
		var v_gauge_max_value = 100; //gauge max변수
		
		var press_s = false;
		var press_d = false;
		var auto_press_d = false;
		
		$('section_detect').css('width', 400 - 10);
		$('button.detect').css('width', 400 / 10);
		$('button.detect').css('height', 400 / 10);
		$('button.detect').css('background','#FFFFCB'); 
		
		$('button.longBtn').css('width', '100%');
		//$('#weapon').css('transform','rotate(180deg)');
		//$('.weapon_range').css('transform','rotate(180deg)');
		
		$('#change_char').on('click',function(){change_charater();});
		$('#starting').on('click',function(){start_game();});
		$('#auto_missile').on('click',function(){auto_missile();});
        
		
		//imsi
    	function change_charater(){
    		$('#charimg')[0].src='/game_set/img/chunsik.png';
    	}
    	function start_game(){
    		flag_start_create_enemy = true;
    		v_score_life = 3;
    		v_score_hit = 0;
    	}
    	function auto_missile(){
    		if(auto_press_d){
    			auto_press_d = false;
    		}else{
    			auto_press_d = true;
    		}
    	}
		
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 1, 
    		$char = $('#char_heat'),
    		$charimg = $('#charimg'),
    		$weapon = $('#weapon'),
    		$weapon_motion = $('#weapon_motion');
    		
    		var range_atk_x = charx+37*2;
    		var range_atk_y = chary+63-18;
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			if(charx < 0) charx=0;
    			if(chary < 0) chary=0;
    			if(charx > 400-37-18) charx=400-37-18;
    			if(chary > 400-63-31) chary=400-63-31;
    			
    			speed = keypress['65']?3:1 // 'a' booster
    			if(keypress['83']) press_s=true;//'s' direct attack
    			if(keypress['68']) press_d=true;//'d' range attack
    			
    			if(keypress['37']) charx -= speed; // left
    			if(keypress['38']) chary -= speed; // up
    			if(keypress['39']) charx += speed; // right
    			if(keypress['40']) chary += speed; // down
    			
    			   			
    			 
    			$weapon_motion.css({top: chary, left: charx+37*2});
    			
    			$char.css({top: chary, left: charx});
    			$charimg.css({top: chary, left: charx});
    			
    		}, 10); // 매 0.01 초마다 실행
    	 
        	$(document).on('keydown mousedown touchstart', function (e) {
				
        		if(e.type == 'keydown'){
        			var key = e.which.toString();
        		}else{
        			var key = e.target.id.substr(4,2);
        		}
        		
        		f_detect(key,true);
        		keypress[key] = true;
        		
        	});
        	
        	$(document).on('keyup mouseup mouseout touchend touchcancel', function (e) {
        		if(e.type == 'keyup'){
        			var key = e.which.toString();
        		}else{
        			var key = e.target.id.substr(4,2);
        		}
        		f_detect(key,false);
        		keypress[key] = false;
        	});
        	
        	
    		sub_interval = setInterval(function(){
        		var e_rect;
        		var c_rect =  $('#char_heat')[0].getBoundingClientRect();
        		var c_a_rect = $('#weapon_motionimg')[0].getBoundingClientRect();
        		var c_r_a_rect;
        		var e_b_rect;
        		var i_rect;
        		var l_rect;
        		
       			//enemy action
           		for(var i =0 ; i < $('.enemy').length ; i++){
           			e_rect = $('.enemy')[i].getBoundingClientRect();
           			
           			if(meetBox(e_rect,c_rect) && !flag_char_hit_delay){
           				v_score_life -= 1;
           				
           				flag_char_hit_delay = true;
           				f_twingkling('char',10);
           				setTimeout(function(){
           					flag_char_hit_delay = false;
           				}, 800*3);
           				
           				if(v_score_life < 0){
           					v_score_life = 0;
           				}
           				
           				$('.enemy')[i].remove();
           				$('button.detect').css('background','#FFFFCB'); 
           				
           			}
           			
           			for(var j=0; j<$('.weapon_range').length;j++){
               			c_r_a_rect = $('.weapon_range')[j].getBoundingClientRect();
               			
               			//원거리 공격 판정
               			if(meetBox(e_rect,c_r_a_rect) ){
               				v_score_hit += 1;
           	   				$('.enemy')[i].remove();
           	   				$('.weapon_range')[j].remove();
               			}
           			}
           		}
       			
           		for(var i =0 ; i < $('.boss').length ; i++){
           			e_b_rect = $('.boss')[i].getBoundingClientRect();
           			
           			for(var j=0; j<$('.weapon_range').length;j++){
               			c_r_a_rect = $('.weapon_range')[j].getBoundingClientRect();
               			//원거리 공격 판정
               			if( meetBox(e_b_rect,c_r_a_rect)){
               				$('.weapon_range')[j].remove();
               				if(!flag_boss_hit_delay){
	               				if(v_boss_tot_energy > 0){
	               					v_boss_tot_energy -= 1;	
	                   			}
	               				//flag_boss_hit_delay = true;
	               				f_twingkling('boss',6);
	               				setTimeout(function(){
	               					//flag_boss_hit_delay = false;
	               				}, 40*5);
	               				//딜레이 계산식 400 * (twingkle-1)
               				}
               			}
           			}
           			
           			//보스 kill action
          			if(v_boss_tot_energy <= 0){
       						v_score_hit += 50;	
       						flag_boss_end = true;
          	    			f_boss_hidden();
           			}
           			
           			//보스 충돌시 사망
          				if(meetBox(e_b_rect,c_rect)){
           				v_score_life = 0;
           			}
           		}
        		
        		
        		for(var i =0 ; i < $('.life').length ; i++){
        			l_rect = $('.life')[i].getBoundingClientRect();
        			
        			if(meetBox(l_rect,c_rect)){
        				v_score_life += 1;
        				$('.life')[i].remove();
        			}
        			
        			/** 라이프 공격시 라이프 깎이던 로직 제거  
        			//근거리 공격 또는 원거리 공격 판정
        			if(meetBox(l_rect,c_a_rect) || meetBox(l_rect,c_r_a_rect) ){
        				v_score_life -= 1;
        				flag_char_hit_delay = true;
        				f_twingkling('char',4);
        				setTimeout(function(){
        					flag_char_hit_delay = false;
        				}, 400*3);
        				
    	   				$('.life')[i].remove();
        			}
        			*/
        			
        		}
        		
        		for(var i =0 ; i < $('.item').length ; i++){
        			i_rect = $('.item')[i].getBoundingClientRect();
        			
        			if(meetBox(i_rect,c_rect)){
        				
        				switch(flag_item_mode){
        					case 1:
        						flag_item_mode =2;
        						break;
        					case 2:
        						flag_item_mode =3;
        						break;
        					case 3:
        						break;
        				}        				
        				$('.item')[i].remove();
        			}
        		}
        		
        		if(press_s){
        			f_char_weapon_direct_atk_create();
        			press_s = false;
        		}
        		
        		if(auto_press_d){
        			f_char_weapon_range_atk_create(v_sp_count,6,charx,chary,flag_item_mode);
        		}
        		if(press_d){
        			f_char_weapon_range_atk_create(v_sp_count,6,charx,chary,flag_item_mode);
        			press_d = false;
        		}
        		
        		//자동적생성
        		if(flag_start_create_enemy){
    				f_enemy_create(v_sp_count++,2,400-20-10,getRandomInt(0, 400-20-10));
    			}
        		//보스 생성
        		if(v_score_hit >= 30 && !flag_boss_attack ){
        			f_boss_create(v_sp_count++,2,400-20-10,50);
       			}
        		//보스 처치 후 적생성 추가 
        		if(flag_start_create_enemy && flag_boss_end){
        			f_enemy_create2(v_sp_count++,4,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		
        		//라이프생성
        		if(v_score_life == 1 && !flag_life_attack){
        			//flag_life_attack=false;
        			f_life_create(v_sp_count++,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		//라이프생성
        		if(v_score_hit == 100 && !flag_life_attack2){
        			//flag_life_attack=false;
        			f_life_create2(v_sp_count++,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		
        		//아이템생성
        		if(v_score_hit >= 10 && !flag_item_attack2){
        			//flag_item_attack=false;
        			f_item_create(v_sp_count++,1,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		//아이템생성
        		if(v_score_hit >= 50 && !flag_item_attack2){
        			//flag_item_attack=false;
        			f_item_create2(v_sp_count++,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		
        		
        		//점수변경 감지
       			$('#score_life')[0].innerText=v_score_life;
       			$('#score_hit')[0].innerText=v_score_hit;
       			$('#score_max')[0].innerText=v_score_max;
       			
       			if(flag_boss_attack){
    	   			$('#boss_tot_energy')[0].innerText= v_boss_tot_energy;
       			}
        		
       			//사망 감지
       			if(v_score_life==0 && flag_start_create_enemy ){
       				flag_start_create_enemy = false;
   					keypress = [];
   					
   					if(v_score_max < v_score_hit){
   						v_score_max = v_score_hit;	
   					}
   					
   					f_game_over();
       			}
        	}, 10);	
    		
    		
    		
    		//examples.... => https://bernii.github.io/gauge.js/ 
    		var opts = {
    				  lines: 1,
    				  angle: 0,
    				  lineWidth: 0.3,
    				  pointer: {
    				    length: 0,
    				    strokeWidth: 0,
    				    color: '#ccc'
    				  },
    				  limitMax: 'false', 
    				  percentColors: [[0.20, "#720000" ], [0.50, "#EBEB3A"], [1, "#5ACF40"]],
    				  strokeColor: '#E0E0E0',
    				  generateGradient: false
    				};
    		var target = document.getElementById('sp_gauge');
    		var gauge = new Gauge(target).setOptions(opts);
    		gauge.maxValue = v_gauge_max_value;
    		gauge.animationSpeed = 1;
    		gauge.set(v_gauge);

    		g_interval = setInterval(function(){v_gauge+=1; gauge.set(v_gauge); gauge.maxValue = v_gauge_max_value}, 10);
    	});
    	
    	
    	function f_char_weapon_direct_atk_create(){

    		if(!flag_char_short_attack && !flag_char_attack_delay){
    			flag_char_short_attack = true;
    			flag_char_attack_delay = true;
    			//v_gauge = 50;
    			v_gauge_max_value = 1000;
    			v_gauge = 0;
    			
    			$('#weapon').css('display','none');
    			$('#weapon_motion').css('display','block');
    			$('#enemy_field').append('<div id="bomb1"><img id="bomb1_img" src="<%=request.getContextPath()%>/game_set/img/bomb1.png?v=<%=System.currentTimeMillis()%>"></div>');
    			//공격은 0.5초 지속, 공격 후 딜레이는 0.5초 지속
    			//공격 후에 다시 공격을 누를 경우 1초동안 공격하지 말아야함
    			
    			setTimeout(function(){
    				flag_char_short_attack = false;
    				$('#weapon_motion').css('display','none'); 
    				$('#weapon').css('display','block');
    				$('#bomb1').remove();
    				f_char_weapon_direct_all_destory_enemy();
    				
    			}, 1000);
    			
    			setTimeout(function(){
    				flag_char_attack_delay = false;
    			}, v_gauge_max_value*10 );
    			
    			//v_boss_tot_energy -= 10;
    			//폭탄 보스 데미지x
    			f_char_weapon_direct_all_destory_enemy();
    		}
    		
    	}
    	
    	function f_char_weapon_direct_all_destory_enemy(){
    		for(var i =0 ; i < $('.enemy').length ; i++){
    			v_score_hit += 1;
    		}
    		$('.enemy').remove();
    	}
    	
    	function f_char_weapon_range_atk_create(sp_count,r_speed,x,y,item_mode){
			var range_atk_move_timer;
			var range_atk_move_timer1;
			var range_atk_move_timer2;
			var range_atk_move_timer3;
    		
			if(item_mode == 1){
				v_sp_count++;
				if(!flag_char_range_atk){
	    			flag_char_range_atk = true;
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			
	    			range_atk_move_timer = setInterval(function(){
	    				$('#weaponimg'+sp_count).css('display','block');
	    				range_atk_move(sp_count);
	    			}, 10); 
	    			
	    			setTimeout(function() {
	    				flag_char_range_atk = false;
	    			}, 400);	
	    		}
	    		
	    		function range_atk_move(sp_count){
	    			x += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y, left: x}); 
	    			if(x > 400 ){
	    				clearTimeout(range_atk_move_timer);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
			}else if(item_mode == 2){
				var x_up = x;
				var x_down= x;
				var y_up= y;
				var y_down= y;
				
				var sp_count_up = sp_count;
				var sp_count_down = sp_count+1;
				v_sp_count = v_sp_count+2;				
				
				if(!flag_char_range_atk){
	    			flag_char_range_atk = true;
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count_up+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			range_atk_move_timer1 = setInterval(function(){
	    				$('#weaponimg'+sp_count_up).css('display','block');
	    				range_atk_move_up(sp_count_up);
	    			}, 10); 
	    			
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count_down+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			range_atk_move_timer2 = setInterval(function(){
	    				$('#weaponimg'+sp_count_down).css('display','block');
	    				range_atk_move_down(sp_count_down);
	    			}, 10);
	    			
	    			setTimeout(function() {
	    				flag_char_range_atk = false;
	    			}, 400);	
	    		}
	    		
	    		function range_atk_move_up(sp_count){
	    			x_up += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y_up-30, left: x_up}); 
	    			if(x_up > 400 ){
	    				clearTimeout(range_atk_move_timer1);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
	    		function range_atk_move_down(sp_count){
	    			x_down += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y_down+30, left: x_down}); 
	    			if(x_down > 400 ){
	    				clearTimeout(range_atk_move_timer2);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
	    		
			}else if(item_mode == 3){
				var x_up = x;
				var y_up= y;
				var x_mid = x;
				var y_mid = y;
				var x_down= x;
				var y_down= y;
				
				var sp_count_up = sp_count;
				var sp_count_mid = sp_count+1;
				var sp_count_down = sp_count+2;
				v_sp_count = v_sp_count+3;				
				
				if(!flag_char_range_atk){
	    			flag_char_range_atk = true;
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count_up+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			range_atk_move_timer1 = setInterval(function(){
	    				$('#weaponimg'+sp_count_up).css('display','block');
	    				range_atk_move_up(sp_count_up);
	    			}, 10); 
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count_mid+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			range_atk_move_timer2 = setInterval(function(){
	    				$('#weaponimg'+sp_count_mid).css('display','block');
	    				range_atk_move_mid(sp_count_mid);
	    			}, 10); 
	    			
	    			$('#char').append('<div id="weaponimg'+sp_count_down+'" class="weapon_range"><img class="weapon_size" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>'); 
	    			range_atk_move_timer3 = setInterval(function(){
	    				$('#weaponimg'+sp_count_down).css('display','block');
	    				range_atk_move_down(sp_count_down);
	    			}, 10);
	    			
	    			setTimeout(function() {
	    				flag_char_range_atk = false;
	    			}, 400);	
	    		}
	    		
	    		function range_atk_move_up(sp_count){
	    			x_up += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y_up-35, left: x_up}); 
	    			if(x_up > 400 ){
	    				clearTimeout(range_atk_move_timer1);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
	    		function range_atk_move_mid(sp_count){
	    			x_mid += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y_mid, left: x_mid}); 
	    			if(x_mid > 400 ){
	    				clearTimeout(range_atk_move_timer2);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
	    		function range_atk_move_down(sp_count){
	    			x_down += r_speed; 
	    			$('#weaponimg'+sp_count).css({top: y_down+35, left: x_down}); 
	    			if(x_down > 400 ){
	    				clearTimeout(range_atk_move_timer3);
	    				$('#weaponimg'+sp_count).remove();
	    			}
	    		}
			}
    	}
    	
    	function f_enemy_create(sp_count,e_speed,x,y){
    		var enemy_move_timer;
    		
    		
    		
    		if(!flag_enemy_create){
    			flag_enemy_create = true;
    			$('#enemy_field').append('<div class="enemy" id="enemy'+sp_count+'"></div>'); 
    			
    			enemy_move_timer = setInterval(function(){
    				$('#enemy'+sp_count).css('display','block');
    				enemy_move(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				flag_enemy_create = false;
    				v_sp_count++;
    			}, 200);	
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
    	function f_enemy_create2(sp_count,e_speed,x,y){
    		var enemy_move_timer;
    		
    		
    		
    		if(!flag_enemy_create2){
    			flag_enemy_create2 = true;
    			$('#enemy_field').append('<div class="enemy" id="enemy'+sp_count+'"></div>'); 
    			
    			enemy_move_timer = setInterval(function(){
    				$('#enemy'+sp_count).css('display','block');
    				enemy_move(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				flag_enemy_create2 = false;
    			}, 200);	
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
    	
    	function f_boss_create(sp_count,e_speed,x,y){
    		var boss_move_timer;
    		var boss_move_timer1;
    		var boss_move_timer2;
    		var boss_move_timer3;
    		
    		if(!flag_boss_attack){
    			flag_boss_attack = true;
    			
    			$('#boss_tot_energy_text')[0].hidden=false;
    			$('#boss_tot_energy')[0].hidden=false;
    			
    			v_boss_tot_energy = v_boss_tot_energy_max;
    			
    			$('#enemy_field').append(
    			'<div class="boss" id="boss'+sp_count+'"><img id="bossimg" src="<%=request.getContextPath()%>/game_set/img/apeech.png?v=<%=System.currentTimeMillis()%>"></div>'
    			); 
    			
    			setTimeout(function() {
    				boss_move_timer = setInterval(function(){
    					$('#boss'+sp_count).css('display','block');
    					boss_move(sp_count,0);
    				},10);
    			}, 6000*0);
    			
    			setTimeout(function() {
    				boss_move_timer1 = setInterval(function(){
    					boss_move(sp_count,1);
    				},10);
    			}, 6000*1);
    			
    			setTimeout(function() {
    				boss_move_timer2 = setInterval(function(){
    					boss_move(sp_count,2);
    				},10);
    			}, 6000*2);
    			
    			setTimeout(function() {
    				boss_move_timer3 = setInterval(function(){
    					boss_move(sp_count,3);
    				},10);
    			}, 6000*3);
    			
    			
    			setTimeout(function() {
    			}, 200);	
    		}
    		
    		
    		function boss_move(sp_count,phase){
    			var phase_no_value;
    			var t_boss_move;
    			var v_reverse=false;
    			
    			if(phase == 0){
    				phase_no_value = 250;//초기위치
    				x -= e_speed; 
    				t_boss_move = boss_move_timer;
    				v_reverse=false;
    			}else if(phase == 1){
    				phase_no_value = 120;//x- 방향으로 120까지만 이동
    				x -= e_speed; 
    				t_boss_move = boss_move_timer1;
    				v_reverse=false;
    			}else if(phase == 2){
    				phase_no_value = 250;//x+ 방향으로 300까지만 이동
    				x += e_speed; 
    				t_boss_move = boss_move_timer2;
    				v_reverse=true;
    			}else if(phase == 3){
    				phase_no_value = -100;//x- 방향으로 -100까지 이동
    				x -= e_speed; 
    				t_boss_move = boss_move_timer3;
    				v_reverse=false;
    			}
    			
    			
    			$('#boss'+sp_count).css({top: y, left: x}); 
    			if(v_reverse){
    				if(x > phase_no_value ){
        				clearTimeout(t_boss_move);
        			}
    			}else{
    				if(x < phase_no_value ){
        				clearTimeout(t_boss_move);
        			}
    			}
    			
    			if(x < -95){
    				f_boss_hidden();
    			}
    		}
    		
    	}
    	function f_item_create(sp_count, e_speed, x, y){
    		var item_move_timer;
    		
    		if(!flag_item_attack){
    			flag_item_attack = true;
    			$('#enemy_field').append('<div class="item" id="item'+sp_count+'"></div>'); 
    			
    			item_move_timer = setInterval(function(){
    				item_move(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				
    			}, 200);	
    		}
    		
    		function item_move(sp_count){
    			x -= e_speed; 
    			$('#item'+sp_count).css({top: y, left: x}); 
    			if(x < -30 ){
    				clearTimeout(item_move_timer);
    				$('#item'+sp_count).remove();
    			}
    		}
    	}
    	function f_item_create2(sp_count, e_speed, x, y){
    		var item_move_timer;
    		
    		if(!flag_item_attack2){
    			flag_item_attack2 = true;
    			$('#enemy_field').append('<div class="item" id="item'+sp_count+'"></div>'); 
    			
    			item_move_timer = setInterval(function(){
    				item_move(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				
    			}, 200);	
    		}
    		
    		function item_move(sp_count){
    			x -= e_speed; 
    			$('#item'+sp_count).css({top: y, left: x}); 
    			if(x < -30 ){
    				clearTimeout(item_move_timer);
    				$('#item'+sp_count).remove();
    			}
    		}
    	}
    	
    	function f_life_create(sp_count, e_speed, x, y){
    		var life_move_timer;
    		
    		if(!flag_life_attack){
    			flag_life_attack = true;
    			$('#enemy_field').append('<div class="life" id="life'+sp_count+'"></div>'); 
    			
    			life_move_timer = setInterval(function(){
    				life_move(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				//flag_life_attack = false;
    				v_sp_count++;
    			}, 200);	
    		}
    		
    		function life_move(sp_count){
    			x -= e_speed; 
    			$('#life'+sp_count).css({top: y, left: x}); 
    			if(x < -30 ){
    				clearTimeout(life_move_timer);
    				$('#life'+sp_count).remove();
    			}
    		}
    	}
    	
    	
    	function f_life_create2(sp_count,e_speed,x,y){
    		var life_move_timer2;
    		
    		if(!flag_life_attack2){
    			flag_life_attack2 = true;
    			$('#enemy_field').append('<div class="life" id="life'+sp_count+'"></div>'); 
    			
    			life_move_timer2 = setInterval(function(){
    				life_move2(sp_count);
    			}, 10); 
    			
    			setTimeout(function() {
    				//flag_life_attack = false;
    				v_sp_count++;
    			}, 200);	
    		}
    		
    		function life_move2(sp_count){
    			x -= e_speed; 
    			$('#life'+sp_count).css({top: y, left: x}); 
    			if(x < -30 ){
    				clearTimeout(life_move_timer2);
    				$('#life'+sp_count).remove();
    			}
    		}
    	}

		function f_detect(key_number,bool_value){
    		if(bool_value){
    			$('#btn_'+key_number).css('background','#C4B73B');	
    		}else{
    			$('#btn_'+key_number).css('background','#FFFFCB');
    		}
    	}

    	
    	function f_twingkling(classname,cnt){
    		
        	for(var retry=0;retry<cnt;retry++){
        		setTimeout(function(){
       				$('.'+classname).toggleClass("twingkle");	
       			},400*retry);
        	}
        	//피격 반짝임
    		//짝수번에 맞게 호출해야함 
    	}
    	
    	function f_boss_hidden(){
    		if(flag_boss_attack){
    			if($('.boss')[0] != null){
    				$('.boss')[0].remove();	
    				$('#boss_tot_energy_text')[0].hidden=true;
        			$('#boss_tot_energy')[0].hidden=true;
    			}
    		}
    	}
    	
    	function f_game_over(){
    		flag_start_create_enemy = false;
    		f_boss_hidden();
    		
    		clearInterval( main_interval );
    		clearInterval( sub_interval );
    		clearInterval( g_interval );
    		common_game_over(db_game_no, v_score_max, "",'${userId }');
    	}
    }
 
	
	
		
	
	
		
	</script>

	<section id="space" class='space'>
		<div id= "char">
			<div id="char_heat"></div>
		</div>
		<img class='char' id="charimg" src="<%=request.getContextPath()%>/game_set/img/lion.png?v=<%=System.currentTimeMillis()%>">
		
		<div id="weapon_motion" style="display:none" ><img id="weapon_motionimg" src="<%=request.getContextPath()%>/game_set/img/knife_motion.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="enemy_field"></div>
	</section>
	
	<section id="section_detect">
	<br>
		<table>
			<tr>
				<td>
				</td>
				<td>
					<button class='detect' id="btn_38">↑</button>
				</td>
				<td>
				</td>
			</tr>

			<tr>
				<td><button class='detect' id="btn_37">←</button></td>
				<td><button class='detect' id="btn_40">↓</button></td>
				<td><button class='detect' id="btn_39">→</button></td>
			</tr>
		</table>
		<table>
			<tr>
				<td><button class='detect' id="btn_65">A Boost</button></td>
				<td><button class='detect' id="btn_83">S Bomb</button></td>
				<td><button class='detect' id="btn_68">D Range Atk</button></td>
			</tr>
			<tr>
				<td><button class='detect' id="change_char">춘식</button></td>
				<td><button class='detect' id="starting">시작</button></td>
				<td><button class='detect' id="auto_missile">AUTO</button></td>
			</tr>
		</table>
	</section>
	
	<section id="description">
	    <canvas id="sp_gauge"></canvas>
	    </br>
		처치 기록 : <label id="score_max"></label>
		</br>
		총 처치 수 : <label id="score_hit"></label>
		</br>
		남은 생명 : <label id="score_life"></label>
		</br>
		<label id="boss_tot_energy_text" hidden="true">보스-체력</label><label id="boss_tot_energy" hidden="true"></label>
		</br>
		
		
	</section>
</BODY>
</HTML>