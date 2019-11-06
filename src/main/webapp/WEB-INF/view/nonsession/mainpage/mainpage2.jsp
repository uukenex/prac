<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
	<head>
	
		<title>여행을 부탁해~</title>
		
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1" />
		<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
  		<meta name="author" content="Nathan Searles">
  		
		<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css" />
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
		<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
		
		<!-- jQuery Fancy Box Style Sheet -->
			<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox.css" media="screen" />
			<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox-buttons.css" />
			<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox-thumbs.css" />
		
		<!-- Font Icon Style -->
			<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css">
	
	</head>
	<body class="homepage">
		<div id="page-wrapper">
		
			<!-- Fancy Header -->
			<jsp:include page="../layout/dropMenu_header.jsp"></jsp:include>

			<!-- Header -->
				<div id="header" class="drop_menu_visiable">
					
					<!-- Inner -->
						<div class="inner">
							<header>
								<h1 id="h1title"><a href="/mainpage" id="logo">여행을 부탁해</a></h1>
								<hr />
								<p>국내 여행자들을 위한 Travel Solution</p>
							</header>
							<footer>
								<a href="#features" class="button circled scrolly">이용방법</a>
								<a href="/mainpage2" class="button circled scrolly">시작</a>
							</footer>
						</div>

					<!-- Nav -->
						<jsp:include page="../layout/menubar_header.jsp"></jsp:include>

				</div>
  				
			<!-- Features -->
				<div class="wrapper style1">

					<section id="features" class="container special">
						<header>
							<jsp:include page="../../help/help.jsp"></jsp:include>
						</header>
				</section>

			</div>

		<!-- Footer -->
				<jsp:include page="../layout/footer.jsp"></jsp:include>
		</div>

		
  			
  			<!-- SlidesJS Required: Initialize SlidesJS with a jQuery doc ready -->
  				<script>
    				$(function() {
      					$('#slides').slidesjs({
        					width: 940,
       						height: 528,
       						navigation: false,
        					start: 1,
        					pagination: {
        						active: true
        					},
        					play: {
        						active: true,
        						auto: true,
        						interval: 4000,
        						swap: true
        					}
     					});
   					});
  				</script>
  			<!-- End SlidesJS Required -->
  			
  			

	</body>
</html>