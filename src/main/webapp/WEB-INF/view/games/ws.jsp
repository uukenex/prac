<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>new titles..</TITLE>
</HEAD>
<BODY>
	<script src="http://code.jquery.com/jquery.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/websocket_module.js?v=<%=System.currentTimeMillis()%>"></script>
	
	<script>
	$(document).on('ready',function(e){
		var id = prompt('사용할 닉네임입력');
		if(id ==null || id== undefined || id===null ){
			alert('다시입력해주세요');
			location.reload();
		}
		$('#chat_id').val(id);
	})
	
	function enterkey() {
        if (window.event.keyCode == 13) {
        	var msg = $('#inputMessage').val();
        	if(msg != ''){
        		send('inputMessage',msg);
                $('#inputMessage').val('');	
        	}
        }
    }
	
	function clickSend() {
		var msg = $('#inputMessage').val();
    	if(msg != ''){
			send('inputMessage',$('#inputMessage').val());
			$('#inputMessage').val('');
    	}
	}
	
	</script>
	<%-- 
	<c:if test="${(login.id ne '') and !(empty login.id)}">
        <input type="hidden" value='${login.id }' id='chat_id' />
    </c:if>
    <c:if test="${(login.id eq '') or (empty login.id)}">
        <input type="hidden" value='<%=session.getId().substring(0, 6)%>'
            id='chat_id' />
    </c:if> --%>
    <input type="hidden" value='' id='chat_id' />
    <!--     채팅창 -->
    <div id="_chatbox" style="display: inline;">
        <fieldset>
            <textarea id="messageWindow" cols="60" rows="6" readonly="true"></textarea>
            <br /> <input id="inputMessage" type="text" onkeyup="enterkey()" />
            <input type="submit" value="send" onclick="clickSend()" />
        </fieldset>
    </div>
</BODY>
</HTML>