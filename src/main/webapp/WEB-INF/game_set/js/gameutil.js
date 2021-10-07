
var isLocal = location.href.indexOf('localhost') > 0 && !(location.href.indexOf('test') > 0);
var isOnlyChat = location.href.indexOf('ws') > 0 || location.href.indexOf('game6')  ;
var windowWidth_org = $(window).width();
var windowHeight_org = $(window).height();

var windowWidth  = $(window).width()>=500?500:$(window).width();
var windowHeight = $(window).height()>=700?700:$(window).height();
var mediaCode = getMediaCode();

var flag_common_game_over = false;

var dbDate = new Date().toISOString().slice(0, 19).replace('T', ' ');

if(!isLocal){
/*
	if(location.href.indexOf('game1') > 0 || location.href.indexOf('game2') > 0
	|| location.href.indexOf('game3') > 0 || location.href.indexOf('game4') > 0 ){
		//alert('유지보수중인 항목입니다.');
		//history.back();
	}*/
}



/*
$(document).on("keydown.disableScroll", function(e) {
	var eventKeyArray = [32 ,33, 34, 35, 36, 37, 38, 39, 40]; //32사용시 채팅 스페이스안됨 
	for (var i = 0; i < eventKeyArray.length; i++) {
		if (e.keyCode === eventKeyArray [i]) { 
			e.preventDefault();
			return; 
		} 
	} 
});
*/
document.oncontextmenu = function(e) {
	return false;
}

jQuery(document).keydown(function(e){
	if(e.target.nodeName != "INPUT" && e.target.nodeName != "TEXTAREA"){
		if(e.keyCode === 37 || e.keyCode === 38 || e.keyCode === 39 || e.keyCode === 40){
			return false;
		}
	}
});

window.onscroll = function () { window.scrollTo(0, 0); };
window.addEventListener('resize', windowClear, true);

function windowClear(){
	if(windowWidth != $(window).width() && !isLocal && !isOnlyChat ){
		$('body').css('display','none');
	}
}

function windowFadeOut(){
	$('body').fadeOut(1000);
}
function windowFadeIn(){
	$('body').fadeIn(500);
}

//min 0 , max 3 인 경우 0, 1, 2, 3 이 나올수 있음
function getRandomInt(min, max) {
	return Math.floor(Math.random() * (max - min + 1)) + min;
}


function getMediaCode() {
	var filter = "win16|win32|win64|mac";
	var mediaCode;
	if(navigator.platform){
		if(0 > filter.indexOf(navigator.platform.toLowerCase())){
			mediaCode ="MOBILE";
		}else{
			mediaCode ="PC";
		}
	}
	return mediaCode;
}

function pad(n, width) {
	n = n + '';
	return n.length >= width ? n : new Array(width - n.length + 1).join('0') + n;
}

function addZero(num) {
	return (num < 10 ? '0'+num : ''+num);
}

function fun_dPoing(integered_number){
	var DPoint = integered_number.toString().length;
	var DPos = 1;
	switch(DPoint){
		case 1:
			DPos = 1;
			break;
		case 2:
			DPos = 10;
			break;
		case 3:
			DPos = 100;
			break;
		case 4:
			DPos = 1000;
			break;
	}
	return DPos;
}

function meetBox(org_rect,target_rect){
	if(target_rect.left   <= org_rect.right 
	&& target_rect.right  >= org_rect.left
    && target_rect.top    <= org_rect.bottom 
    && target_rect.bottom >= org_rect.top ) {
		return true;
	}else{
		return false;
	}
}

function meetBoxPre(org_rect,target_rect){
	if(target_rect.left   < org_rect.right + 1
	&& target_rect.right  > org_rect.left - 1
    && target_rect.top    < org_rect.bottom + 1
    && target_rect.bottom > org_rect.top - 1 ) {
		return true;
	}else{
		return false;
	}
}

function NotMovable(target_rect_left,target_rect_right,target_rect_top,target_rect_bottom, org_rect){
	if(target_rect_left < org_rect.right){
		return true;
	} 
	if(target_rect_right > org_rect.left){
		return true;
	}
	if(target_rect_top < org_rect.bottom ){
		return true;
	}
	if( target_rect_bottom > org_rect.top ){
		return true;
	}
	
	return false;
}


function meetWall(org_rect){
	if(org_rect.right > windowWidth_org || org_rect.left < 0 || org_rect.bottom > windowHeight_org || org_rect.top < 0){
		return true;
	}else{
		return false;
	}
}


function common_game_over(arg_game_no, arg_cnt, arg_reason,arg_userId){
	var selectUrl = "/gameUtil/selectGameCnt";
	var insertUrl = "/gameUtil/insertGameCnt";
	
	if(flag_common_game_over){
		return;
	} 
	flag_common_game_over = true;
	
	
	//선 저장 후
	if(!isLocal){
		$.ajax({
			type : "post",
			url : insertUrl,
			data : {
				msg : ' ',
				userName : arg_userId,
				mediaCode: mediaCode,
				ip : ip(),
				cnt : arg_cnt,
				gameNo : arg_game_no,
				dbDate : dbDate,
				flag : 'I'
			},
			success : function(res) {
				
			},
			error : function(request, status, error) {
				alert(request);
			}
		});	
	}
	
	//화면가리기
	windowFadeOut();
	
	
	setTimeout(function(){
		if(!isLocal){

			var promptVal = prompt(arg_reason+arg_cnt+'점 \n코멘트를 입력해주세요.');
			
			if(promptVal ==null || promptVal== undefined || promptVal===null ){
				alert('결과화면으로 이동합니다');
			}else{
				$.ajax({
					type : "post",
					url : insertUrl,
					data : {
						msg : promptVal,
						userName : arg_userId,
						mediaCode: mediaCode,
						ip : ip(),
						cnt : arg_cnt,
						gameNo : arg_game_no,
						dbDate : dbDate,
						flag : 'U'
					},
					success : function(res) {
						
					},
					error : function(request, status, error) {
						alert(request);
					}
				});
			}
		}
		
		
		setTimeout(function(){
			$.ajax({
				type : "get",
				url : selectUrl,
				data : {
					gameNo : arg_game_no
				},
				success : function(res) {
					var innerHTML = '<table class="rank" style="border:2px solid #FFA2A2"><thead id="board_table_thead"><tr><td><strong>순위</strong></td><td><strong>인입</strong></td><td>ID</td><td>MSG</td><td><strong>점수</strong></td><tr></thead>';
					$(res.RESULT).each(function(idx,item){
						innerHTML += '<tr style="border:2px solid #FFA2A2"><td>'+item.ROWNUM+'</td><td>'+item.MEDIA_CODE+'</td><td>'+item.USER_NICK+'</td><td>'+item.MSG+'</td><td>'+item.CNT+'</td><tr>';
					});
					innerHTML += '</table>';
					$('body')[0].innerHTML = innerHTML;
					windowFadeIn();
				},
				error : function(request, status, error) {
					alert(request);
				}
			});
		},1500);
	},1500);
	//코멘트 update
	
}



