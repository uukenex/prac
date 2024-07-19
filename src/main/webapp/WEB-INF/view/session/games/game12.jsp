<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE></TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game12.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<!-- <script type="text/javascript" src="http://bernii.github.io/gauge.js/dist/gauge.min.js"></script> -->
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	function init()
    {
		var db_game_no = 12;
		//https://bssow.tistory.com/129 timer int로 조정하기 
		var v_sp_count= 3;//clear timer variable - enemy
		
		var main_interval; //게임종료시 종료할 interval 선언
		var sub_interval; //게임종료시 종료할 interval 선언
		
		var press_space = false;
		
		var angle = 45;// angle (from 0 to Math.PI * 2)
		var charx = 0;
		
		$('section_detect').css('width', 400 - 10);
		$('button.detect').css('width', 400 / 10);
		$('button.detect').css('height', 400 / 10);
		$('button.detect').css('background','#FFFFCB'); 
		
		$('button.longBtn').css('width', '100%');
		
		$('#starting').on('click',function(){start_game();});
        
    	function start_game(){
    	}
    	
    	$(function(){
    		var keypress = {}, // 어떤 키가 눌려 있는지 저장
    		speed = 1, //charx , angle은 전역변수 
    		$char = $('#char_heat'),
    		$char_angle = $('#char_angle');
    		
    		main_interval = setInterval(function(){ // 주기적으로 검사
    			//이동거리제한
    			if(charx < 0) charx=0;
    			if(charx > 400-30-15) charx=400-30-15;
    			
    			//각도제한
    			if(angle < 0) angle=0;
    			if(angle > 90) angle=90;
    			
    			speed = keypress['65']?3:1 // 'a' booster
    			if(keypress['32']) press_space=true;//space pressed
    			
    			if(keypress['37']) charx -= speed; // left
    			if(keypress['39']) charx += speed; // right
    			
    			if(keypress['38']) angle += speed; // up
    			if(keypress['40']) angle -= speed; // down
    			 
    			$char.css({top: 400-15-8, left: charx});
    			$char_angle.css({top: 400-15-8, left: charx+30-16, 'transform': 'rotate(-'+angle+'deg)'});
    			
    			$('#charx')[0].innerText=charx;
    			$('#angle')[0].innerText=angle;
    			
    			var missx= charx+30 + 25*Math.cos(angle*Math.PI*2 / 360)+5;
    			var missy= 400-15 - 25*Math.sin(angle*Math.PI*2 / 360);
    			$('#missx')[0].innerText=Math.round(missx);
    			$('#missy')[0].innerText=Math.round(missy);
    			
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
        		
        		if(press_space){
        			press_space = false;
        			var missx= charx+30 + 25*Math.cos(angle*Math.PI*2 / 360)+5;
        			var missy= 400-15 - 25*Math.sin(angle*Math.PI*2 / 360);
        			$('#enemy_field').append('<div style="background-color: red; top:'+missy+';left:'+missx+';" class="bullet" ></div>'); 
        		}
        	}, 10);	
    		
    		
    		
    	});
    	
    	
    	
    	
		function f_detect(key_number,bool_value){
    		if(bool_value){
    			$('#btn_'+key_number).css('background','#C4B73B');	
    		}else{
    			$('#btn_'+key_number).css('background','#FFFFCB');
    		}
    	}

    }
 
	
	
		
	
	
		
	</script>

	<section id="space" class='space'>
		<div id= "char">
			<div id="char_heat"></div>
			<div id="char_angle"></div>
		</div>
		
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
				<!--
				<td><button class='detect' id="btn_65">A Boost</button></td>
				<td><button class='detect' id="btn_83">S Bomb</button></td>
				<td><button class='detect' id="btn_68">D Range Atk</button></td>
				-->
			</tr>
			<tr>
				<!--
				-->
			</tr>
			  
			<tr>
				<td><button class='detect' id="btn_32">space</button></td>
			</tr>
		</table>
	</section>
	
	<section id="description">
	    </br>
		각도 : <label id="angle"></label>
		</br>
		현위치(x축):<label id="charx"></label>
		</br>
		총구(x축):<label id="missx"></label>
		</br>
		총구(y축):<label id="missy"></label>
		</br>
		
		
	</section>
</BODY>
</HTML>