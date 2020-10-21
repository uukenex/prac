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
}