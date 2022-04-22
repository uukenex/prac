<%@ page import="org.springframework.web.bind.annotation.ModelAttribute"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE>
<html>
<HEAD>
<meta name="viewport"
	content="width=device-width, initial-scale=1, user-scalable=no" />

<TITLE>dev</TITLE>
<%-- <link rel="stylesheet" href="<%=request.getContextPath()%>/game_set/css/game5.css?v=<%=System.currentTimeMillis()%>" /> --%>
</HEAD>
<BODY>
<%-- 
	<script src="<%=request.getContextPath() %>/assets/js/jquery.min.js"></script>
	<script type="text/javascript" src="http://bernii.github.io/gauge.js/dist/gauge.min.js"></script>
	<script src="<%=request.getContextPath()%>/game_set/js/gameutil.js?v=<%=System.currentTimeMillis()%>"></script> 
--%>
	<script src="//cdn.jsdelivr.net/npm/phaser@3.11.0/dist/phaser.min.js"></script>
	<script language="javascript">

	var config = {
	    type: Phaser.AUTO,
	    width: 800,
	    height: 600,
	    physics: {
	        default: 'arcade',
	        arcade: {
	            gravity: { y: 1000 },
	            debug: false
	        }
	    },
	    scene: {
	        preload: preload,
	        create: create,
	        update: update
	    }
	};

	var player;
	var stars;
	var bombs;
	var platforms;
	 
	var score = 0;
	var gameOver = false;
	var scoreText;
	
	var cursors;
	var actions;
	
	var systime = <%=System.currentTimeMillis()%>;
	
	var move_speed_min = 160;
	var move_speed_max = 480;
	var move_speed_cur = 160;
	
	var move_speed_change_able = true;

	var game = new Phaser.Game(config);

	function preload (){
	    this.load.image('sky', 'game_set/phaser3/sky.png?v='+systime);
	    this.load.image('ground', 'game_set/phaser3/platform.png?v='+systime);
	    this.load.image('star', 'game_set/phaser3/star.png?v='+systime);
	    this.load.image('bomb', 'game_set/phaser3/bomb.png?v='+systime);
	    this.load.spritesheet('dude', 'game_set/phaser3/dude.png?v='+systime, { frameWidth: 32, frameHeight: 48 });
	}

	function create (){
	    //  A simple background for our game
	    this.add.image(400, 300, 'sky');

	    //  The platforms group contains the ground and the 2 ledges we can jump on
	    platforms = this.physics.add.staticGroup();

	    //  Here we create the ground.
	    //  Scale it to fit the width of the game (the original sprite is 400x32 in size)
	    platforms.create(400, 568, 'ground').setScale(2).refreshBody();

	    //  Now let's create some ledges
	    platforms.create(600, 400, 'ground');
	    platforms.create(50, 250, 'ground');
	    platforms.create(750, 220, 'ground');
	  	//X,Y 좌표 중앙에 배치됨(0일 경우 x200 만 노출,최소길이, 최대길이 초과는 잘림), 기본사이즈 : 400 x 32 / 화면사이즈 800 x 600;
	    platforms.create(400, 0, 'ground'); 

	    // The player and its settings
	    player = this.physics.add.sprite(100, 450, 'dude');

	    //  Player physics properties. Give the little guy a slight bounce.
	    //player.setBounce(0.2);
	    player.setCollideWorldBounds(true);

	    //  Our player animations, turning, walking left and walking right.
	    this.anims.create({
	        key: 'left',
	        frames: this.anims.generateFrameNumbers('dude', { start: 0, end: 3 }),
	        frameRate: 10,
	        repeat: -1
	    });

	    this.anims.create({
	        key: 'turn',
	        frames: [ { key: 'dude', frame: 4 } ],
	        frameRate: 20
	    });

	    this.anims.create({
	        key: 'right',
	        frames: this.anims.generateFrameNumbers('dude', { start: 5, end: 8 }),
	        frameRate: 10,
	        repeat: -1
	    });

	    //  Input Events
	    cursors = this.input.keyboard.createCursorKeys();
	    actions = this.input.keyboard.addKeys({
			'TAB': Phaser.Input.Keyboard.KeyCodes.TAB,
			'ESC': Phaser.Input.Keyboard.KeyCodes.ESC,
			'Space': Phaser.Input.Keyboard.KeyCodes.SPACE,
			'X': Phaser.Input.Keyboard.KeyCodes.X,
			'One': Phaser.Input.Keyboard.KeyCodes.ONE,
			'Two': Phaser.Input.Keyboard.KeyCodes.TWO,
			'F': Phaser.Input.Keyboard.KeyCodes.F,
			'C': Phaser.Input.Keyboard.KeyCodes.C,
			'W': Phaser.Input.Keyboard.KeyCodes.W,
			'M': Phaser.Input.Keyboard.KeyCodes.M,
			'I': Phaser.Input.Keyboard.KeyCodes.I,
			'A': Phaser.Input.Keyboard.KeyCodes.A,
			'S': Phaser.Input.Keyboard.KeyCodes.S,
			'D': Phaser.Input.Keyboard.KeyCodes.D,
			'E': Phaser.Input.Keyboard.KeyCodes.E,
			'Enter': Phaser.Input.Keyboard.KeyCodes.ENTER
		});
	    
	    //  Some stars to collect, 12 in total, evenly spaced 70 pixels apart along the x axis
	    stars = this.physics.add.group({
	        key: 'star',
	        repeat: 11,
	        setXY: { x: 12, y: 0, stepX: 70 }
	    });

	    stars.children.iterate(function (child) {

	        //  Give each star a slightly different bounce
	        child.setBounceY(Phaser.Math.FloatBetween(0.4, 0.8));

	    });

	    bombs = this.physics.add.group();

	    //  The score
	    scoreText = this.add.text(16, 16, 'score: 0', { fontSize: '30px', fill: '#000' });

	    //  Collide the player and the stars with the platforms
	    this.physics.add.collider(player, platforms);
	    this.physics.add.collider(stars, platforms);
	    this.physics.add.collider(bombs, platforms);

	    //  Checks to see if the player overlaps with any of the stars, if he does call the collectStar function
	    this.physics.add.overlap(player, stars, collectStar, null, this);

	    this.physics.add.collider(player, bombs, hitBomb, null, this);
	}

	function update (){
	    if (gameOver){
	        return;
	    }

	    if (cursors.left.isDown){
	        player.setVelocityX(-move_speed_cur);

	        player.anims.play('left', true);
	    }
	    else if (cursors.right.isDown){
	        player.setVelocityX(move_speed_cur);

	        player.anims.play('right', true);
	    }
	    else{
	        player.setVelocityX(0);

	        player.anims.play('turn');
	    }

	    if (cursors.up.isDown && player.body.touching.down){
	        player.setVelocityY(-600);
	    }
	    
	    if( actions.Space.isDown){
	    	console.log('space');
	    }
	    
	    //jump 중에 속도 변경 불가처리
	    if(move_speed_change_able){
	    	if(actions.A.isDown){
	    		move_speed_cur = move_speed_max; 
	    	}else{
	    		move_speed_cur = move_speed_min;
	    	}
	    }
	    
	    if(player.body.touching.down){
	    	move_speed_change_able = true;
	    }else{
	    	move_speed_change_able = false;
	    }
	    
	}

	function collectStar (player, star){
	    star.disableBody(true, true);

	    //  Add and update the score
	    score += 10;
	    scoreText.setText('Score: ' + score);

	    if (stars.countActive(true) === 0){
	        //  A new batch of stars to collect
	        stars.children.iterate(function (child) {

	            child.enableBody(true, child.x, 0, true, true);

	        });

	        var x = (player.x < 400) ? Phaser.Math.Between(400, 800) : Phaser.Math.Between(0, 400);

	        var bomb = bombs.create(x, 16, 'bomb');
	        bomb.setBounce(1);
	        bomb.setCollideWorldBounds(true);
	        bomb.setVelocity(Phaser.Math.Between(-200, 200), 20);
	        bomb.allowGravity = false;

	    }
	}

	function hitBomb (player, bomb){
	    this.physics.pause();

	    player.setTint(0xff0000);

	    player.anims.play('turn');

	    gameOver = true;
	}
	</script>
</BODY>
</HTML>