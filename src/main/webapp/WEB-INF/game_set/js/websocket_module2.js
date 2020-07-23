	var webSocket;
	var v_chat_count=0;
	
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
        
        var fulldata = newMsg.split('|')
        var targetName = fulldata[0];
        var targetX = fulldata[1];
        var targetY = fulldata[2];
        var targetMsg = fulldata[3];
        
        
        if(!targetName == ""){
        	var newChar = "char_"+targetName;
            
            if( $('#'+newChar).length == 0 ){
            	$('#space').append('<div id='+newChar+' class="char_chat"></div>');
            }else{
            	$('#'+newChar).css({top: targetY, left: targetX});
            }
            
        }
        
        if (!targetMsg == "" ) {
        	$("#messageWindow").html(orgMsg +targetName + " : "+ targetMsg + "\n");
        	$("#messageWindow")[0].scrollTop = $("#messageWindow")[0].scrollHeight;
        	
        	if(!(newMsg.indexOf('|||') == 0)){

            	var chatbox_create_yn = true;
            	$('#chat_space').append('<input type="text" class="chat chat_float" id="chat_float'+v_chat_count+'" readonly="true"></input>'); 
            	chatterbox_timer_function(v_chat_count,targetName,targetX,targetY,targetMsg);
            	
            }
        } 
    }
    
    
    function chatterbox_timer_function(v_chat_count_values,targetName,x,y,msg){
    	var chat_timer ;
    	var createYn = true;
    	var newChar = "char_"+targetName;
    	var chat_float_area;
    	
    	
    	$('#chat_float'+v_chat_count_values).val(msg);
		setInterval(function(){
			if(createYn){
				chat_float_area = $('#'+newChar)[0].getBoundingClientRect();
				$('#chat_float'+v_chat_count_values).css({top: chat_float_area.top, left: chat_float_area.left});
			}
    	},10);
    		
		
		
		chat_timer = setTimeout(function() {
			$('.chat_float').remove();
			createYn = false;
		}, 2000);
		v_chat_count++;
		
    }
    
    
    function onOpen(event) {
    	webSocket.send($("#chat_id").val() + " 님 입장");
    }
    function onError(event) {
        alert(event.data);
    }
    function send(chatbox_id, msg, x, y) {
    	
    	var sendVal;
    	sendVal = $("#chat_id").val() + '|' + x + '|' + y + '|' + msg;
    	webSocket.send(sendVal);
    	
    }
    