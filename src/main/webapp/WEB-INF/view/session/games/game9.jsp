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
	
	function init()
    {
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
		
	    $('#wp10').append('<img src="<%=request.getContextPath()%>/game_set/img/'+weapons["wp10"].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
	    $('#wp10').append('<input type="button" value="+0">');
	    
	    $('#wp20').append('<img src="<%=request.getContextPath()%>/game_set/img/'+weapons["wp20"].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
	    $('#wp20').append('<input type="button" value="+0">');
	    
	    $('#wp30').append('<img src="<%=request.getContextPath()%>/game_set/img/'+weapons["wp30"].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
	    $('#wp30').append('<input type="button" value="+0">');
	    
	    $('#wp40').append('<img src="<%=request.getContextPath()%>/game_set/img/'+weapons["wp40"].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
	    $('#wp40').append('<input type="button" value="+0">');
	    
	    $('#wp50').append('<img src="<%=request.getContextPath()%>/game_set/img/'+weapons["wp50"].wp_img+'?v=<%=System.currentTimeMillis()%>"></img>');
	    $('#wp50').append('<input type="button" value="+0">');
   	}
	
	function advCalc(wp){
		//강화 계수표 참조
		var DPos = fun_dPoing(wp.wp_org_damage);
		
		wp.wp_add_damage = wp.wp_org_damage * advance.getAdvVal(wp.wp_adv_no);
		
		return Math.floor(wp.wp_add_damage);
		
	}
	
	
	function advUp(wp,tag_id){
		var res;
		if(wp.wp_adv_no > 14){
			alert('15단계까지 구현되었습니다..!!');
			return;
		}
		var org_damage = wp.wp_org_damage;
		
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_add_damage;
		
		var adv_rate =advance_rate.getAdvRateVal(wp.wp_adv_no); 
		
		//1, 100을 포함한 수 중 랜덤
		//10강화시 80 <= 30 (31~100::70퍼센트로 실패)
		if(getRandomInt(1,100) <= adv_rate){
			wp.wp_adv_no++;	
			res=' 강화에 성공하였습니다.';
		}else{
			wp.wp_adv_no--;
			res=' 강화에 실패하였습니다.';
		}
		
		wp.wp_add_damage = advCalc(wp);
		
		var aft_adv_no = wp.wp_adv_no;
		var aft_damage = wp.wp_add_damage;
		var aft_adv_rate =advance_rate.getAdvRateVal(wp.wp_adv_no+1);
		
		var msg = wp.wp_name +' +'+ (bef_adv_no+1) + res + '\n'
			msg+=          bef_adv_no+'강, 데미지: ' +org_damage+' ( + '+ bef_damage +' )' ;
		    msg+= ' => ' + aft_adv_no+'강, 데미지: ' +org_damage+' ( + '+ aft_damage +' )\n\n';
		
		$('#' + tag_id + '> input').val('+' + aft_adv_no + '.다음확률: ' + aft_adv_rate+'%');
		$('#res_field').val( $('#res_field').val() + msg );
		$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
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
	</section>
	<section id="chattings">
		<div  style="display: inline;">
			<textarea id="res_field" cols="60" rows="20" readonly="true" ></textarea>
		</div>		
	</section>
</BODY>
</HTML>