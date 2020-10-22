	var webSocket;
	
	var v_chat_count=0;
	var v_sp_count = 0;
	
	function onConnect(){
		var webSocketUrl = 'ws://dev-apc.com/WebSocket4';
		if(isLocal){
			webSocketUrl = 'ws://localhost/WebSocket4?userNick='+$('#chat_id').val(); 
		}
		webSocket = new WebSocket(webSocketUrl);
		
	    webSocket.onmessage = function(event) {
	        onMessage(event)
	    };
	}
    
    
	function onMessage(event) {
        var newMsg = event.data;
        var orgMsg = $("#messageWindow").html();
        
        var fulldata = newMsg.split('|')
        
        var msg_gb = fulldata[0];
        
        /**msg gb
         * 00 : 시스템메시지
         * 01 : 채팅
         * 02 : 이동
         * 03 : 공격
         * */
        
        switch(msg_gb){
            case '09': //채팅유저리스트
            	var userdata = fulldata[1].substring(1,fulldata[1].length-1);
            	var userlist = userdata.split(', ');
            	$('#chat_user_list').text('');
            	for(var i =0 ; i < userlist.length ; i++){
            		$('#chat_user_list').append(userlist[i]+'</br>');
            	}
            	
            	
            	break;
        	case '00': //시스템메시지
        		//메시지박스 출력
                $("#messageWindow").html(orgMsg +fulldata[1] + "\n");
            	$("#messageWindow")[0].scrollTop = $("#messageWindow")[0].scrollHeight;
            	$('.char_chat').remove();
            	
        		break;
        		
        	case '01': //채팅
        		var targetName = fulldata[1];
                var targetX = fulldata[2];
                var targetY = fulldata[3];
                var targetMsg = fulldata.slice(4).join('');;
                
                var chatbox_create_yn = true;
            	$('#chat_space').append('<input type="text" class="chat chat_float" id="chat_float'+v_chat_count+'" readonly="true"></input>'); 
            	chatterbox_timer_function(v_chat_count,targetName,targetX,targetY,targetMsg);
            	
            	//메시지박스 출력
                $("#messageWindow").html(orgMsg +targetName + " : "+ targetMsg + "\n");
            	$("#messageWindow")[0].scrollTop = $("#messageWindow")[0].scrollHeight;
        		break;
        		
        	case '02': //이동
        		var targetName = fulldata[1];
                var targetX = fulldata[2];
                var targetY = fulldata[3];
                
            	var newChar = "char_"+targetName;
                
                if( $('#'+newChar).length == 0 ){
                	$('#space').append('<div id='+newChar+' class="char_chat '+targetName+'"></div>');
                }else{
                	$('#'+newChar).css({top: targetY, left: targetX});
                }
                    
        		break;
        		
        	case '03':
        		var targetName = fulldata[1];
                var startX = Number(fulldata[2]);
                var startY = Number(fulldata[3]);
                var direction = fulldata[4];
                var b_speed = 3;
        		
                create_socket_bullet(v_sp_count,b_speed,startX,startY,direction)
                v_sp_count++;
    			 
    			
    			
    			
    			function create_socket_bullet(sp_count,speed,bul_x,bul_y,l_key){
    				var bullet_move_timer;
        			
    				$('#bullet_field').append('<div class="bullet '+targetName+'" id="bullet_'+targetName+sp_count+'"></div>');
    				
        			bullet_move_timer = setInterval(function(){
        				$('#bullet_'+targetName+sp_count).css('display','block');
        				bullet_move(sp_count, direction);
        			}, 10); 
        			
        			function bullet_move(inner_sp_count,lkey){
            			switch(lkey){
            				case '37'://left
            					bul_x -= speed; 
            					break;
            				case '38'://up
            					bul_y -= speed; 
            					break;
            				case '39'://right
            					bul_x += speed; 
            					break;
            				case '40'://down
            					bul_y += speed; 
            					break;
            			}
            			
            			$('#bullet_'+targetName+inner_sp_count).css({top: bul_y, left: bul_x}); 
            			if(bul_x < -30 || bul_x > 430 || bul_y < -30 || bul_y > 430 ){
            				clearTimeout(bullet_move_timer);
            				$('#bullet_'+targetName+inner_sp_count).remove();
            			}
            		}
    			}
    			
    			
                
        		break;
        		
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
				$('#chat_float'+v_chat_count_values).css('display','block');
			}
    	},10);
    		
		
		
		chat_timer = setTimeout(function() {
			$('#chat_float'+v_chat_count_values).remove();
			createYn = false;
		}, 3000);
		v_chat_count++;
		
    }
    
    function send(msg_gb, msg, x, y) {
    	
    	var sendVal;
    	sendVal = msg_gb+"|"+$("#chat_id").val() + '|' + x + '|' + y + '|' + msg;
    	webSocket.send(sendVal);
    	
    }