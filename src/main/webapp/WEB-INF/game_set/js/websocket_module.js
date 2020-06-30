//https://j3rmy.tistory.com/3
	
	var webSocket;
	
	if(isLocal){
		webSocket = new WebSocket('ws://localhost:80/WebSocket4');
	}else{
		webSocket = new WebSocket('ws://54.180.82.173/WebSocket4');
	}
	
    webSocket.onerror = function(event) {
        onError(event)
    };
    webSocket.onopen = function(event) {
        onOpen(event)
    };
    webSocket.onmessage = function(event) {
        onMessage(event)
    };
    
    function onMessage(event) {
        var newMsg = event.data;
        var orgMsg = $("#messageWindow").html();
        if (!newMsg == "") {
        	$("#messageWindow").html(orgMsg + newMsg + "\n");
        	$("#messageWindow")[0].scrollTop = $("#messageWindow")[0].scrollHeight;
        } 
    }
    function onOpen(event) {
    }
    function onError(event) {
        alert(event.data);
    }
    function send(chatbox_id,msg) {
        webSocket.send($("#chat_id").val() + " : " + msg);
    }
    