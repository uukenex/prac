
var isLocal = location.href.indexOf('localhost')>0 ;

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

