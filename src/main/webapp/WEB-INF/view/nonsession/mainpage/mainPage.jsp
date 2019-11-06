<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>   
    
<!DOCTYPE html>
<html>
	<head>
	
		<title>여행을 부탁해~</title>
		
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1.0" />
		<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
  		<meta name="author" content="Nathan Searles">
  		
		<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css?v=20190808_01" />
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css" />
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mainpage.css?v=20190808_01" />
			<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/post_script.css" />
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
		
			<!-- Drop Menu Header -->
				<jsp:include page="../layout/dropMenu_header.jsp" />
			<!-- Menu Bar Header -->
				<jsp:include page="../layout/menubar_header.jsp" />

			<!-- Main Body part -->
			
				<div id="page-wrapper" class="boardPage-Wrapper">
					<div id="main">
						<div class="container">
							<div class="row main-row">
							
								<!-- Image Slide Script & jQuery -->
								
								<script src="<%=request.getContextPath() %>/assets/js/jssor.slider-21.1.6.mini.js" type="text/javascript"></script>
								<script type="text/javascript">
							        jQuery(document).ready(function ($) {
							
							            var jssor_1_SlideoTransitions = [
							              [{b:0,d:600,y:-290,e:{y:27}}],
							              [{b:0,d:1000,y:185},{b:1000,d:500,o:-1},{b:1500,d:500,o:1},{b:2000,d:1500,r:360},{b:3500,d:1000,rX:30},{b:4500,d:500,rX:-30},{b:5000,d:1000,rY:30},{b:6000,d:500,rY:-30},{b:6500,d:500,sX:1},{b:7000,d:500,sX:-1},{b:7500,d:500,sY:1},{b:8000,d:500,sY:-1},{b:8500,d:500,kX:30},{b:9000,d:500,kX:-30},{b:9500,d:500,kY:30},{b:10000,d:500,kY:-30},{b:10500,d:500,c:{x:87.50,t:-87.50}},{b:11000,d:500,c:{x:-87.50,t:87.50}}],
							              [{b:0,d:600,x:410,e:{x:27}}],
							              [{b:-1,d:1,o:-1},{b:0,d:600,o:1,e:{o:5}}],
							              [{b:-1,d:1,c:{x:175.0,t:-175.0}},{b:0,d:800,c:{x:-175.0,t:175.0},e:{c:{x:7,t:7}}}],
							              [{b:-1,d:1,o:-1},{b:0,d:600,x:-570,o:1,e:{x:6}}],
							              [{b:-1,d:1,o:-1,r:-180},{b:0,d:800,o:1,r:180,e:{r:7}}],
							              [{b:0,d:1000,y:80,e:{y:24}},{b:1000,d:1100,x:570,y:170,o:-1,r:30,sX:9,sY:9,e:{x:2,y:6,r:1,sX:5,sY:5}}],
							              [{b:2000,d:600,rY:30}],
							              [{b:0,d:500,x:-105},{b:500,d:500,x:230},{b:1000,d:500,y:-120},{b:1500,d:500,x:-70,y:120},{b:2600,d:500,y:-80},{b:3100,d:900,y:160,e:{y:24}}],
							              [{b:0,d:1000,o:-0.4,rX:2,rY:1},{b:1000,d:1000,rY:1},{b:2000,d:1000,rX:-1},{b:3000,d:1000,rY:-1},{b:4000,d:1000,o:0.4,rX:-1,rY:-1}]
							            ];
							
							            var jssor_1_options = {
							              $AutoPlay: true,
							              $Idle: 2000,
							              $CaptionSliderOptions: {
							                $Class: $JssorCaptionSlideo$,
							                $Transitions: jssor_1_SlideoTransitions,
							                $Breaks: [
							                  [{d:2000,b:1000}]
							                ]
							              },
							              $ArrowNavigatorOptions: {
							                $Class: $JssorArrowNavigator$
							              },
							              $BulletNavigatorOptions: {
							                $Class: $JssorBulletNavigator$
							              }
							            };
							
							            var jssor_1_slider = new $JssorSlider$("jssor_1", jssor_1_options);
							
							            /*responsive code begin*/
							            /*you can remove responsive code if you don't want the slider scales while window resizing*/
							            function ScaleSlider() {
							                var refSize = jssor_1_slider.$Elmt.parentNode.clientWidth;
							                if (refSize) {
							                    refSize = Math.min(refSize, 1000);
							                    jssor_1_slider.$ScaleWidth(refSize);
							                }
							                else {
							                    window.setTimeout(ScaleSlider, 50);
							                }
							            }
							            ScaleSlider();
							            $(window).bind("load", ScaleSlider);
							            $(window).bind("resize", ScaleSlider);
							            $(window).bind("orientationchange", ScaleSlider);
							            /*responsive code end*/
							        });
							    </script>
								
								<!-- Image Slide body part -->
								<div class="1u"></div>
								<div class="10u real_main_page">
									<h3 class="part_title_h3 moba">오늘의 추천 후기</h3>
									<div class="moba" id="jssor_1" style="position: relative; margin: 0 auto; top: 0px; left: 0px; width: 600px; height: 300px; overflow: hidden; visibility: hidden;">
								        
								        <!-- Loading Screen -->
								        <div data-u="loading" style="position: absolute; top: 0px; left: 0px;">
								            <div style="filter: alpha(opacity=70); opacity: 0.7; position: absolute; display: block; top: 0px; left: 0px; width: 100%; height: 100%;"></div>
								            <div style="position:absolute;display:block;background:url('img/loading.gif') no-repeat center center;top:0px;left:0px;width:100%;height:100%;"></div>
								        </div>
								         <div data-u="slides" style="cursor: default; position: relative; top: 0px; left: 0px; width: 600px; height: 300px; overflow: hidden;">
								        <c:forEach var="review" items="${reviewTop3 }">
								            <div data-p="112.50">
								                <img data-u="image" src="/photo_upload/${review.reviewImage }" />
								                <div data-u="caption" data-t="0" style="position: absolute; top: 320px; left: 30px; width: 350px; height: 30px; background-color: rgba(235,81,0,0.5); font-size: 20px; color: #ffffff; line-height: 30px; text-align: center;">${review.reviewTitle }</div>
								            </div>
								        </c:forEach>
								        </div>
								        <!-- Bullet Navigator -->
								        <div data-u="navigator" class="jssorb01" style="bottom:16px;right:16px;">
								            <div data-u="prototype" style="width:12px;height:12px;"></div>
								        </div>
								        <!-- Arrow Navigator -->
								        <span data-u="arrowleft" class="jssora02l" style="top:0px;left:8px;width:55px;height:55px;" data-autocenter="2"></span>
								        <span data-u="arrowright" class="jssora02r" style="top:0px;right:8px;width:55px;height:55px;" data-autocenter="2"></span>
								    </div>
								    
								    
								    <!-- Main board view part -->
								    	<hr id="mainpage_divide_hr1 moba">
								    	<div id="mainpage_board_view">
								    		<h3 class="part_title_h3">게시판</h3>
								    		<section class="mainpage_board_features" style="padding-top: 0px">
								    			<article>
								    				<h4>공지사항</h4>
								    				<ul>
								    				<c:forEach var="notice" items="${noticeTop5 }">
								    					<li><a href="/noticeView?commentNo=${notice.commentNo }">- ${notice.commentName }</a></li>
								    				</c:forEach>
								    					<li>
								    						<input type="button" value="More" class="mainpageButtonStyle1">
								    					</li>
								    				</ul>
								    			</article>
								    			<article>
								    				<h4>Q & A</h4>
								    				<ul>
								    				<c:forEach var="qna" items="${qnaTop5 }">
									    				<li><a href="/qnaView?qnaNo=${qna.qnaNo }">${qna.qnaTitle }</a></li>
									    			</c:forEach>
									    				<li>
								    						<input type="button" value="More" class="mainpageButtonStyle1">
								    					</li>
								    				</ul>
								    			</article>
								    			<article>
								    				<h4>자유 게시판</h4>
								    				<ul>
								    				<c:forEach var="free" items="${free }">
								    					<li><a href="/freeView?commentNo=${free.commentNo }">- ${free.commentName }</a></li>
								    				</c:forEach>
								    					<li>
								    						<input type="button" value="More" class="mainpageButtonStyle1">
								    					</li>
								    				</ul>
								    			</article>
								    		</section>
								    	</div>
								    	
								    	<hr id="mainpage_divide_hr2 moba">
								    	<div class="moba" id="mainpage_route_view">
								    		<h3 class="part_title_h3">방금 등록된 일정</h3>
								    		<section class="mainpage_route_features" style="padding-top: 0px">
								    		<c:forEach items="${routeTop4}" var="route" end="3">
								    			<article>
								    				
								    				<ul>
								    					<li>
								    						<iframe src='/routeImage?routeNo=${ route.routeNo}'  
								    						height="300px" width="300px"  scrolling="no" frameborder="0">
								    						</iframe>
								    					</li>
								    					<li><h4>${route.routeName}<br>-${route.userNick}</h4></li>
								    				</ul>
								    			</article>
								    		</c:forEach>
								    		</section>
								    	</div>
								</div>
								
							</div>
						</div>
					</div>
				</div>
				
<%-- 
			<!-- Features -->
				<div class="wrapper style1">

					<section id="features" class="container special">
						<header>
							<h2>여기서 부터는 추천 후기 순으로 띄워주는 Part</h2>
							<p>우아아아앙 이거 구현하고 나면 header, footer 나누면 된다아아</p>
						</header>

				<!-- 추천 후기 4개 띄워주는 Code -->
					<section id="four" class="fancyWrapper alt fancyStyle1">
						<div class="fancyInner">
							<section class="fancyFeatures">
								<article>
									<a href="#" class="fancyImage"><img src="<%=request.getContextPath() %>/images/pics06.jpg" alt="" /></a>
									<h3 class="major">여행을 부탁해 경로 예시 1</h3>
									<p id="fancyPFont">Lorem ipsum dolor sit amet, consectetur adipiscing
										vehicula id nulla dignissim dapibus ultrices.</p>
									<a href="#" class="fancySpecial">Learn more</a>
								</article>
								<article>
									<a href="#" class="fancyImage"><img src="<%=request.getContextPath() %>/images/pics01.jpg" alt="" /></a>
									<h3 class="major">여행을 부탁해 경로 예시 2</h3>
									<p id="fancyPFont">Lorem ipsum dolor sit amet, consectetur adipiscing
										vehicula id nulla dignissim dapibus ultrices.</p>
									<a href="#" class="fancySpecial">Learn more</a>
								</article>
								<article>
									<a href="#" class="fancyImage"><img src="<%=request.getContextPath() %>/images/pics02.jpg" alt="" /></a>
									<h3 class="major">여행을 부탁해 경로 예시 3</h3>
									<p id="fancyPFont">Lorem ipsum dolor sit amet, consectetur adipiscing
										vehicula id nulla dignissim dapibus ultrices.</p>
									<a href="#" class="fancySpecial">Learn more</a>
								</article>
								<article>
									<a href="#" class="fancyImage"><img src="<%=request.getContextPath() %>/images/pics03.jpg" alt="" /></a>
									<h3 class="major">여행을 부탁해 경로 예시 4</h3>
									<p id="fancyPFont">Lorem ipsum dolor sit amet, consectetur adipiscing
										vehicula id nulla dignissim dapibus ultrices.</p>
									<a href="#" class="fancySpecial">Learn more</a>
								</article>
							</section>
							<ul class="fancyActions">
								<li><a href="#" class="fancyButton">Browse All</a></li>
							</ul>
						</div>
					</section>
				</section>

			</div>
 --%>
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