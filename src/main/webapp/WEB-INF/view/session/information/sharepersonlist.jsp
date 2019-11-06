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
<title>${Users.userNick }님의 공유 설정</title>
</head>
<body>


<c:forEach var="sharePerson" items="${sharePersonList }">
<div class="share_choice_person">
<label for="shareId1">공유자1</label>
<input type="text" name="shareId1" id="shareId1" placeholder=" 클릭하세요."
readonly="readonly" value="${sharePerson.photoShareId1}"
onclick="window.open('/searchNick', 'shareId1',
'width=400, height=350');" >
<input type="button" id="shareId1" value="삭제하기" class="share_person_button delete">
</div>

<div class="share_choice_person">
<label for="shareId2">공유자2</label>
<input type="text" name="shareId2" id="shareId2" placeholder=" 클릭하세요."
readonly="readonly" value="${sharePerson.photoShareId2}"
onclick="window.open('/searchNick', 'shareId2',
'width=400, height=350');" >
<input type="button" id="shareId2" value="삭제하기" class="share_person_button delete">
</div>

<div class="share_choice_person">
<label for="shareId3">공유자3</label>
<input type="text" name="shareId3" id="shareId3" placeholder=" 클릭하세요."
readonly="readonly" value="${sharePerson.photoShareId3}"
onclick="window.open('/searchNick', 'shareId3',
'width=400, height=350');" >
<input type="button" id="shareId3" value="삭제하기" class="share_person_button delete">
</div>

<div class="share_choice_person">
<label for="shareId4">공유자4</label>
<input type="text" name="shareId4" id="shareId4" placeholder=" 클릭하세요."
readonly="readonly" value="${sharePerson.photoShareId4}"
onclick="window.open('/searchNick', 'shareId4',
'width=400, height=350');" >
<input type="button" id="shareId4" value="삭제하기" class="share_person_button delete">
</div>

</c:forEach>

<script type="text/javascript" src="http://code.jquery.com/jquery.js"></script>
<script type="text/javascript">

<c:url value="/noshare" var="noshare" />
	$(".delete").on("click",function(){
		var shareId = $(this)[0].id;
		$.ajax({
			type:"post",
			url:"${noshare}",
			data:{
				userId:'${Users.userId}',
				shareId:shareId,
				folderName:'<%=request.getParameter("folderName")%>'
			},
			success:function(res){
				console.log(res);
				if(res==1){
					console.log(window);
					location.reload();
				}
			}
			,
			error:function(xhr,status,error){
				alert(error);
			}
		});
			
	});
</script>
</body>
</html>