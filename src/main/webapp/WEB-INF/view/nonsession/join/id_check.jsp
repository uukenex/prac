<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>아이디 중복 확인</title>
</head>
<style>
	fieldset {
		background-color: #95B3D7;
		width: 800px;
	}
</style>
<body>
	<img alt="아아디 중복확인 img" 
			src="<%=request.getContextPath() %>/join_icons/id_validation_check.png"
			onLoad="javascript: imgResize(this)" />
	<fieldset>
		<table class="IdvalidationTable">
			<tr>
				<td>▷ 아이디 o3ocrazy 는 사용할 수 있습니다.</td>
				<td><input type="button" value="사용하기"></td>
			</tr>
			<tr>
				<td>▷ 다른 아이디 검색 입력 <input type="text" id="id" name="id" placeholder="아이디 입력"></td>
				<td><input type="button" value="확인"></td>
			</tr>
		</table>
	</fieldset>
	<img alt="Barimg" src="<%=request.getContextPath() %>/join_icons/bar.png"
			onLoad="javascript: imgResize(this)" />
	<br>
	<p align="right"><a href="#">close</a></p>
</body>
<script>
	// 이미지 resize
	function imgResize(obj) {
		var imgFile = new Image();
		 imgFile.src = obj.src;
		 var x1 = imgFile.width;
		 var y1 = imgFile.height;
		 
		 var x2 = 0;
		 var y2 = 0;
		 
		 if(x1 >600){
		  x2 = 600;
		  y2 = 600;
		  
		  if (x1 >= y1){
		   y2 = parseInt((y1 * x2) / x1);
		  }else{
		   x2 = parseInt((x1 * y2) / y1);
		  }
		 }else{
		  x2 = x1;
		  y2 = y1;
		 }
		 
		 obj.width = x2; 
		 obj.height = y2; 
	}
</script>
</html>