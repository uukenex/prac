// 모바일 여부
var isMobile = false;
// PC 환경
var filter = "win16|win32|win64|mac";
var finalWindowWidth  = $(window).width();
var finalWindowHeight = $(window).height();



if (navigator.platform) {
    isMobile = filter.indexOf(navigator.platform.toLowerCase()) < 0;
    
}

console.log('모바일여부 : '+ isMobile);
window.addEventListener('resize', resize_event, true);
resize_event('test');




function resize_event(arg){
	if($('.board_part_title').length != 0 || arg=='test'){
		console.log('board_part_title setting');
		$('.board_part_title').attr('title', 'Width x Height : '+finalWindowWidth + ' x '+finalWindowHeight );
	}else{
		console.log('board_part_title missing');
	}
}