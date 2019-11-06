<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/post_script.css">
<title>사이트 이용방법</title>
<style>
img{
border:2px solid #f56a6a;
}

@-webkit-keyframes fadeEffect {
    from {opacity: 0;}
    to {opacity: 1;}
}

@keyframes fadeEffect {
    from {opacity: 0;}
    to {opacity: 1;}
}

</style>
</head>
<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../nonsession/layout/menubar_header.jsp" />
		
	<!-- How to use this site body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<div class="row main-row">
					
						<!-- Board Left Menu -->
						<jsp:include page="../nonsession/layout/board_left_menu.jsp"></jsp:include>
						
						<div class="8u how_using_div">
							<h3>사이트 이용방법</h3>
							<ul class="using_tab">
								<li>
									<a href="javascript:void(0)" class="using_tablinks"
										onclick="openUsing(event, 'using1')">
										일정 만들기1
									</a>
								</li>
								<li>
									<a href="javascript:void(0)" class="using_tablinks"
										onclick="openUsing(event, 'using2')">
										일정 만들기2
									</a>
								</li>
								<li>
									<a href="javascript:void(0)" class="using_tablinks"
										onclick="openUsing(event, 'using3')">
										일정 만들기3
									</a>
								</li>
								<li>
									<a href="javascript:void(0)" class="using_tablinks"
										onclick="openUsing(event, 'using4')">
										포토북1
									</a>
								</li>
								<li>
									<a href="javascript:void(0)" class="using_tablinks"
										onclick="openUsing(event, 'using5')">
										포토북2
									</a>
								</li>
							</ul>
							
							<div id="using1" class="using_tabcontent">
								<h4>일정 만들기1</h4>
								<ul>
									<li><img src="/images/help/using01.png" ></li>
								</ul>
							</div>
							
							<div id="using2" class="using_tabcontent">
								<h4>일정 만들기2</h4>
								<ul>
									<li><img src="/images/help/using02.png"></li>
								</ul>
							</div>
							
							<div id="using3" class="using_tabcontent">
								<h4>일정 만들기3</h4>
								<ul>
									<li><img src="/images/help/using03.png"></li>
									
								</ul>
							</div>
							
							<div id="using4" class="using_tabcontent">
								<h4>포토북1</h4>
								<ul>
									<li><img src="/images/help/using04.png"></li>
								</ul>
							</div>
							
							<div id="using5" class="using_tabcontent">
								<h4>포토북2</h4>
								<ul>
									<li><img src="/images/help/using05.png"></li>
								</ul>
							</div>
							
						</div>
					</div>
					
					<!-- footer -->
						<jsp:include page="../nonsession/layout/footer.jsp"></jsp:include>
				</div>
			</div>
		</div>

</body>


<script>
	function openUsing(evt, using_number) {
	    var i, tabcontent, tablinks;
	    tabcontent = document.getElementsByClassName("using_tabcontent");
	    for (i = 0; i < tabcontent.length; i++) {
	        tabcontent[i].style.display = "none";
	    }
	    tablinks = document.getElementsByClassName("using_tablinks");
	    for (i = 0; i < tablinks.length; i++) {
	        tablinks[i].className = tablinks[i].className.replace(" active", "");
	    }
	    document.getElementById(using_number).style.display = "block";
	    evt.currentTarget.className += " active";
	}

</script>

<script>
		var beforeBoard, nowBoard;
		beforeBoard = document.getElementById("current");
		beforeBoard.id = beforeBoard.id.replace("");
		$(".howuseBoard").attr('id', 'current');
	</script>
</html>