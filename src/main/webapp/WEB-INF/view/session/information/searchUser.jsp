<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>공유자 찾기</title>

<style>
p{
display:inline;
}
</style>

</head>
<body>
	<div class="search_for_users">
	
		<label for="searchNick" >닉네임 검색</label>
		<input type="text" id="searchNick" name="searchNick">
		<input type="button" id="search" value="검색" > 
	</div>
<hr>

<div id="search_for_result">닉네임 / ID</div>
<output id="result"></output>

<script src="http://code.jquery.com/jquery.js"></script>
<script>
<c:url value="/selectedAjax" var="selectedAjax"></c:url>
$(document).on("click",".share",function() {
	var uId = this.previousSibling.innerText;
	$.ajax({
		type:"post",
		url:'${selectedAjax}',
		data:{
			winName:$(window)[0].name,
			shareId:uId,
			folderName:$(window.opener)[0].name
		},
		success:function(res){
			console.log(res);
			$(window.opener.document)[0].activeElement.value=uId;
			window.close();
		},
		error:function(request,status,error){
			alert(request.responseText);
		}
	});
	
	
});


<c:url value="/searchNickAjax" var="searchNickAjax"></c:url>
$("#search").on("click",function(){
	$.ajax({
		type:"post",
		url:'${searchNickAjax}',
		data:{
			nickName:$("#searchNick").val()
		},
		success:function(res){
			$("#result")[0].innerHTML="";
			var html = "";
			$(res).each(function(idx,item){
				html+="<p>";
				html+=item.USER_NICK;
				html+="</p>";
				html+="/";
				html+="<p>";
				html+=item.USER_ID;
				html+="</p>";
				html+="<input type='button' class='share' value='공유하기'>";
				html+="<br>";
				
			});
			$("#result")[0].innerHTML=html;
		},
		error:function(request,status,error){
			alert(request.responseText);
		}
		});
});
</script>

</body>
</html>