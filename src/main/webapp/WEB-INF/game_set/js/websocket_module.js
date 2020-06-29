//https://j3rmy.tistory.com/3
	
	var webSocket;
	
	if(isLocal){
		webSocket = new WebSocket('ws://localhost:80/WebSocket4');
	}else{
		webSocket = new WebSocket('ws://http://54.180.82.173/WebSocket4');
	}
	
	var textarea = document.getElementById("messageWindow");
    
    webSocket.onerror = function(event) {
        onError(event)
    };
    webSocket.onopen = function(event) {
        onOpen(event)
    };
    webSocket.onmessage = function(event) {
        onMessage(event)
    };
    webSocket.onclose = function(event) {
        onClose(event)
    };
    
    function onMessage(event) {
        var message = event.data;
        if (message == "") {
            
        } else {
        	$("#messageWindow").html($("#messageWindow").html()
                    + message + "\n");
        }
    }
    function onOpen(event) {
        $("#messageWindow").html("채팅에 참여하였습니다.\n");
        webSocket.send($("#chat_id").val() + "님 접속");
    }
    function onClose(event) {
        webSocket.send($("#chat_id").val() + "님 종료");
    }
    function onError(event) {
        alert(event.data);
    }
    function send(chatbox_id,msg) {
    	//var inputMessage = document.getElementById(chatbox_id);
        
        $("#messageWindow").html($("#messageWindow").html()
            + "나 : " + msg + "\n");
        webSocket.send($("#chat_id").val() + " : " + msg);
        //inputMessage.value = "";
    }
    //     엔터키를 통해 send함
    /*
    function enterkey() {
        if (window.event.keyCode == 13) {
            send();
        }
    }*/
    //     채팅이 많아져 스크롤바가 넘어가더라도 자동적으로 스크롤바가 내려가게함
    window.setInterval(function() {
        var elem = document.getElementById('messageWindow');
        elem.scrollTop = elem.scrollHeight;
    }, 0);