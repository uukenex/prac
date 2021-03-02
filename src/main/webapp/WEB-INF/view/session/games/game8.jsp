<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>dev</TITLE>
<%-- <link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" /> --%>
</HEAD>
<style>
.drag {
	position: relative;
	cursor: hand;
	z-index: 100;
	width: 200px;
	height: 200px;
}
</style>
<BODY onload="init()">
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<%-- <script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script> --%>
	<script language="javascript">
		function init() {
			
			
		}

		var dragobject = {
			z : 0,
			x : 0,
			y : 0,
			offsetx : null,
			offsety : null,
			targetobj : null,
			dragapproved : 0,
			mode : null,//touch or mouse mode
			initialize : function() {
				document.onmousedown = this.drag;
				document.onmouseup = function() {
					this.dragapproved = 0;
					this.targetobj.src = "<%=request.getContextPath()%>/game_set/img/game8_img1.gif?v=<%=System.currentTimeMillis()%>";
				}
				
				document.ontouchstart = this.drag; //동일
				document.ontouchend = function() {
					this.dragapproved = 0;
					this.targetobj.src = "<%=request.getContextPath()%>/game_set/img/game8_img1.gif?v=<%=System.currentTimeMillis()%>";
				}
				
				
			},
			drag : function(e) {
				var evtobj = window.event ? window.event : e;
				this.targetobj = window.event ? event.srcElement : e.target;
				
				if(evtobj.type=='touchstart'){
					this.mode = 'touch';
				}else if(evtobj.type=='mousedown'){
					this.mode = 'mouse';
				}			
				
				if (this.targetobj.className == "drag") {
					this.dragapproved = 1;
					if (isNaN(parseInt(this.targetobj.style.left))) {
						this.targetobj.style.left = 0;
					}
					if (isNaN(parseInt(this.targetobj.style.top))) {
						this.targetobj.style.top = 0;
					}
					
					if(this.dragapproved == 1){
						this.targetobj.src = "<%=request.getContextPath()%>/game_set/img/game8_img2.gif?v=<%=System.currentTimeMillis()%>";
					}
					
					this.offsetx = parseInt(this.targetobj.style.left);
					this.offsety = parseInt(this.targetobj.style.top);
					
					if(this.mode == 'mouse'){
						this.x = evtobj.clientX;
						this.y = evtobj.clientY;	
						if (evtobj.preventDefault){
							evtobj.preventDefault();
						}
						document.addEventListener("mousemove", dragobject.moveit, {passive: false} );
					}else if(this.mode =='touch'){
						this.x = evtobj.touches[0].clientX;
						this.y = evtobj.touches[0].clientY;
						if (evtobj.touches[0].preventDefault){
							evtobj.touches[0].preventDefault();
						}
						document.addEventListener("touchmove", dragobject.moveit, {passive: false} );
					}
				}
			},
			
			moveit : function(e){
				var evtobj = window.event ? window.event : e;
				if (this.dragapproved == 1) {
					if(this.mode == 'mouse'){
						this.targetobj.style.left = this.offsetx + evtobj.clientX - this.x + "px";
						this.targetobj.style.top = this.offsety + evtobj.clientY - this.y + "px";
					}else if(this.mode =='touch'){
						this.targetobj.style.left = this.offsetx + e.touches[0].clientX - this.x + "px"; 
						this.targetobj.style.top = this.offsety + e.touches[0].clientY - this.y + "px"; 
					}
					return false;
				}
			}
			
		}

		dragobject.initialize();
	</script>


	<IMG class=drag	src="<%=request.getContextPath()%>/game_set/img/game8_img1.gif?v=<%=System.currentTimeMillis()%>" id = "d1" >
	<IMG class=drag	src="<%=request.getContextPath()%>/game_set/img/game8_img1.gif?v=<%=System.currentTimeMillis()%>" id = "d2" >
	<IMG class=drag src="<%=request.getContextPath()%>/game_set/img/game8_img1.gif?v=<%=System.currentTimeMillis()%>" id = "d3" >

</BODY>
</HTML>