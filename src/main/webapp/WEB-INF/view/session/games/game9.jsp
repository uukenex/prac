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
<link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game8.css?v=<%=System.currentTimeMillis()%>" />
</HEAD>
<BODY onload="init()">

	
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://jsgetip.appspot.com"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
	var wp1 = {};
	wp1.wp_id = 'wb001';
	wp1.wp_name = '초보자무기';
	wp1.wp_desc = '초보자용 무기이다.';
	wp1.wp_org_damage = 2;
	wp1.wp_cur_damage = 2;
	wp1.wp_adv_no = 0;
	
	var wp2 = {};
	wp2.wp_id = 'wb002';
	wp2.wp_name = '숙련자무기';
	wp2.wp_desc = '숙련자용 무기이다.';
	wp2.wp_org_damage = 3;
	wp2.wp_cur_damage = 3;
	wp2.wp_adv_no = 0;
	
	var wp3 = {};
	wp3.wp_id = 'wb003';
	wp3.wp_name = '전설무기';
	wp3.wp_desc = '전설 무기이다.';
	wp3.wp_org_damage = 200;
	wp3.wp_cur_damage = 200;
	wp3.wp_adv_no = 0;
	
	function init()
    {
		
    }
	
	function advCalc(wp){
		//n강 까지는 현재데미지 = 무기원데미지 * 강화수치
		var n_enhance_value = 6;
		var n2_enhance_value = 10;
		
		var DPoint = wp.wp_org_damage.toString().length;
		var DPos = 1;
		switch(DPoint){
			case 1:
				DPos = 1;
				break;
			case 2:
				DPos = 10;
				break;
			case 3:
				DPos = 100;
				break;
			case 4:
				DPos = 1000;
				break;
		}
		
		var ret;
		if(wp.wp_adv_no < n_enhance_value)
			ret = wp.wp_org_damage + Math.floor(wp.wp_org_damage/DPos)*DPos*(wp.wp_adv_no-1);
		if(wp.wp_adv_no >= n_enhance_value && wp.wp_adv_no < n2_enhance_value)
			ret = wp.wp_org_damage + Math.floor(wp.wp_org_damage/DPos)*DPos*(wp.wp_adv_no-1)*1.1;
		if(wp.wp_adv_no >= n2_enhance_value)
			ret = wp.wp_org_damage + Math.floor(wp.wp_org_damage/DPos)*DPos*(wp.wp_adv_no-1)*1.3;
		
		return Math.floor(ret);
	}
	
	
	function advUp(wp){
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_cur_damage;
		
		wp.wp_adv_no++;
		wp.wp_cur_damage = advCalc(wp);
		
		var aft_adv_no = wp.wp_adv_no;
		var aft_damage = wp.wp_cur_damage;
		
		console.log('데미지상승');
		console.log(bef_adv_no+'강, 데미지: ' +bef_damage + ' => ' + aft_adv_no+'강, 데미지: ' + aft_damage  );
	}
	</script>
	
	<section id="space" class='space'>
		<input type="button" value="일반" onclick="advUp(wp1);">
		<input type="button" value="특수" onclick="advUp(wp2);">
		<input type="button" value="전설" onclick="advUp(wp3);">
	</section>
	
	
	
</BODY>
</HTML>