/*
	Solid State by HTML5 UP
	html5up.net | @ajlkn
	Free for personal and commercial use under the CCA 3.0 license (html5up.net/license)
*/

(function($) {

	"use strict";

	skel.breakpoints({
		xlarge:	'(max-width: 1680px)',
		large:	'(max-width: 1280px)',
		medium:	'(max-width: 980px)',
		small:	'(max-width: 736px)',
		xsmall:	'(max-width: 480px)'
	});

	$(function() {

		var	$window = $(window),
			$body = $('body'),
			$fancyHeader = $('#fancyHeader'),
			$banner = $('.drop_menu_visiable');

		// fancyHeader.
			if (skel.vars.IEVersion < 9)
				$fancyHeader.removeClass('fancyAlt');

			if ($banner.length > 0
			&&	$fancyHeader.hasClass('fancyAlt')) {

				$window.on('resize', function() { $window.trigger('scroll'); });

				$banner.scrollex({
					bottom:		$fancyHeader.outerHeight(),
					terminate:	function() { $fancyHeader.removeClass('fancyAlt'); },
					enter:		function() { $fancyHeader.addClass('fancyAlt'); },
					leave:		function() { $fancyHeader.removeClass('fancyAlt'); }
				});

			}

		// fancyMenu.
		
			var $fancyMenu = $('#fancyMenu');
			$fancyMenu._locked = false;

			$fancyMenu._lock = function() {

				if ($fancyMenu._locked)
					return false;

				$fancyMenu._locked = true;

				window.setTimeout(function() {
					$fancyMenu._locked = false;
				}, 350);

				return true;

			};

			$fancyMenu._show = function() {
				if ($fancyMenu._lock())
					$body.addClass('is-menu-visible');

			};

			$fancyMenu._hide = function() {

				if ($fancyMenu._lock())
					$body.removeClass('is-menu-visible');

			};

			$fancyMenu._toggle = function() {

				if ($fancyMenu._lock())
					$body.toggleClass('is-menu-visible');

			};

			$fancyMenu
				.appendTo($body)
				.on('click', function(event) {

					event.stopPropagation();

					// Hide.
						$fancyMenu._hide();

				})
				.find('.fancyInner')
					.on('click', '.close', function(event) {

						event.preventDefault();
						event.stopPropagation();
						event.stopImmediatePropagation();

						// Hide.
							$fancyMenu._hide();

					})
					.on('click', function(event) {
						event.stopPropagation();
					})
					.on('click', 'a', function(event) {
						
						var href = $(this).attr('href');
						event.preventDefault();
						event.stopPropagation();

						// Hide.
							$fancyMenu._hide();

						// Redirect.
							window.setTimeout(function() {
								window.location.href = href;
							}, 350);

					});

			$body
				.on('click', 'a[href="#fancyMenu"]', function(event) {

					event.stopPropagation();
					event.preventDefault();
					// Toggle.
						$fancyMenu._toggle();

				})
				.on('keydown', function(event) {

					// Hide on escape.
						if (event.keyCode == 27)
							$fancyMenu._hide();

				});

	});
	

})(jQuery);