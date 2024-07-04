<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>${Users.userId }님의 포토북</title>
<style type="text/css">

#frm label {
  display: inline-block;
  padding: .5em .75em;
  color: #fff;
  font-size: inherit;
  line-height: normal;
  vertical-align: middle;
  background-color: #2e6da4;
  cursor: pointer;
  border: 1px solid #2e6da4;
  border-bottom-color: #e2e2e2;
  border-radius: .25em;
}

 #frm input[type="file"],#frm input[type="button"] {  /* 파일 필드 숨기기 */
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip:rect(0,0,0,0);
  border: 0;
} 

img[id^=chk]{
position: relative;
	left: -50px; /* -이미지의 크기 x 갯수 */
	top:-175px; /* -(버튼의크기-이미지의크기) */
}
img[id^=down]{
position: relative;
	left: -50px; /* -이미지의 크기 x 갯수  */
	top:-175px; /* -(버튼의크기-이미지의크기) */
}

.outer {
position: relative;
width: 250px; /* 너비 설정*/
height: 200px; /* 높이 설정*/
display: inline-block;
}
.bottomleft {
position: absolute;
bottom: 0;
left: 0.5em;
width: 200px;
text-align : center;
font-weight: bold;
color: #000;
display: inline;
}
.folderInline{
display: inline-block;
z-index: 1;
}
</style>
</head>
<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
		
	<!-- Photo Book Body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
					<div class="row main-row" id="photoBook-wrapper">
						
						<c:set value='<%=request.getParameter("userId") %>' var="curUserId"></c:set>
						<c:set var="folderName" value='<%=request.getParameter("folderName") %>'></c:set>
						
						<%-- 루트폴더 처리 --%>
						<c:if test="${folderName=='..' }" >
						 	<c:set var="folderName" value='.'></c:set>
						</c:if>
						
						<%-- 현재접속아이디(*Users.userId) 와 들어가려는 아이디(*curUserId)가 같은경우에만 수정이가능함 --%>
						<c:if test='${Users.userId==curUserId || !empty shareFolder }'> 
						<h3 class="photobookUser">${curUserId }님의 포토북</h3>
							<c:url value="/photo" var="photo" />
							<form id="frm">
						    <p>
						    <br />
						     <c:if test="${!(empty folderName|| folderName=='.')}">
						        <input type="file" accept="image/*" id="fileLoader" multiple="multiple" />
						        <br />
						        <label for="fileLoader" class="labeling1">사진 올리기</label>
						        
						        <input type="text" id="cntFiles" style="display: inline; width: 20em;" readonly="readonly"/>
						        <br/>
						   
								<input type="button" id="fileSubmit" value="Upload"/>
								<label for="fileSubmit" class="labeling1">저장</label>
							 </c:if>
								<c:if test="${empty folderName || folderName=='.' }">
								<input type="button" id="newFolder" value="새폴더"/>
								<label for="newFolder" class="labeling2">새폴더</label>
								</c:if>
						    </p>
						    </form>
						   
						    <output class="12u" id="result"></output>
						</c:if>
						
						<c:if test="${Users.userId!=curUserId&&empty shareFolder  }">
							<div class="12u shareGrantInfo">
								<h1><i class="fa fa-times-circle-o"></i></h1>
								<h2><i class="fa fa-share-alt"></i> 권한이 없습니다. <i class="fa fa-share-alt"></i></h2>
								<input type="button" value="뒤로가기" class="labeling1" onclick="location.href='/mainpage2'">
							</div>
						</c:if>
						
					</div>
					
					<!-- footer -->
						<jsp:include page="../../nonsession/layout/footer.jsp" />
				</div>
			</div>
		</div>
				
<%-- 				
<div style="height: 5em;"></div>




<c:set value='<%=request.getParameter("userId") %>' var="curUserId"></c:set>
<c:set var="folderName" value='<%=request.getParameter("folderName") %>'></c:set>

루트폴더 처리
<c:if test="${folderName=='..' }" >
 	<c:set var="folderName" value='.'></c:set>
</c:if>

현재접속아이디(*Users.userId) 와 들어가려는 아이디(*curUserId)가 같은경우에만 수정이가능함
<c:if test='${Users.userId==curUserId || !empty shareFolder }'> 
<h3>${curUserId }의 포토북</h3>
	<c:url value="/photo" var="photo" />
	<form id="frm">
    <p>
        <br />
        <input type="file" accept="image/*" id="fileLoader" multiple="multiple" />
        <label for="fileLoader" >사진 올리기</label>
        
        <input type="text" id="cntFiles" style="display: inline; width: 200px;" readonly="readonly"/>
		<input type="button" id="fileSubmit" value="Upload"/>
		<label for="fileSubmit">저장</label>
		<c:if test="${empty folderName || folderName=='.' }">
		<input type="button" id="newFolder" value="새폴더"/>
		<label for="newFolder">새폴더</label>
		</c:if>
    </p>
    </form>
    <output id="result"></output>
</c:if>
<c:if test="${Users.userId!=curUserId && empty shareFolder  }">
<div style="height: 5em;"></div> 
	<div style="text-align: center;">
	<h1>권한이 없습니다. 공유신청을 하세요!</h1>
	<input type="button" value="사진폴더 공유신청하기">
	</div>
</c:if> --%>
</body>
<script src="http://code.jquery.com/jquery.js"></script>
<script src="http://malsup.github.com/jquery.form.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/assets/js/jquery.mousewheel-3.0.6.pack.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/assets/js/jquery.fancybox.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/assets/js/jquery.fancybox-buttons.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/assets/js/jquery.fancybox-thumbs.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/assets/js/jquery.fancybox-media.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/js/jszip.min.js"></script>
<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox.css" media="screen" />
<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox-buttons.css" />
<link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/assets/css/jquery.fancybox-thumbs.css" />
<script>
<c:if test="${Users.userId==curUserId ||!empty shareFolder }"> 



//팬시박스
$(document).ready(function() {
	$(".fancybox").fancybox({
		closeEffect	: 'elastic',
    	helpers : {
    		title : {
    			type : 'inside'
    		}
    	}
	});
	
});

var files = [];
var interval=0;




//등록중 처리
$(document).on("change","#fileLoader",function(event) {
                 files=event.target.files;
                $("#cntFiles").val(files.length+"개 등록중");
                });

//사진등록 ajax
$(document).on("click","#fileSubmit",function() {
                processUpload();
                $("#cntFiles").val("등록완료");
                });
                
//삭제처리
<c:url value="/delete" var="delete" />
$(document).on("click",".close",function() {
	//글씨나 div를 넣어주는경우 한번씩 더실행할것
	var delImg0 = this.previousSibling.previousSibling;
	var delImg1 = this.previousSibling;
	var delImg2 = this;
	if(confirm('정말 삭제하시겠습니까?')==true)
		{
		
		//여기서 ajax 삭제처리 해주어야함
		console.log(this.previousSibling.previousSibling);
		var pathname=this.previousSibling.previousSibling.pathname;
		$.ajax({
			type:"post",
			url:"${delete}",
			data:{
				pathname:pathname
			},
			success:function(res){
				if(res==1){
					console.log('삭제성공');
					delImg0.remove();
					delImg1.remove();
					delImg2.remove();
				}
				else{
					console.log('삭제실패');
				}
			}
			,
			error:function(xhr,status,error){
				console.log('ajax연결실패')
			}
		});
		
		
			
		}
	else{
		return;
		}
    });

//폴더 삭제처리
<c:url value="/deleteFolder" var="deleteFolder" />
$(document).on("click",".fclose",function() {
	
var delParent=this.parentNode;
	if(confirm('폴더를 삭제합니다.\n'
			 +'삭제하시겠습니까?')==true)
		{
		//여기서 ajax 삭제처리 해주어야함
		var pathname=this.previousSibling.previousSibling.innerText.trim();
		$.ajax({
			type:"post",
			url:"${deleteFolder}",
			data:{
				curUserId:'${curUserId}',
				pathname:pathname
			},
			success:function(res){
				if(res==1){
					console.log('삭제성공');
					delParent.remove();
				}
				else{
					console.log('삭제실패');
					alert('폴더안의 파일을 먼저 삭제해주세요');
				}
			}
			,
			error:function(xhr,status,error){
				console.log('ajax연결실패')
			}
		});
	
		}
	else{
		return;
		}
    });


//알집으로 압축해서 다운로드처리
<c:url value="/zipdown" var="zipdown" />
	$(document).on("click",".fdown",function() {
		alert('알집으로 다운로드합니다');
		
		var pathname=this.previousSibling.innerText.trim();
		$.ajax({
			type:"post",
			url:"${zipdown}",
			data:{
				curUserId:'${curUserId}',
				pathname:pathname
			},
			success:function(res){
				
					console.log(res);
					var save = document.createElement("a");
					save.href = "/photo_upload/"+res;
			        save.download = res;
			        save.click();
			}
			,
			error:function(xhr,status,error){
				console.log('ajax연결실패')
			}
		});
			
	  });





//파일업로드
function processUpload()
{
    var oMyForm = new FormData();
    for(var i=0;i<files.length;i++){
    	oMyForm.append("file", files[i]);
   		}
    oMyForm.append("userId",'${curUserId}');
    oMyForm.append("folderName",'${folderName}');
    	
   $.ajax({
	   dataType : 'json',
       url : "${photo}",
       data :oMyForm,
       type : "POST",
       enctype: 'multipart/form-data',
       processData: false, 
       contentType:false,
       success : function(result) {
       	  var html="";
       	  $(result[0]).each(function(idx,item){
       		  html+="<a class='fancybox' rel='gallery1' href=/photo_upload/${curUserId}/${folderName}/";
       		  html+=item;
       		  html+=" id='img"+interval+"' ";
       		  html+=">";
       		  html+="<img src=/photo_upload/${curUserId}/${folderName}/";
       		  html+=item;
       		  html+=" width='200px' height='200px' >";
       		  html+="</a>";
       		  //다운로드 이미지버튼
       		  html+="<a href=/photo_upload/${curUserId}/${folderName}/"+item;
       		  html+=" download id='down"+interval+"' ><img id='down"+interval;
       		  html+="' src='/images/down.png'";
       		  html+="height='25px' width='25px' class='down ' >";
       		  html+="</a>";
       		  //삭제 이미지버튼
       		  html+="<img id='chk"+interval;
       		  html+="' src='/images/delete.png'";
       		  html+="height='25px' width='25px' class='close '>";
        		  
       		  interval++;
        	  });
       	  	$("#result")[0].innerHTML+=html;
        	  
          },
          error : function(result){
        	  console.log('실패');
        	  console.log(result);
          }
      });
}



<c:url value="/newfolder" var="newfolder" />
$("#newFolder").on("click",function(){
	
	var conf = prompt("새폴더 이름을 만들어주세요");
	conf=conf.replace(/ /gi,'');
	$.ajax({
		type:"post",
		url:"${newfolder}",
		data:{
			userId:'${curUserId}',
			name:conf
		},
		success:function(res){
			if(res==true){
				var html="";
				html+="<div class='outer'>";
				html+="<img src=/images/newFolder2.png class='folderInline'";
				html+=" width='200px' height='200px' >";
				html+="<div class='bottomleft'><p style='text-align: center'>";
				html+=conf;
				html+="  </p></div>"
				//다운로드 이미지버튼
				var fullName="";
				for(var op=0;op<this.length;op++){
				fullName +=this[op];
				}
		     	
		     	html+="<img id='down"+interval;
		     	html+="' src='/images/zipdown.png' ";
		     	html+="height='25px' width='25px' class='fdown ' >";
		      	
		      	//삭제 이미지버튼
		      	html+="<img id='chk"+interval;
		      	html+="' src='/images/delete.png'";
		      	html+="height='25px' width='25px' class='fclose '>";
				html+="<div>";
				$("#result")[0].innerHTML+=html;
			}
			else{
				console.log('실패');
			}
		}
		,
		error:function(xhr,status,error){
			alert(error);
		}
	});
		
});

<c:url value="/loadfolder" var="loadfolder" />


	
$(document).on("ready",function(){
	
	$(document).on("dblclick",".folderInline",function(event) {
		
		var folderName = event.currentTarget.nextSibling.innerText;
		
		if(folderName.trim()=='..'){
			folderName='.'
		}
			
	 	location.href='/session/myPhoto?userId=${curUserId}&folderName='+folderName;
		
		
	});
	$.ajax({
		type:"post",
		url:"${loadfolder}",
		data:{
			userId:'${curUserId}',
			folderName:'${folderName}'
		},
		success:function(res){
			//폴더목록 띄워주기 . 여기서 if문으로
			//주인은 모든 폴더를 볼 수 있고,
			//주인이 아닌
			//공유자는 공유폴더만 볼 수 있도록
			var shareFolderList=[];
			<c:forEach items='${shareFolder}' var='shareF' >
			shareFolderList.push('${shareF}');
			</c:forEach>
			console.log('공유된 폴더리스트'+shareFolderList);
			$(res[1]).each(function(idx,item){	
				if( shareFolderList.indexOf(item)!=-1&&'${curUserId}'!='${Users.userId}'){
					var html="";
					html+="<div class='outer'>";
					html+="<img src=/images/newFolder2.png class='folderInline'";
					html+=" width='200px' height='200px' >";
					html+="<div class='bottomleft'><p style='text-align: center'>";
					html+=item;
					html+="  </p></div>";
					//다운로드 이미지버튼
					var fullName="";
					for(var op=0;op<this.length;op++){
					fullName +=this[op];
					}
		       		html+=" <img id='down"+interval;
		       		html+="' src='/images/zipdown.png'";
		       		html+="height='25px' width='25px' class='fdown ' >";
		       		
		       		
					html+="<div>";
					$("#result")[0].innerHTML+=html;
				}
				else if('${curUserId}'=='${Users.userId}'){
					var html="";
					html+="<div class='outer'>";
					html+="<img src=/images/newFolder2.png class='folderInline'";
					html+=" width='200px' height='200px' >";
					html+="<div class='bottomleft'><p style='text-align: center'>";
					html+=item;
					html+="  </p></div>";
					//다운로드 이미지버튼
					var fullName="";
					for(var op=0;op<this.length;op++){
					fullName +=this[op];
					}
		       		html+="<img id='down"+interval;
		       		html+="' src='/images/zipdown.png'";
		       		html+="height='25px' width='25px' class='fdown ' >";
		       		//삭제 이미지버튼
		       		html+="<img id='chk"+interval;
		       		html+="' src='/images/delete.png'";
		       		html+="height='25px' width='25px' class='fclose '>";
		       		
					html+="<div>";
					$("#result")[0].innerHTML+=html;
				}
				
				
				
				});
			
			<c:if test="${!(empty folderName|| folderName=='.')}">
			//폴더가 없을경우 이전폴더로 가는 폴더이미지띄우기
			if(res[1].length==0){
				var html="";
				html+="<div class='outer'>";
				html+="<img src=/images/newFolder2.png class='folderInline'";
				html+=" width='200px' height='200px' >";
				html+="<div class='bottomleft'><p style='text-align: center'>..</p></div><div>";
	       		$("#result")[0].innerHTML+=html;
			}
			</c:if>
			
				var html="";
			//파일목록 받아옴
	       	  $(res[0]).each(function(idx,item){
	       		  html+="<a class='fancybox' rel='gallery1' href=/photo_upload/${curUserId}/${folderName}/";
	       		  html+=item;
	       		  html+=" id='img"+interval+"' ";
	       		  html+=">";
	       		  html+="<img src=/photo_upload/${curUserId}/${folderName}/";
	       		  html+=item;
	       		  html+=" width='200px' height='200px' >";
	       		  html+="</a>";
	       		  //다운로드 이미지버튼
	       		  html+="<a href=/photo_upload/${curUserId}/${folderName}/"+item;
	       		  html+=" download id='down"+interval+"' ><img id='down"+interval;
	       		  html+="' src='/images/down.png'";
	       		  html+="height='25px' width='25px' class='down ' >";
	       		  html+="</a>";
	       		  //삭제 이미지버튼
	       		  html+="<img id='chk"+interval;
	       		  html+="' src='/images/delete.png'";
	       		  html+="height='25px' width='25px' class='close '>";
	        		  
	       		  interval++;
	        	  });
	       	  	$("#result")[0].innerHTML+=html;
			
		},
		error:function(xhr,status,error){
			console.log(xhr.responseText);
			alert(error);
		}
	});
	

});

</c:if>
</script>

</html>