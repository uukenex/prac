
	
	/*window.onload = function () {
        if (window.Notification) {
            Notification.requestPermission();
        }
    }*/
	
	//Websocket :: https://j3rmy.tistory.com/3
	var webSocket;
	
	if(isLocal){
		webSocket = new WebSocket('ws://localhost:80/WebSocket4');
	}else{
		webSocket = new WebSocket('ws://54.180.82.173/WebSocket4');
	}
	
	
	
	
	

	/*var notifications = new Array();
	
	//notification :: https://ko.coder.work/so/javascript/542330
    function calculate(newMsg) {
    	
    	if (Notification.permission == 'granted') {
    	
    		closeAllPush();
		   	var notification = new Notification('chat_test', {
	            icon: 'http://cdn.sstatic.net/stackexchange/img/logos/so/so-icon.png',
	            body: newMsg,
	        });
		   	notifications.push(notification);
	    };
        
	    function closeAllPush(){
	    	for (notification in notifications) {
    		   notifications[notification].close();
    		}
	    }
        
    }*/

    
    
	
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
        	
        	/*if(newMsg.indexOf($("#chat_id").val())!=0){
        		calculate(newMsg);
        	}*/
        		
        } 
    }
    function onOpen(event) {
    	webSocket.send($("#chat_id").val() + " 님 입장");
    }
    function onError(event) {
        alert(event.data);
    }
    function send(chatbox_id,msg) {
        webSocket.send($("#chat_id").val() + " : " + msg);
    }
    