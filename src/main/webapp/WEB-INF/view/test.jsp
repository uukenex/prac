<html>
<head>
<meta charset="UTF-8">
<title>captcha-test-01</title>
	<script type="text/javascript" src="http://code.jquery.com/jquery.js"></script>
	<!--<script type="text/javascript" src="http://www.google.com/recaptcha/api/js/recaptcha_ajax.js"></script> -->
	<script src="https://www.google.com/recaptcha/api.js?onload=onloadCallback&render=explicit" async defer></script>
	<script>
	//	화면 시작 시 g-recaptcha 생성
	 var onloadCallback = function() {
	   grecaptcha.render('g-recaptcha', {
	   'sitekey' : '${reCaptchaSiteKey}',
	   'callback' : verifyCallback,
	   'expired-callback' : expiredCallback,
	   });
	 };

	 //	인증 성공 시
	 var verifyCallback = function(response) {
	   $("#loginBtn").removeClass("disabled-btn");
	   $("#loginBtn").attr("disabled", false);
	 };

	 //	인증 만료 시
	 var expiredCallback = function(response) {
	   $("#loginBtn").addClass("disabled-btn");
	   $("#loginBtn").attr("disabled", true);
	 }

	 //	g-recaptcha 리셋
	   var resetCallback = function() {
	   grecaptcha.reset();
	 }
		
	$(function(){
	        $("#loginBtn").click(function(){
	            
				var recaptcha = $("#g-recaptcha-response").val();
				
	            $.ajax({
	                type: "POST",
	                url: "/validation",
	                data: {
	                    recaptcha : recaptcha
	                },
					dataType: "JSON",
	                success: function(data) {
	                    if(data == "0") {
	                        alert('okok');
	                    }else{
							alert('failfail');
	                    }
	                }
	            });
	            
	        });
	            
	    });    
	</script>
</head>

<body>
	<div id="g-recaptcha"></div>
	<br>
	<div>
		<input type="button" class="disabled-btn" id="loginBtn" value="login" disabled>
	</div>
</body>
</html>