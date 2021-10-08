//무기정보 변수 선언 후 weapons 구현할 것..  

var wp10 = {};
	wp10.wp_id = 'wb010'; // table1 pk , table2 pk  
	wp10.wp_name = '초보자무기';// table1
	wp10.wp_desc = '초보자용 무기이다.</br>매너5강 필수!';//table1
	wp10.wp_org_damage = 10; //table1
	wp10.wp_add_damage = 0;
	wp10.wp_adv_no = 0; //table2
	wp10.wp_key ="yyyymmdd + seq"; //table2 pk
	wp10.wp_max_socket = 1;
	
var wp20 = {};
	wp20.wp_id = 'wb020';
	wp20.wp_name = '희귀무기';
	wp20.wp_desc = '희귀한 무기이다.</br>소켓을활용한다면 강해질수도..';
	wp20.wp_org_damage = 20;
	wp20.wp_add_damage = 0;
	wp20.wp_adv_no = 0;
	wp20.wp_key ="yyyymmdd + seq";
	wp20.wp_max_socket = 3;
	
var wp30 = {};
	wp30.wp_id = 'wb030';
	wp30.wp_name = '레어무기';
	wp30.wp_desc = ' 현존하는 최강무기이다.</br>설명은 필요없다.';
	wp30.wp_org_damage = 20;
	wp30.wp_add_damage = 0;
	wp30.wp_adv_no = 0;
	wp30.wp_key ="yyyymmdd + seq";
	wp30.wp_max_socket = 3;
	
var wp40 = {};
	wp40.wp_id = 'wb040';
	wp40.wp_name = '유니크무기';
	wp40.wp_desc = '세상에 하나뿐인 무기.</br>당신이 주인이 될수있을까?';
	wp40.wp_org_damage = 20;
	wp40.wp_add_damage = 0;
	wp40.wp_adv_no = 0;
	wp40.wp_key ="yyyymmdd + seq";
	wp40.wp_max_socket = 4;	

var wp50 = {};
	wp50.wp_id = 'wb050';
	wp50.wp_name = '전설무기';
	wp50.wp_desc = '전설속에만 존재하는 무기이다.</br>(강화불가)';
	wp50.wp_org_damage = 200;
	wp50.wp_add_damage = 0;
	wp50.wp_adv_no = 0;
	wp50.wp_key ="yyyymmdd + seq";
	wp50.wp_max_socket = 4;

//아이템
var weapons = {
		'wp10':wp10,
		'wp20':wp20,
		'wp30':wp30,
		'wp40':wp40,
		'wp50':wp50,
		getWpVal : function(Str){
			return this[Str];
		},
		toString : function(Str){
			var full_text = "";
				full_text+= this[Str].wp_name+'</br>';
				full_text+= this[Str].wp_desc+'</br>';
				full_text+= ""+'</br>';
				full_text+= "데미지"+this[Str].wp_org_damage+'</br>';
				full_text+= "최대소켓"+this[Str].wp_max_socket+'</br>';
			return full_text;
		}
};
