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
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/game_enhance.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	var wp1 = {};
	wp1.wp_id = 'wb001';
	wp1.wp_name = '초보자무기';
	wp1.wp_desc = '초보자용 무기이다.';
	wp1.wp_org_damage = 10;
	wp1.wp_add_damage = 0;
	wp1.wp_adv_no = 0;
	
	var wp2 = {};
	wp2.wp_id = 'wb002';
	wp2.wp_name = '숙련자무기';
	wp2.wp_desc = '숙련자용 무기이다.';
	wp2.wp_org_damage = 20;
	wp2.wp_add_damage = 0;
	wp2.wp_adv_no = 0;
	
	var wp3 = {};
	wp3.wp_id = 'wb003';
	wp3.wp_name = '전설무기';
	wp3.wp_desc = '전설 무기이다.';
	wp3.wp_org_damage = 200;
	wp3.wp_add_damage = 0;
	wp3.wp_adv_no = 0;
	
	function init()
    {
		
    }
	
	function advCalc(wp){
		//강화 계수표 참조
		var DPos = fun_dPoing(wp.wp_org_damage);
		
		wp.wp_add_damage = wp.wp_org_damage * advance.getAdvVal(wp.wp_adv_no);
		
		return Math.floor(wp.wp_add_damage);
		
	}
	
	
	function advUp(wp){
		if(wp.wp_adv_no > 15){
			alert('15단계까지 구현되었습니다..!');
		}
		var org_damage = wp.wp_org_damage;
		
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_add_damage;
		
		wp.wp_adv_no++;
		wp.wp_add_damage = advCalc(wp);
		
		var aft_adv_no = wp.wp_adv_no;
		var aft_damage = wp.wp_add_damage;
		
		var msg = bef_adv_no+'강, 데미지: ' +bef_damage + ' => ' + aft_adv_no+'강, 데미지: ' +org_damage+' ( + '+ aft_damage +' )\n';
		
		$('#res_field').val( $('#res_field').val() + msg );
		$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
	}
	</script>
	
	<section id="space" class='space'>
		<div>
			<input type="button" value="일반" onclick="advUp(wp1);">
			<input type="button" value="특수" onclick="advUp(wp2);">
			<input type="button" value="전설" onclick="advUp(wp3);">
		</div>
	</section>
	<section id="chattings">
		<div  style="display: inline;">
			<textarea id="res_field" cols="60" rows="6"></textarea>
		</div>		
	</section>
</BODY>
</HTML>