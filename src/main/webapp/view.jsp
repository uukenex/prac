<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE>
<html>
<HEAD>
	<TITLE> New </TITLE>
	<script language="javascript">
		function bonload()
		{
			document.b.b.focus();
		}
		function bsubmit()
		{
			if  (document.b.b.value != "")
			{
				btext = document.b.b.value;
				window.opener.atext(btext);
				window.close();
			}			
			else
				document.b.b.focus();
		}
	</script>
</HEAD>
<BODY onload="bonload()">
내용을 입력하세요.
<form name="b">
	<input type="text" name="b">
	<input type="button" value="확인" onclick="bsubmit()">
</form>
</BODY>
</HTML>