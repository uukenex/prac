
var isLocal = location.href.indexOf('localhost')>0 ;
var windowWidth  = $(window).width()>=500?500:$(window).width();
var windowHeight = $(window).height()>=700?700:$(window).height();
var mediaCode = getMediaCode();


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


window.addEventListener('resize', windowClear, true);


function windowClear(){
	if(windowWidth != $(window).width() && !isLocal ){
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
