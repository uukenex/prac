<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
 	<meta name="author" content="Nathan Searles">
	<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
	<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/main.css?v=<%=System.currentTimeMillis() %>" />
	<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/fancy.css?v=<%=System.currentTimeMillis() %>" />
	<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
	<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css?v=1.0">
<title>자유게시판 글쓰기</title>
</head>

<body>
	<script type="text/javascript" src="//code.jquery.com/jquery-1.11.0.min.js"></script>
	<script type="text/javascript" src="<%=request.getContextPath()%>/se2/js/HuskyEZCreator.js?v=1.0" charset="utf-8"></script>
	
	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">
					<!-- Board body part -->
						<div class="8u 12u(mobile) important(mobile)">
							<section class="middle-content">
								<h3>공유 게시판 작성</h3>
								<form action="/shareWrite" method="post" id="frm">
									<table class="boardEditorTable" >
										<colgroup>
											<col width="5%">
											<col width="*%">
										</colgroup>
										<tr>
											<td></td>
											<td class="inputTitle"><input type="text"  name="title" id="editorTitleWritter" 
															placeholder="제목을 입력해주세요"></td>
										</tr>
										<tr>
											<td colspan="2">
												<textarea name="content" id="content"
												 rows="9" cols="100" style="width:100%; 
												 height:412px; min-width:200px; display:none;"
												 class="editorContentWritter"></textarea>
											</td>
										</tr>
										<tr>
											<td colspan="2" class="editorButtonTd">
												<input type="button" id="savebutton" class="editorButtonStyle1" value="완료" />
     											<input type="button" name="Submit2" class="editorButtonStyle1" value="취소" onclick="history.back();">
											</td>
										</tr>
									</table>
								</form>
							</section>
						</div>
				</div>
				
				<!-- footer -->
					<%-- <jsp:include page="../../nonsession/layout/footer.jsp" /> --%>
			</div>
		</div>
	</div>

	
<script>
function resize_Image(){
	 resize_X=event.x;
	 resize_Y=event.y;
	 event.srcElement.width=resize_X;
	 event.srcElement.height=resize_Y;
	}

$(function(){
    //전역변수선언
    var editor_object = [];
     
    nhn.husky.EZCreator.createInIFrame({
        oAppRef: editor_object,
        elPlaceHolder: "content",
        sSkinURI: "/se2/SmartEditor2Skin.html?v=3", 
        htParams : {
            // 툴바 사용 여부 (true:사용/ false:사용하지 않음)
            bUseToolbar : true,             
            // 입력창 크기 조절바 사용 여부 (true:사용/ false:사용하지 않음)
            bUseVerticalResizer : false,     
            // 모드 탭(Editor | HTML | TEXT) 사용 여부 (true:사용/ false:사용하지 않음)
            bUseModeChanger : false, 
        }
    });
     
    //전송버튼 클릭이벤트
    $("#savebutton").click(function(){
        //id가 content인 textarea에 에디터에서 대입
        editor_object.getById["content"].exec("UPDATE_CONTENTS_FIELD", []);
         
        // 이부분에 에디터 validation 검증
         if($("#editorTitleWritter").val()==""){
        	 alert("제목을 입력해주세요.");
         }else{
      	  //폼 submit
     	   $("#frm").submit();
       	 }
    })
})
</script>

<!-- <script>
		var beforeBoard, nowBoard;
		beforeBoard = document.getElementById("current");
		beforeBoard.id = beforeBoard.id.replace("");
		$(".freeBoard").attr('id', 'current');
	</script>
 -->
</body>
</html>