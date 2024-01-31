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
<link rel="stylesheet"
	href="<%=request.getContextPath()%>/game_set/css/game4_2.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script
		src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
		var db_game_no = 4;
	
		var is_reverse = false;
		var is_dup_speed=false;
		var is_game_over = false;
		
		var is_end_timer_start = false;//20초 게임오버 타이머 시작여부
		var end_timer;//20초 게임오버타이머  
		
		var rotateVar; //타이머 담을 변수 
		var rotate_rate = 2;//배속
		
		var cnt = 0;
		
		var x = 200; // center
		var y = 200; // center
		var r = 50;// radius
		var a = 0; // angle (from 0 to Math.PI * 2)
		
		var bonus_flag = false;
		
		
		function init() {
			
			
			$('#controll').css('top',0);
			$('body').on('click',click_reverse);
			
			$('#sec')[0].innerText = '20';
			$('#milisec')[0].innerText = '00';
			
			enemy_create();
			rotation();
			
			
		}
		
		
		
		function enemy_destroy(){
			$('#enemy').css('left','-200');
			$('#enemy').css('top','-200');
		}
		function enemy_create(){
			$('#enemy').css('left',getRandomInt(60,windowWidth-60));
			$('#enemy').css('top', getRandomInt(60,windowHeight-60));
		}
		
		var dateTime;
		var nowTime;
		function click_reverse(event){
			
			if(!is_end_timer_start){
				dateTime = new Date(); 
				//처음에 저장했던시간
				nowTime = dateTime.getTime();
				
				end_timer = setInterval(function() {
					
					if(bonus_flag){
						var sec1 = dateTime.getSeconds();
						nowTime = dateTime.setSeconds(dateTime.getSeconds()+2);
						bonus_flag = false;
					}
					
					 //1ms당 한 번씩 현재시간 timestamp를 불러와 nowTime에 저장
					 //현재시간이 더 크다 에서 마이너스처리 
					var newTime = new Date(Date.now() - nowTime); //(nowTime - stTime)을 new Date()에 넣는다
					
					var min = newTime.getMinutes();
					var sec = newTime.getSeconds(); //초
					
					if(sec > 20){
						console.log(sec);
						sec = 0;
					}
					
					var milisec = Math.floor(newTime.getMilliseconds() / 10); //밀리초
					$('#sec')[0].innerText = addZero(19-sec);
					$('#milisec')[0].innerText = addZero(99-milisec);
					
					if(19-sec < 0){
						game_over('time is over ..');
					}
					
				}, 5);
				
				is_end_timer_start= true;
				
			}
			
			if(is_game_over){
				return;
			} 
			
			is_reverse==true?is_reverse = false: is_reverse = true;
			clearTimeout(rotateVar);
			
			$('#is_reverse')[0].innerText=is_reverse;
			
			if(event != 'rd_func'){
				var prevX = $('#center2')[0].getBoundingClientRect().x;
				var prevY = $('#center2')[0].getBoundingClientRect().y;
				var cursorX = $('#point2')[0].getBoundingClientRect().x;
				var curosrY = $('#point2')[0].getBoundingClientRect().y;
				
				x = prevX + 2*(cursorX-prevX);
				y = prevY + 2*(curosrY-prevY);
				a = a >= Math.PI?a-Math.PI:a+Math.PI;	
				
				$('#center2_2').css( 'left',x+"px");
				$('#center2_2').css( 'top' ,y+"px");
				
			}
			rotation();
		}
		
		
		function click_speed_control(event){
			var is_speed_up = event;
			is_speed_up ? rotate_rate<10?rotate_rate+=1:rotate_rate=rotate_rate:rotate_rate>1?rotate_rate-=1:rotate_rate=rotate_rate;
		}
		function click_r_incdec(event){
			var is_increase = event;
			
			if(is_dup_speed){//중복실행방지 
				return;
			} 
			is_dup_speed = true;
			
			for(var i=0;i<25;i++){
				promise_r_incdec(i,is_increase);
			}
			
			
		}
		
		async function promise_r_incdec(seq,is_increase){
				return new Promise((resolve, reject) => {
					setTimeout(()=>{
						clearTimeout(rotateVar);
						is_increase?r+=2:r>10?r=r-1:r=r;
						rotation();
						resolve(seq);
					}, 15*seq); 
				}).then(function(result){
					if(seq==24){
						is_dup_speed = false; //마지막 promise가 종료될때 중복플래그를 종료 
					}
				}) ;
		}
		
		function rotation(){
			var px;
			var py;

			rotateVar = setInterval(function() {
				for(var rate=0;rate<rotate_rate;rate++){
					if(is_reverse){
						a = (a + Math.PI / 360) % (Math.PI * 2);
					}else{
						a = (a - Math.PI / 360) % (Math.PI * 2);	
					}
				}
				if(is_game_over){
					return;
				}
				rotate(a);
			}, 5);
			
			
			function rotate() {
				
				$('#center2').css('left',x+"px");
				$('#center2').css('top' ,y+"px");
				$('#point2').css( 'left',px+"px");
				$('#point2').css( 'top' ,py+"px");
				
				px = x + r * Math.cos(a);
				py = y + r * Math.sin(a);	 
				
				var rect = $('#enemy')[0].getBoundingClientRect();
				var c_rect = $('#point2')[0].getBoundingClientRect();
				
				if(meetWall(c_rect)){
				//if(px > windowWidth || px < 0 || py > windowHeight || py < 0){
					game_over('벽에 부딪힘..');
				}
				
				if(meetBox(c_rect,rect)){
				//if(px > rect.left && px <rect.right && py > rect.top && py < rect.bottom){
					cnt++;
					$('#cnt')[0].innerText=cnt;
					
					var rd;
					if(cnt%2==0){
						rd = rd_func();
					}
					if(cnt%4==0){
						rd_func(rd);
					}
					
					enemy_destroy();
					bonus_flag =true;
					
					enemy_create();
					
				}
			}
			
			function rd_func(post_process){
				var rd = getRandomInt(0,4);
				
				switch(rd){
					case 0:
						if(post_process == 0 || post_process == 1){
							rd_func(post_process);
							return;
						}
						click_r_incdec(true);
						break;
					case 1:
						if(post_process == 0 || post_process == 1){
							rd_func(post_process);
							return;
						}
						click_r_incdec(false);
						break;
					case 2:
						if(post_process == 2 || post_process == 3){
							rd_func(post_process);
							return;
						}
						click_speed_control(true);
						break;
					case 3:
						if(post_process == 2 || post_process == 3){
							rd_func(post_process);
							return;
						}
						click_speed_control(false);
						break;
					case 4:
						if(post_process == 4){
							rd_func(post_process);
							return;
						}
						click_reverse('rd_func');
						break;	
				}
				return rd;
			}
		}
		
		function game_over(arg0){
			if(is_game_over){
				return;
			} 
			is_game_over = true;
			
			clearTimeout(rotateVar);
			clearTimeout(end_timer);
			
			common_game_over(db_game_no, cnt, arg0,'${userId }');
		}
		
		
	</script>

	<section id="space">

		<div id="center2"></div>
		<div id="point2"></div>

		<div id="center2_2"></div>

		
		
		<div id="enemy"></div>
	</section>

	<section id="controll">
		v1.01
		<br/>
		설명 : 화면 아무곳 클릭시 반대로 회전합니다.
		</br>
		초록박스에 닿으면 점수획득
		</br>
		.
		</br>
		2점마다 아래 효과 중 랜덤 발동
		</br>
		<strong>회전범위 증가/감소, 속도 증가/감소, reverse</strong>
		</br>
		.
		</br>
		현재점수 : <label id="cnt"></label>
		</br>
		정방회전 : <label id="is_reverse"></label>
		</br>
		TIMER <strong><label id="sec"></label> : <label id="milisec"></label></strong>
		
	</section>
</BODY>
</HTML>