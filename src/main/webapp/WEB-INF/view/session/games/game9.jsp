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
	<script src="<%=request.getContextPath()%>/game_set/js/game_items.js?v=<%=System.currentTimeMillis()%>"></script>
	<script language="javascript">
	
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
		if(wp.wp_adv_no > 14){
			alert('15단계까지 구현되었습니다..!!');
			return;
		}
		var org_damage = wp.wp_org_damage;
		
		var bef_adv_no = wp.wp_adv_no;
		var bef_damage = wp.wp_add_damage;
		
		wp.wp_adv_no++;
		wp.wp_add_damage = advCalc(wp);
		
		var aft_adv_no = wp.wp_adv_no;
		var aft_damage = wp.wp_add_damage;
		
		var msg =          bef_adv_no+'강, 데미지: ' +org_damage+' ( + '+ bef_damage +' )' ;
		    msg+= ' => ' + aft_adv_no+'강, 데미지: ' +org_damage+' ( + '+ aft_damage +' )\n';
		
		$('#res_field').val( $('#res_field').val() + msg );
		$("#res_field")[0].scrollTop = $("#res_field")[0].scrollHeight;
	}
	</script>
	
	<section id="space" class='space'>
		<div>
			<a href="#" onclick="advUp(wp1);"><img src="<%=request.getContextPath()%>/game_set/img/item_1.png?v=<%=System.currentTimeMillis()%>">
			<input type="button" value="일반강화">
			</a>
			<p id="wp1-desc">
			<script>
			var txt = wp1.wp_name+'</br>';
			    txt+= wp1.wp_desc+'</br>';
			    txt+= '기본데미지:'+wp1.wp_org_damage+'</br>';
			$('#wp1-desc').html(txt);
			</script> 
			</p>
		</div>
		<div>
			<a href="#" onclick="advUp(wp2);"><img src="<%=request.getContextPath()%>/game_set/img/item_2.png?v=<%=System.currentTimeMillis()%>">
			<input type="button" value="매직강화">
			</a>
			<p id="wp2-desc">
			<script>
			var txt = wp2.wp_name+'</br>';
			    txt+= wp2.wp_desc+'</br>';
			    txt+= '기본데미지:'+wp2.wp_org_damage+'</br>';
			$('#wp2-desc').html(txt);
			</script> 
			</p>
		</div>
		<div>
			<a href="#" onclick="advUp(wp3);"><img src="<%=request.getContextPath()%>/game_set/img/item_3.png?v=<%=System.currentTimeMillis()%>">
			<input type="button" value="전설강화">
			</a>
			<p id="wp3-desc">
			<script>
			var txt = wp3.wp_name+'</br>';
			    txt+= wp3.wp_desc+'</br>';
			    txt+= '기본데미지:'+wp3.wp_org_damage+'</br>';
			$('#wp3-desc').html(txt);
			</script> 
			</p>
		</div>
	</section>
	<section id="chattings">
		<div  style="display: inline;">
			<textarea id="res_field" cols="60" rows="10"></textarea>
		</div>		
	</section>
</BODY>
</HTML>