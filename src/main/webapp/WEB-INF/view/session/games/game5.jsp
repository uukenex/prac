<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>라이언칼잡이</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script type="text/javascript" src="http://bernii.github.io/gauge.js/dist/gauge.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	function init()
    {
		var db_game_no = 5;
		//https://bssow.tistory.com/129 timer int로 조정하기 
		var v_sp_count= 3;//clear timer variable - enemy
		var v_chat_count = 0; // chat floating box name value
		var v_chat_timer;//clear timer variable - chat floating box
		
		var flag_char_short_attack = false; //근거리 공격
		var flag_char_range_atk = false; // 원거리 공격중
		var flag_char_attack_delay = false;//공격 딜레이 
		var flag_char_hit_delay = false; //피격 딜레이
		
		var flag_enemy_create = false; // 적 자동생성
		var flag_enemy_create2 = false; // 적 자동생성
		var flag_boss_attack = false;//보스 존재여부 
		var flag_life_attack = false;//라이프 생성여부
		var flag_life_attack2= false;//100점라이프
		
		var v_boss_range_energy = 3;//보스 원거리 방어 수치
		var v_boss_short_energy = 3;//보스 근거리 방어 수치 
		var v_boss_range_energy_max = 3;//보스 원거리 방어 수치
		var v_boss_short_energy_max = 2;//보스 근거리 방어 수치 
		
		var flag_boss_end = false; //보스 잡았는지 여부 
		var flag_boss_hit_delay = false;//보스 피격 딜레이  
		
		var flag_enter = false; //엔터 눌렸는지 여부 검사
		var flag_chat_float = false;  // 채팅창 떠있는지 검사 
		
		var flag_start_create_enemy = false; 
		
		var v_score_life = 0;// init life
		var v_score_hit = 0; //init hit enemy
		var v_score_max = 0; //init hit enemy
		
		
		var main_interval; //게임종료시 종료할 interval 선언
		var sub_interval; //게임종료시 종료할 interval 선언
		var g_interval; //gauge interval
		var v_gauge=100; //gauge 변수
		var v_gauge_max_value = 100; //gauge max변수
		
		
		$('section_detect').css('width', 400 - 10);
		$('button.detect').css('width', 400 / 10);
		$('button.detect').css('height', 400 / 10);
		$('button.detect').css('background','#FFFFCB'); 
		
		$('button.longBtn').css('width', '100%');
		$('#weapon').css('transform','rotate(180deg)');
		
		
        
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		charx = 0, chary = 0, speed = 1, 
    		$char = $('#char_heat'),
    		$charimg = $('#charimg'),
    		$weapon = $('#weapon'),
    		$weapon_motion = $('#weapon_motion');
    		
    		//var w_r_rect =  $('#weapon')[0].getBoundingClientRect();
    		var range_atk_x = charx+37*2;
    		var range_atk_y = chary+63-18;
    		var range_atk_x_org = charx+37*2;
    		var range_atk_y_org = chary+63-18;
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			if(charx < 0) charx=0;
    			if(chary < 0) chary=0;
    			if(charx > 400-37-18) charx=400-37-18;
    			if(chary > 400-63-31) chary=400-63-31;
    			
    			speed = keypress['65']?3:1
    			if(keypress['83']) f_char_weapon_short_atk_create();//'s' short attack
    			if(keypress['68']) f_char_weapon_range_atk_create();//'d' range attack
    			
    			if(keypress['37']) charx -= speed; // left
    			if(keypress['38']) chary -= speed; // up
    			if(keypress['39']) charx += speed; // right
    			if(keypress['40']) chary += speed; // down
    			
    			if(keypress['32']){
    				if(!flag_start_create_enemy){
    					f_boss_create(v_sp_count,2,400-20-10,50);//'space' enemy create
    				}
    			}
    			
    			
    			range_atk_x_org = charx+37*2;
    			range_atk_y_org = chary+63-18; 
    			$weapon_motion.css({top: chary, left: charx+37*2});
    			
    			if(flag_char_range_atk){
    				range_atk_x = range_atk_x + 5;
    				
    				$weapon.css({top: range_atk_y, left: range_atk_x});
    				/* $weapon.css('border','dashed 1px black'); */
    				if(range_atk_x > 400){
    					flag_char_range_atk = false;
    				}
    				
    			}else{
    				$weapon.css({top: chary+63-18, left: charx+37*2});
    				/* $weapon.css('border',''); */
    				range_atk_x = range_atk_x_org;
    				range_atk_y = range_atk_y_org;
    			}
    			
    			$char.css({top: chary, left: charx});
    			$charimg.css({top: chary, left: charx});
    			
    		}, 10); // 매 0.01 초마다 실행
    	 
    		
    		//window Event
        	$(document).keydown(function(e){ // 어떤 키가 눌렸는지 저장 
        		var key = e.which.toString();
        	
        		f_detect(key,true);
        		if(key=='13'){
        			f_chatter_box_create(400/2-100, 350);//'enter' chatter box
        		} else if(key=='27'){
        			f_enemy_end();
        		} else if(flag_enter){
        			$('#chat')[0].text += key;
        		} else{
        			keypress[key] = true;
        		}
        	});
        	$(document).keyup(function(e){ // 눌렸던 키를 해제
        		var key = e.which.toString();
        	
        		f_detect(key,false);
        		keypress[key] = false;	
        	});
        	
        	$(document).mousedown(function(e){
        		var key = e.target.id.substr(4,2);
        		
        		if(key=='13'){
        			f_chatter_box_create(400/2-100, 350);//'enter' chatter box
        		} else if(key=='27'){
        			f_enemy_end();
        		} else if(flag_enter){
        			$('#chat')[0].text += key;
        		} else{
        			keypress[key] = true;
        		}
        	});
        	$(document).mouseup(function(e){
        		var key = e.target.id.substr(4,2);

        		keypress[key] = false;
        	});
        	
        	$(document).mouseout(function(e){
        		var key = e.target.id.substr(4,2);

        		keypress[key] = false;
        	});
        	
        	/* 터치 액션 .. 개발 진행중
        	
        	var char_touch_point_x;
        	var char_touch_point_y;
        	var move_touch_point_x_before;
        	var move_touch_point_y_before;
        	var move_touch_point_x_after;
        	var move_touch_point_y_after;
        	var move_calc_x;
        	var move_calc_y;
        	var move_direct;
        	
        	var base = {x:0, y:0};
        	
        	var pointerEventToXY = function(e){
         	     var out = {x:0, y:0};
         	     var calc = {x:0, y:0};
         	     
         	     if(e.type == 'touchstart' ){
         	    	var touch = e.originalEvent.touches[0] || e.originalEvent.changedTouches[0];
         	    	base.x = touch.pageX;
         	    	base.y = touch.pageY;
         	    	calc.x = base.x;
           	     	calc.y = base.y;
         	     }
         	     if(e.type == 'touchmove' ){
         	        var touch = e.originalEvent.touches[0] || e.originalEvent.changedTouches[0];
         	        out.x = touch.pageX;
         	        out.y = touch.pageY;
         	        
         	       	calc.x = out.x - base.x;
           	     	calc.y = out.y - base.y;
         	     } 
         	     
         	     
         	     return calc;
         	    };

  	       	$(document).on('touchstart touchmove', function(e){
  	      	  console.log(pointerEventToXY(e)); // will return obj ..kind of {x:20,y:40}
  	      	})
        	
  	      	
  	      	 */


        	
        	
    		
    		
    		sub_interval = setInterval(function(){
        		var e_rect;
        		var c_rect =  $('#char_heat')[0].getBoundingClientRect();
        		var c_a_rect = $('#weapon_motionimg')[0].getBoundingClientRect();
        		var c_r_a_rect = $('#weapon')[0].getBoundingClientRect();
        		
        		var e_b_rect;
        		
        		
        		//enemy action
        		for(var i =0 ; i < $('.enemy').length ; i++){
        			e_rect = $('.enemy')[i].getBoundingClientRect();
        			
        			if(meetBox(e_rect,c_rect) && !flag_char_hit_delay){
        				v_score_life -= 1;
        				
        				flag_char_hit_delay = true;
        				f_twingkling('char',4);
        				setTimeout(function(){
        					flag_char_hit_delay = false;
        				}, 400*3);
        				
        				if(v_score_life < 0){
        					v_score_life = 0;
        				}
        				
        				$('.enemy')[i].remove();
        				$('button.detect').css('background','#FFFFCB'); 
        				
        			}
        			
        			//근거리 공격 또는 원거리 공격 판정
        			if((flag_char_short_attack && meetBox(e_rect,c_a_rect)) || (flag_char_range_atk && meetBox(e_rect,c_r_a_rect) )){
        				v_score_hit += 1;
    	   				$('.enemy')[i].remove();
        			}
        			
        		}
        		
        		for(var i =0 ; i < $('.boss').length ; i++){
        			e_b_rect = $('.boss')[i].getBoundingClientRect();
        			
        			//근거리 공격 판정 
        			if(meetBox(e_b_rect,c_a_rect) && !flag_boss_hit_delay){
        				if(v_boss_short_energy > 0){
            				v_boss_short_energy -= 1;	
            			}
        				flag_boss_hit_delay = true;
        				f_twingkling('boss',6);
        				setTimeout(function(){
        					flag_boss_hit_delay = false;
        				}, 400*5);
        				//딜레이 계산식 400 * (twingkle-1)
        			}
        			
        			//원거리 공격 판정
        			if((flag_char_range_atk && meetBox(e_b_rect,c_r_a_rect) && !flag_boss_hit_delay)){
        				if(v_boss_range_energy > 0){
            				v_boss_range_energy -= 1;	
            			}
        				flag_boss_hit_delay = true;
        				f_twingkling('boss',6);
        				setTimeout(function(){
        					flag_boss_hit_delay = false;
        				}, 400*5);
        				//딜레이 계산식 400 * (twingkle-1)
        			}
        			
        			//보스 kill action
       				if(v_boss_range_energy == 0 && v_boss_short_energy == 0){
       					if(flag_start_create_enemy){
       						v_score_hit += 50;	
       						flag_boss_end = true;
       					}	
       	    			f_boss_hidden();
        			}
        			
        			//보스 충돌시 사망
       				if(meetBox(e_b_rect,c_rect)){
        				v_score_life = 0;
        			}
        		}
        		
        		//life action
        		for(var i =0 ; i < $('.life').length ; i++){
        			l_rect = $('.life')[i].getBoundingClientRect();
        			
        			if(meetBox(l_rect,c_rect)){
        				v_score_life += 1;
        				
        				flag_char_hit_delay = true;
        				f_twingkling('char',4);
        				setTimeout(function(){
        					flag_char_hit_delay = false;
        				}, 400*3);
        				
        				$('.life')[i].remove();
        				$('button.detect').css('background','#FFFFCB'); 
        				
        			}
        			
        			//근거리 공격 또는 원거리 공격 판정
        			if(meetBox(l_rect,c_a_rect) || (flag_char_range_atk && meetBox(l_rect,c_r_a_rect) )){
        				v_score_life -= 1;
        				flag_char_hit_delay = true;
        				f_twingkling('char',4);
        				setTimeout(function(){
        					flag_char_hit_delay = false;
        				}, 400*3);
        				
    	   				$('.life')[i].remove();
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
        		
        		//자동적생성
        		if(flag_start_create_enemy){
    				f_enemy_create(v_sp_count,2,400-20-10,getRandomInt(0, 400-20-10));
    			}
        		//보스 생성
        		if(v_score_hit == 30){
        			f_boss_create(v_sp_count,2,400-20-10,50);
       			}
        		//보스 처치 후 적생성 추가 
        		if(flag_start_create_enemy && flag_boss_end){
					f_enemy_create2(v_sp_count,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		
        		//라이프생성
        		if(v_score_life == 1){
        			f_life_create(v_sp_count,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		//라이프생성
        		if(v_score_hit == 100){
        			f_life_create2(v_sp_count,3,400-20-10,getRandomInt(0, 400-20-10));
       			}
        		
        		//점수변경 감지
       			$('#score_life')[0].innerText=v_score_life;
       			$('#score_hit')[0].innerText=v_score_hit;
       			$('#score_max')[0].innerText=v_score_max;
       			
       			if(flag_boss_attack){
    	   			$('#boss_short_energy')[0].innerText= v_boss_short_energy;
    	   			$('#boss_range_energy')[0].innerText= v_boss_range_energy;
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
    	
    	
    	function f_char_weapon_short_atk_create(){

    		if(!flag_char_short_attack && !flag_char_attack_delay){
    			flag_char_short_attack = true;
    			flag_char_attack_delay = true;
    			//v_gauge = 50;
    			v_gauge_max_value = 100;
    			v_gauge = 0;
    			
    			$('#weapon').css('display','none');
    			$('#weapon_motion').css('display','block');
    			
    			//공격은 0.5초 지속, 공격 후 딜레이는 0.5초 지속
    			//공격 후에 다시 공격을 누를 경우 1초동안 공격하지 말아야함
    			
    			setTimeout(function(){
    				flag_char_short_attack = false;
    				$('#weapon_motion').css('display','none'); 
    				$('#weapon').css('display','block');
    				
    				
    			}, 500);
    			
    			setTimeout(function(){
    				flag_char_attack_delay = false;
    			}, 500+500);
    			
    		}
    		
    	}
    	
    	function f_char_weapon_range_atk_create(){
    		
    		if(!flag_char_range_atk && !flag_char_attack_delay){
    			flag_char_range_atk = true;
    			
    			flag_char_attack_delay = true;
    			v_gauge_max_value = 150;
    			v_gauge = 0;
    			
    			setTimeout(function(){
    				flag_char_attack_delay = false;
    			}, 1500);
    			
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
    			}, 300);	
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
    				v_sp_count++;
    			}, 300);	
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
    			
    			$('#boss_short_energy_text')[0].hidden=false;
    			$('#boss_range_energy_text')[0].hidden=false;
    			$('#boss_short_energy')[0].hidden=false;
    			$('#boss_range_energy')[0].hidden=false;
    			
    			v_boss_short_energy = v_boss_short_energy_max;
    			v_boss_range_energy = v_boss_range_energy_max;
    			
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
    				v_sp_count++;
    			}, 300);	
    		}
    		
    		
    		function boss_move(sp_count,phase){
    			var phase_no_value;
    			var t_boss_move;
    			var v_reverse=false;
    			
    			if(phase == 0){
    				phase_no_value = 300;//초기위치
    				x -= e_speed; 
    				t_boss_move = boss_move_timer;
    				v_reverse=false;
    			}else if(phase == 1){
    				phase_no_value = 150;//x- 방향으로 150까지만 이동
    				x -= e_speed; 
    				t_boss_move = boss_move_timer1;
    				v_reverse=false;
    			}else if(phase == 2){
    				phase_no_value = 300;//x+ 방향으로 300까지만 이동
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
    			}, 300);	
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
    			}, 300);	
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
    	
    	function f_enemy_end(){
    		if(flag_enter){
    			flag_enter= false;
    			$('#chat').remove();
    		}/* else{
    			flag_start_create_enemy = false;	
    		} */
    		
    	}
    	
    	function f_chatter_box_create(x,y){
    		var msg;
    		var chat_float_area = $('#charimg')[0].getBoundingClientRect();
    		
    		if(!flag_enter){
    			flag_enter= true;
    			$('#chat_space').append('<input type="text" class="chat" id="chat"></input>');
    			$('#chat').css({top: y, left: x}); 
    			
    			setTimeout(function(){ 
    				
    				
    				$("#chat").focus();
    				
    				//$("#chat").focus(); 
    			}, 1);

    			//$('#chat')[0].focus();
    			//console.log($('#chat')[0]);
    			
    		}else{
    			flag_enter= false;
    			msg = $('#chat').val();
    			$('#chat').remove();
    			
    			if(msg != ''){
    				if(msg == '시작'){
    					flag_start_create_enemy = true;
    					v_score_life = 3;
    		   			v_score_hit = 0;
    		   			
    		   			f_boss_hidden();
    				}
    				
    				$('#chat_space').append('<input type="text" class="chat chat_float" id="chat_float'+v_chat_count+'" readonly="true"></input>'); 
    				$('#chat_float'+v_chat_count).css({top: chat_float_area.top-30, left: chat_float_area.left-37*2});
    				$('#chat_float'+v_chat_count).val(msg);
    				
    				flag_chat_float = true;
    				
    				clearTimeout(v_chat_timer);
    				
    				v_chat_timer = setTimeout(function() {
    					flag_chat_float = false;
    					v_sp_count++;
    				}, 2000);
    				v_chat_count++;
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
    			flag_boss_attack = false;
    			$('.boss')[0].remove();
    				
    			$('#boss_short_energy_text')[0].hidden=true;
    			$('#boss_range_energy_text')[0].hidden=true;
    			$('#boss_short_energy')[0].hidden=true;
    			$('#boss_range_energy')[0].hidden=true;	
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
		
		<div id="weapon"><img id="weaponimg" src="<%=request.getContextPath()%>/game_set/img/knife.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="weapon_motion" style="display:none" ><img id="weapon_motionimg" src="<%=request.getContextPath()%>/game_set/img/knife_motion.png?v=<%=System.currentTimeMillis()%>"></div>
		<div id="enemy_field"></div>
	</section>
	<section id="chat_space" class='space'>
	</section>
	
	<section id="section_detect">
	<br>
		<table>
			<tr>
				<td>
					<button class='detect' id="btn_65">A Boost</button>
				</td>
				<td>
					<button class='detect' id="btn_83">S Short Atk</button>
				</td>
				<td>
					<button class='detect' id="btn_68">D Range Atk</button>
				</td>
			</tr>

			<tr>
				<td colspan="3"><button class='detect longBtn' id="btn_13">ENTER</button></td>
			</tr>
		</table>
		<table>
			<tr>
				<td>
					<button class='detect' id="btn_27">ESC</button>
				</td>
				<td>
					<button class='detect' id="btn_38">↑</button>
				</td>
			</tr>

			<tr>
				<td><button class='detect' id="btn_37">←</button></td>
				<td><button class='detect' id="btn_40">↓</button></td>
				<td><button class='detect' id="btn_39">→</button></td>
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
		<label id="boss_short_energy_text" hidden="true">보스-근거리보호</label><label id="boss_short_energy" hidden="true"></label>
		</br>
		<label id="boss_range_energy_text" hidden="true">보스-원거리보호</label><label id="boss_range_energy" hidden="true"></label>
		</br>
		
		
	</section>
	<section id="chattings">
		<c:if test="${(userId ne '') and !(empty userId)}">
	        <input type="hidden" value='${userNick}' id='chat_id' />
	    </c:if>
	    <c:if test="${(userId eq '') or (empty userId)}">
	        <input type="hidden" value='<%=session.getId().substring(0, 6)%>'
	            id='chat_id' />
	    </c:if>
	</section>
</BODY>
</HTML>