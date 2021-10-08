//무기정보 변수 선언 후 weapons 구현할 것..  

var wp1 = {};
	wp1.wp_id = 'wb001'; // table1 pk , table2 pk  
	wp1.wp_name = '초보자무기';// table1
	wp1.wp_desc = '초보자용 무기이다.';//table1
	wp1.wp_org_damage = 10; //table1
	wp1.wp_add_damage = 0;
	wp1.wp_adv_no = 0; //table2
	wp1.wp_key ="yyyymmdd + seq"; //table2 pk
	
var wp2 = {};
	wp2.wp_id = 'wb002';
	wp2.wp_name = '숙련자무기';
	wp2.wp_desc = '숙련자용 무기이다.';
	wp2.wp_org_damage = 20;
	wp2.wp_add_damage = 0;
	wp2.wp_adv_no = 0;
	wp2.wp_key ="yyyymmdd + seq";

var wp3 = {};
	wp3.wp_id = 'wb003';
	wp3.wp_name = '전설무기';
	wp3.wp_desc = '전설속에만 존재하는 무기이다.</br>구할수 없다';
	wp3.wp_org_damage = 200;
	wp3.wp_add_damage = 0;
	wp3.wp_adv_no = 0;
	wp3.wp_key ="yyyymmdd + seq";

//아이템
var weapons = {
		'wb001':wp1,
		'wb002':wp2,
		'wb003':wp3,
		getWpVal : function(Str){
			return this[Str];
		}
};
