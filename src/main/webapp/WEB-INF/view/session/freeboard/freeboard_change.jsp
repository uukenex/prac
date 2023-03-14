<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
    
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="sform" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

<meta name="viewport" content="width=device-width, initial-scale=1" />
	<meta name="description" content="SlidesJS is a simple slideshow plugin for jQuery. Packed with a useful set of features to help novice and advanced developers alike create elegant and user-friendly slideshows.">
 	<meta name="author" content="Nathan Searles">
  		
		<!--[if lte IE 8]><script src="assets/js/ie/html5shiv.js"></script><![endif]-->
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/main.css?v=1.0" />
		<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/fancy.css?v=1.0" />
		<!--[if lte IE 8]><link rel="stylesheet" href="assets/css/ie8.css" /><![endif]-->
		
		<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/css/font-awesome.min.css?v=1.0">
		
<title>${comment.commentName} 수정</title>

<script type="text/javascript" src="//code.jquery.com/jquery-1.11.0.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/se2/js/HuskyEZCreator.js?v=<%=System.currentTimeMillis()%>" charset="utf-8"></script>
<!-- jqeury 이후 호출 -->
<script src="<%=request.getContextPath()%>/game_set/js/comutil.js?v=<%=System.currentTimeMillis()%>"></script>
</head>

<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
	<div id="page-wrapper" class="boardPage-Wrapper">
		<div id="main">
			<div class="container">
				<div class="row main-row">
					
					<!-- Board Left Menu -->
						<%-- <jsp:include page="../../nonsession/layout/board_left_menu.jsp" /> --%>
						
					<!-- Board body part -->
						<div class="10u -1u 12u(mobile) important(moblie)">
							<section class="middle-content">
								<h3>${comment.commentName} 수정</h3>
								<form action="/freeUpdate" method="post" id="frm">
									<table class="boardEditorTable">
										<colgroup>
											<col width="5%">
											<col width="*%">
										</colgroup>
										<tr>
											<td></td>
											<input type="hidden" name="commentNo" value="${comment.commentNo }" />
											<td class="inputTitle"><input type="text" name="title" id="editorTitleWritter"
														value="${comment.commentName}">
											</td>
										</tr>
										<tr>
											<td colspan="2">
												<textarea name="content" id="content" 
												 rows="9" cols="100" style="width:100%; 
												 height:412px; min-width:200px; display:none;"
												 class="editorContentWritter">
													${comment.commentContent }
												</textarea>
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
			</div>
		</div>
	</div>
	
<script>
$(function(){
    //전역변수선언
    var editor_object = [];
     
    nhn.husky.EZCreator.createInIFrame({
        oAppRef: editor_object,
        elPlaceHolder: "content",
        sSkinURI: "/se2/SmartEditor2Skin.html?v=8", 
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
     	  $("#content").val(base64toFile($("#content").val()));
    	   $("#frm").submit();
      	}
    })
    
    function base64toFile(content) {

        // 스마트 에디터에서 content 값이 "~~"와 같은 text 타입으로 넘어온다.
        let div = document.createElement("div");
        div.innerHTML = content;
        var base64Images = div.querySelectorAll("img"); // img 태그 추출
        
        var set = new Set();
        base64Images.forEach( v=> set.add(v.src));
        
        let imgFiles = [];
        
        for (const value of set) {
        	if(value.startsWith("data:")) {
                let arr = src.split(',');
                let mime = arr[0].match(/:(.*?);/)[1];
                let bstr = atob(arr[1]);
                let n = bstr.length;
                let u8arr = new Uint8Array(n);
                
                while(n--) {
                	u8arr[n] = bstr.charCodeAt(n);
                }
                
                let imgFile = new File([u8arr], "image", {type: mime});
                imgFiles.push(imgFile)
            } 
       	}
        
        let fdata = new FormData();
        let index = 0;
        imgFiles.forEach(e => {
        	fdata.append("file"+index, imgFiles[index])
            index++;
        })
        fdata.append("length", imgFiles.length)

        $.ajax({
        	url: "/base64imgUpload"
            , data: fdata
            , method: "POST"
            , enctype: "multipart/form-data; charset=utf-8"
            , processData: false
            , contentType: false
            , cache: false
            , async: false
            , success: function(data) {
            	if(data) {
                	let resultFiles = data;
                    for(let i = 0; i<resultFiles.length; i++) {
                    	replaceAll(con,base64Images[i].src, "/imgServer/"+resultFiles[i]);
                    	//content = content.replace(base64Images[i].src, "/imgServer/"+resultFiles[i]);
                    }
                } else {
                	alert('이미지 업로드 실패');
                }
            }, error: function(data) {
            	alert('이미지 업로드 실패');
            }
        })

        return content;
    }
    
    function replaceAll(str, searchStr, replaceStr) {
   	   return str.split(searchStr).join(replaceStr);
   	}
    
});
</script>

<!-- <script>
		var beforeBoard, nowBoard;
		beforeBoard = document.getElementById("current");
		beforeBoard.id = beforeBoard.id.replace("");
		$(".freeBoard").attr('id', 'current');
	</script> -->
</body>
</html>