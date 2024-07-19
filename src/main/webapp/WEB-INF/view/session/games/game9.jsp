<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />
<TITLE>20211001 dev</TITLE>
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game9.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/game_enhance.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/game_items.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	var autoyn = false; //자동강화 사용여부
	var autocnt= 0;
	var v_auto_timer;
	
	var able_click =true;
	
	var bless1yn =false; //축복1 적용여부
	var bless2yn =false; //축복2 적용여부
	var v_bless_timer;//적용여부 반짝임 타이머
	
	var fail_correction =0;//장기백 시스템 
	
	function init()
    {
		$('#res_field').val( $('#res_field').val() + '강화기 시뮬레이터.. \n자동강화는 축복영향 없음..\n확률공개..ing\n' );
		$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
		
		// html 페이지에서 'rel=tooltip'이 사용된 곳에 마우스를 가져가면 
	    $('a[rel=tooltip]').mouseover(function(e) 
	    {
	         var tip = $(this).attr('title');         
	 
	        // 브라우져에서 제공하는 기본 툴 팁을 끈다
	        $(this).attr('title','');
	         
	        // css와 연동하기 위해 html 태그를 추가해줌
	        $(this).append('<div id="tooltip"><div class="tipBody">'
	                      + weapons.toString(this.id) + '</div></div>');               
	    }).mousemove(function(e) 
	   {
	         //마우스가 움직일 때 툴 팁이 따라 다니도록 위치값 업데이트
	        $('#tooltip').css('top', e.pageY + 10 );
	        $('#tooltip').css('left', e.pageX + 10 );
	    }).mouseout(function() 
	    {
	        //위에서 껐던 브라우져에서 제공하는 기본 툴 팁을 복원
	        $(this).attr('title',$('.tipBody').html());
	        $(this).children('div#tooltip').remove();
	    });
		
		
		if(windowWidth < 340){
			$('#chattings').css('display','none');	
		}
	    
		var wp_list = ['wp10','wp20','wp30','wp40','wp50'];
		
		wp_list.forEach(function(wp_one,idx){
				$('#'+wp_one).append('<img id="img_'+wp_one+'" src="<%=request.getContextPath()%>/game_set/img/'+weapons[wp_one].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
			    $('#'+wp_one).append('<input type="button" value="+0">');	
			}
		);
   	}
	
	function advCalc(wp){
		//강화 계수표 참조
		var DPos = fun_dPoing(wp.wp_org_damage);
		
		wp.wp_add_damage = wp.wp_org_damage * advance_loa.getAdvVal(wp.wp_adv_no);
		
		return Math.floor(wp.wp_add_damage);
		
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
	
	
	function bless_1(){
		if(!able_click){
			$('#res_field').val( $('#res_field').val() + '강화 실행 중 !!! \n' );
			$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
			return;
		}
		
		if(!bless1yn){
			clearTimeout(v_bless_timer);
			
			bless1yn = true;
			bless2yn = false;
			$('#wp_bless_1').text('+5%적용중');
			$('#wp_bless_2').text('축복2단계');
			
			f_twingkling('wp_bless_1',2);
			v_bless_timer = setInterval(function() {
				f_twingkling('wp_bless_1',2);
			}, 1600);
			
		}else{
			clearTimeout(v_bless_timer);
			bless1yn = false;
			$('#wp_bless_1').text('축복1단계');
		}
		
	}
	
	function bless_2(){
		if(!able_click){
			$('#res_field').val( $('#res_field').val() + '강화 실행 중 !!! \n' );
			$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
			return;
		}
		
		if(!bless2yn){
			clearTimeout(v_bless_timer);
			bless1yn = false;
			bless2yn = true;
			$('#wp_bless_2').text('+15%적용중');
			$('#wp_bless_1').text('축복1단계');
			
			f_twingkling('wp_bless_2',2);
			v_bless_timer = setInterval(function() {
				f_twingkling('wp_bless_2',2);
			}, 1600);
			
		}else{
			clearTimeout(v_bless_timer);
			bless2yn = false;
			$('#wp_bless_2').text('축복2단계');
		}
	}
	
	function autoAdvUp(){
		
		var max_adv_no = 22;
		if(!able_click){
			$('#res_field').val( $('#res_field').val() + '강화 실행 중 !!! \n' );
			$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
			return;
		}
		
		if(autoyn){
			autoyn = false;
			$('#wp_auto').text('자동강화시작');
			clearTimeout(v_auto_timer);
			return;
		}else{
			autoyn = true;
			$('#wp_auto').text('자동강화중지');
		}
		
		autocnt = 0;
		
		if(wp50.wp_adv_no == max_adv_no){
			$('#wp_auto').text('자동강화시작');
			alert('자동강화는 여기까지..');
			autoyn = false;
			return;
		}
		
		v_auto_timer = setInterval(function() {
			autocnt++;
			advUp_auto(wp50,'wp50');
			
			if(wp50.wp_adv_no >= max_adv_no){
				autoyn = false;
				$('#wp_auto').text('자동강화시작');
				alert(autocnt+'회 실행하여 목표에 도달하였습니다.\n25강화까지 전설무기를 강화해보세요');
				clearTimeout(v_auto_timer);
				return;
			}
		}, 200);
		
	}
	
	
	
	
	function advUp_auto(wp,tag_id){

		var res;
		if(wp.wp_adv_no > 24){
			alert('25단계까지 구현되었습니다..!!');
			return;
		}
		
		var org_damage = wp.wp_org_damage;
		
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_add_damage;
		
		var bef_adv_rate = advance_rate.getAdvRateVal(wp.wp_adv_no);
		var adv_rate =bef_adv_rate; 
		
		
		if(fail_correction >= 100){
			wp.wp_adv_no++;	
			fail_correction = 0;
			res=' 강화에 성공하였습니다.'+"\n장인의기운: 초기화";
		}else{
			//1, 100을 포함한 수 중 랜덤
			//10강화시 80 <= 30 (31~100::70퍼센트로 실패)
			if(getRandomInt(1,100) <= adv_rate){
				wp.wp_adv_no++;	
				fail_correction = 0;
				res=' 강화에 성공하였습니다.'+"\n장인의기운: 초기화";
			}else{
				//wp.wp_adv_no--;
				fail_correction += advance_correction.getAdvVal(wp.wp_adv_no);
				res=' 강화에 실패하였습니다.'+"\n장인의기운: "+fail_correction;
			}
			
		}
		
		wp.wp_add_damage = advCalc(wp);
		
		var aft_adv_no = wp.wp_adv_no;
		var aft_damage = wp.wp_add_damage;
		var aft_adv_rate =advance_rate.getAdvRateVal(wp.wp_adv_no+1);
		
		var msg = wp.wp_name_kr +' +'+ (bef_adv_no+1) + res + '\n'
			msg+=          bef_adv_no+'강, 데미지: ' +org_damage+'(+'+ bef_damage +')' ;
		    msg+= ' => ' + aft_adv_no+'강, 데미지: ' +org_damage+'(+'+ aft_damage +')\n\n';
		
		$('#' + tag_id + '> input').val('+' + aft_adv_no + '.이번확률: ' + bef_adv_rate + '%');
		console.log(aft_adv_no+' '+bef_adv_rate);
		
		$('#res_field').val( $('#res_field').val() + '(축복미적용)자동.. cnt: '+autocnt +' 회차 실행 중...');
		$('#res_field').val( $('#res_field').val() + msg );
		$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
		
		if($('.tipBody')[0]){
			$('.tipBody')[0].innerHTML = weapons.toString(tag_id);	
		}
	}
	
	
	function advUp(wp,tag_id){
		var res;
		if(wp.wp_adv_no > 24){
			alert('25단계까지 구현되었습니다..!!');
			return;
		}
		if(autoyn){
			$('#res_field').val( $('#res_field').val() + '자동강화 중 사용 불가 !!! \n' );
			$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
			return;
		}
		
		var org_damage = wp.wp_org_damage;
		
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_add_damage;
		
		var bef_adv_rate = advance_rate.getAdvRateVal(wp.wp_adv_no);
		var adv_rate =bef_adv_rate; 
		
		if(bless1yn){
			adv_rate = Number(adv_rate) + 5;
		}
		if(bless2yn){
			adv_rate = Number(adv_rate) + 15;	
		}
		
		
		var enhance_flag = 1;
		if(able_click){
			able_click = false;	
			
			//1, 100을 포함한 수 중 랜덤
			//10강화시 80 <= 30 (31~100::70퍼센트로 실패)
			var random_value = getRandomInt(1,100);
			
			if(fail_correction >= 100){
				enhance_flag = 1;
				$('#img_'+tag_id)[0].src='<%=request.getContextPath()%>/game_set/img/enhance_fast_1.gif?v=<%=System.currentTimeMillis()%>';
			}else{
				if(random_value <= adv_rate){
					enhance_flag = 1;
					$('#img_'+tag_id)[0].src='<%=request.getContextPath()%>/game_set/img/enhance_fast_1.gif?v=<%=System.currentTimeMillis()%>';
				}else{
					enhance_flag = 2;
					$('#img_'+tag_id)[0].src='<%=request.getContextPath()%>/game_set/img/enhance_fast_2.gif?v=<%=System.currentTimeMillis()%>';
				}
			}
			
			setTimeout(function(){
				able_click = true;
				$('#img_'+tag_id)[0].src='<%=request.getContextPath()%>/game_set/img/'+weapons[tag_id].wp_img+'?v=<%=System.currentTimeMillis()%>';
				if(enhance_flag == 1){
					wp.wp_adv_no++;	
					fail_correction = 0;
					res=' 강화에 성공하였습니다.\n확률공개: '+random_value +' <= '+ adv_rate+"\n장인의기운: 초기화";
				}else{
					//wp.wp_adv_no--;
					fail_correction += advance_correction.getAdvVal(wp.wp_adv_no);
					res=' 강화에 실패하였습니다.\n확률공개: '+random_value +' <= '+ adv_rate+"\n장인의기운: "+fail_correction;
					
				}
				
				wp.wp_add_damage = advCalc(wp);
				
				var aft_adv_no = wp.wp_adv_no;
				var aft_damage = wp.wp_add_damage;
				var aft_adv_rate =advance_rate.getAdvRateVal(wp.wp_adv_no+1);
				
				var msg = wp.wp_name_kr +' +'+ (bef_adv_no+1) + res + '\n'
					msg+=          bef_adv_no+'강, 데미지: ' +org_damage+'(+'+ bef_damage +')' ;
				    msg+= ' => ' + aft_adv_no+'강, 데미지: ' +org_damage+'(+'+ aft_damage +')\n\n';
				
				$('#' + tag_id + '> input').val('+' + aft_adv_no + '.이번확률: ' + bef_adv_rate + '%');
				
				$('#res_field').val( $('#res_field').val() + msg );
				$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
				
				if($('.tipBody')[0]){
					$('.tipBody')[0].innerHTML = weapons.toString(tag_id);	
				}
		        
		        
		        if(wp.wp_adv_no >= 25){
			        var promptVal = prompt('축하드립니다. 25강화 달성자의 이름입력:');
					alert(promptVal+' 바보');
		        }
				
				
			}, 2000);
		}else{
			$('#res_field').val( $('#res_field').val() + '강화쿨타임..\n' );
			$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
			return;
		}
		
		
	}
	</script>
	
	<section id="space" class='space'>
		<div>
			<a href="#" id="wp10" rel="tooltip" onclick="advUp(weapons[this.id],id);">
			</a>
		</div>
		<div>
			<a href="#" id="wp20" rel="tooltip" onclick="advUp(weapons[this.id],id);">
			</a>
		</div>
		<div>
			<a href="#" id="wp30" rel="tooltip" onclick="advUp(weapons[this.id],id);">
			</a>
		</div>
		<div>
			<a href="#" id="wp40" rel="tooltip" onclick="advUp(weapons[this.id],id);">
			</a>
		</div>
		<div>
			<a href="#" id="wp50" rel="tooltip" onclick="advUp(weapons[this.id],id);">
			</a>
		</div>
		<div>
			<button id="wp_auto" onclick="autoAdvUp();">자동강화시작</button>
			<button id="wp_bless_1" class='wp_bless_1' onclick="bless_1();">축복1단계</button>
			<button id="wp_bless_2" class='wp_bless_2' onclick="bless_2();">축복2단계</button>
		</div>
	</section>
	<section id="chattings">
		<div  style="display: inline;">
			<textarea id="res_field" cols="60" rows="20" readonly="true" ></textarea>
		</div>		
	</section>
</BODY>
</HTML>