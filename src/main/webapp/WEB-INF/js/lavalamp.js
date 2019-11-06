/* lavalamp.nav.js - 라바 램프 네비게이션 디자인 스타일, 2012 © yamoo9.com
---------------------------------------------------------------- */
(function($) { // 자바스크립트 자가실행함수
	$(function() { // jQuery Ready()문.
	
		// 대상 참조
		var $nav = $('#navigation'),
			$current_item = $nav.find('.focus'),
			$lava = $('<li class="lava"/>'),
			// 옵션 설정
			options = {
				gap: 20,
				bgColor: '#eee',
				speed: 400,
				easing: 'easeInOutElastic',	
				reset: 2000
			},
			reset;
		
		// $lava의 기준요소 $nav 설정 및 <a> z-index 높이 조정
		$nav.css('position', 'relative')
			.find('a').css({
				position: 'relative',
				zIndex: 1
			});
		
		// $lava 조작 및 스타일링
		$lava.css({
			position: 'absolute',
			top: $current_item.position().top - options.gap/2,
			left: $current_item.position().left,
			width: $current_item.outerWidth(),
			height: $current_item.outerHeight() + options.gap,
			backgroundColor: options.bgColor		
		}).appendTo($nav.find('ul'));
		
		// $nav의 li에 마우스/포커스 이벤트 핸들링 제어
		$nav.find('li')
		.bind('mouseover focusin', function() {
			// 마우스 오버, 포커스 상태에서 수행할 코드
			clearTimeout(reset);
			$lava.animate({ 
				left: $(this).position().left,
				width: $(this).outerWidth()
			}, {
				duration: options.speed,
				easing: options.easing,
				queue: false
			});
		})
		.bind('mouseout focusout', function() {
			// 마우스 오버, 포커스 상태에서 수행할 코드
			reset = setTimeout(function() {
				$lava.animate({
					left: $current_item.position().left,
					width: $current_item.outerWidth()					
				}, options.speed);
			}, options.reset);
		});
		
	});
})(jQuery); // window.jQuery 객체 전달.