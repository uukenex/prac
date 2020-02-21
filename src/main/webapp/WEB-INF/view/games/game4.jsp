<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>원형이동 practice</TITLE>

<link rel="stylesheet"
	href="<%=request.getContextPath()%>/game_set/css/game4_2.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">
	<script src="http://code.jquery.com/jquery.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script
		src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
		var is_reverse = false;
		var is_dup_speed=false;
		var rotateVar; //타이머 담을 변수 
		//var rotateSpeed = 5;
		var rotate_rate = 2;//배속
		
		var cnt = 0;
		
		
		var partition = 20;//원형구체 충돌 방지 간격
		
		var x = 200 // center
		var y = 200 // center
		var r = 50 // radius
		var a = 0 // angle (from 0 to Math.PI * 2)
		
		function init() {
			
			$('body').on('click',click_reverse);
			$('#controll').css('top',windowHeight-200);
			
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
		
		function click_reverse(event){
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
						is_increase?r+=2:r>10?r-=2:r=r;
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
						a = (a - Math.PI / 360) % (Math.PI * 2);
					}else{
						a = (a + Math.PI / 360) % (Math.PI * 2);	
					}
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
				
				if(px > windowWidth || px < 0 || py > windowHeight || py < 0){
					game_over();
				}
				
				
				if(px > rect.left && px <rect.right && py > rect.top && py < rect.bottom){
					cnt++;
					$('#cnt')[0].innerText=cnt;
					if(cnt%2==0){
						var rd = rd_func();
					}
					if(cnt%4==0){
						rd_func(rd);
					}
					
					
					enemy_destroy();
					enemy_create();
					
				}
			}
			
			function rd_func(post_process){
				var rd = getRandomInt(0,4);
				
				switch(rd){
					case 0:
						if(post_process == 1){
							rd_func(post_process);
							return;
						}
						click_r_incdec(true);
						break;
					case 1:
						if(post_process == 0){
							rd_func(post_process);
							return;
						}
						click_r_incdec(false);
						break;
					case 2:
						if(post_process == 3){
							rd_func(post_process);
							return;
						}
						click_speed_control(true);
						break;
					case 3:
						if(post_process == 2){
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
		
		function game_over(){
			clearTimeout(rotateVar);
			windowFadeOut();
			/* alert(cnt+'점 달성!'); */
			
			setTimeout(function(){
				/* 
				if(isLocal){
					console.log('testMode '+arg0);
					arg0 = 'testMode';
				}
				 */
				var promptVal = prompt('벽에 부딪힘..'+cnt+'점 \n이름을 입력해주세요.');
				
				if(promptVal==null){
					return;
				}
				
				$.ajax({
					type : "post",
					url : "/game4/saveGame4Cnt",
					data : {
						userName : promptVal,						
						mediaCode: mediaCode,
						ip : ip(),
						cnt : cnt
					},
					success : function(res) {
						alert(cnt+"점수가 저장되었습니다");
						console.log(res);
						
						var innerHTML = '<table><tr><td>인입</td><td>ID</td><td>점수</td><tr>';
						$(res.RESULT).each(function(idx,item){
							innerHTML += '<tr><td>'+item.MEDIA_CODE+'</td><td>'+item.USER_ID+'</td><td>'+item.CNT+'</td><tr>';
							
						});
						
						innerHTML += '</table>';
						
						$('body')[0].innerHTML = innerHTML;
						
						windowFadeIn();
						
					},
					error : function(request, status, error) {
						alert(request);
					}
				});
				
			},4000);
			
			
		}
		
		
	</script>

	<section id="space">

		<div id="center2"></div>
		<div id="point2"></div>

		<div id="center2_2"></div>

		
		
		<div id="enemy"></div>
	</section>

	<section id="controll">
		설명 : 화면 아무곳 클릭시 반대로 회전합니다.</br>
		회전하는 붉은점으로 초록박스를 먹으면 점수+1!!</br>
		3점마다 아래 효과 중 발동
		</br>
		<strong>회전범위 증가/감소, 속도 증가/감소, reverse</strong>
		<!-- 
		회전범위 : <label id="range"></label>
		</br> 
		현재속도 : <label id="speed"></label>
		</br> 
		 -->
		</br>
		현재점수 : <label id="cnt"></label>
		</br>
		정방회전 : <label id="is_reverse"></label>
	</section>
</BODY>
</HTML>