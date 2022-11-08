// 모바일 여부
var isMobile = false;
// PC 환경
var filter = "win16|win32|win64|mac";
var resizeWindowWidth  = $(window).width();
var resizeWindowHeight = $(window).height();



if (navigator.platform) {
    isMobile = filter.indexOf(navigator.platform.toLowerCase()) < 0;
    
}

console.log('모바일여부 : '+ isMobile);
window.addEventListener('resize', resize_event, true);

function resize_event(arg){
	resizeWindowWidth  = $(window).width();
	resizeWindowHeight = $(window).height();
	
	if($('img').length == 0){
		return;
	}
	var se2_board_width = $('img').parent().css('width').replace('px','');
	
	if(resizeWindowWidth < se2_board_width ){
		se2_board_width = resizeWindowWidth;
		$('img').css('width',Math.round( se2_board_width * 0.9) );
		$('img').css('height',Math.round( se2_board_width * 0.9) );
	}else{
		$('img').css('width',Math.round( resizeWindowWidth * 0.9) );
		$('img').css('height',Math.round( resizeWindowWidth * 0.9) );
	}
}