//무기정보 변수 선언 후 weapons 구현할 것..  

var wp10 = {};
	wp10.wp_id = 'wp010'; // table1 pk , table2 pk  
	wp10.wp_name_kr = '초보자무기';// table1
	wp10.wp_desc = '초보자용 무기이다.</br>매너5강 필수!';//table1
	wp10.wp_org_damage = 10; //table1
	wp10.wp_add_damage = 0;
	wp10.wp_adv_no = 0; //table2
	wp10.wp_key ="yyyymmdd + seq"; //table2 pk
	wp10.wp_max_socket = 1;
	wp10.wp_img='wp010.png';
	
var wp20 = {};
	wp20.wp_id = 'wp020';
	wp20.wp_name_kr = '희귀무기';
	wp20.wp_desc = '희귀한 무기이다.</br>소켓을활용한다면 강해질수도..';
	wp20.wp_org_damage = 20;
	wp20.wp_add_damage = 0;
	wp20.wp_adv_no = 0;
	wp20.wp_key ="yyyymmdd + seq";
	wp20.wp_max_socket = 3;
	wp20.wp_img='wp020.png';
	
var wp30 = {};
	wp30.wp_id = 'wp030';
	wp30.wp_name_kr = '레어무기';
	wp30.wp_desc = ' 현존하는 최강무기이다.</br>설명은 필요없다.';
	wp30.wp_org_damage = 120;
	wp30.wp_add_damage = 0;
	wp30.wp_adv_no = 0;
	wp30.wp_key ="yyyymmdd + seq";
	wp30.wp_max_socket = 3;
	wp30.wp_img='wp030.png';


var wp40 = {};
	wp40.wp_id = 'wp040';
	wp40.wp_name_kr = '유니크무기';
	wp40.wp_desc = '세상에 하나뿐인 무기.</br>주인으로 인정받아야한다.</br>(고유)(파괴불가)';
	wp40.wp_org_damage = 220;
	wp40.wp_add_damage = 0;
	wp40.wp_adv_no = 0;
	wp40.wp_key ="yyyymmdd + seq";
	wp40.wp_max_socket = 4;
	wp40.wp_img='wp040.png';

var wp41 = {};
	wp41.wp_id = 'wp041';
	wp41.wp_name_kr = '1등 신의 이빨';
	wp41.wp_name_en = '1st god\'s teeth';
	wp41.wp_desc = ' 1등 신의 이빨은 무척 단단하며, 제련하기 어렵다.</br>(강화확률-10%)(고유)(파괴불가)';
	wp41.wp_org_damage = 120;
	wp41.wp_add_damage = 0;
	wp41.wp_adv_no = 0;
	wp41.wp_key ="yyyymmdd + seq";
	wp41.wp_max_socket = 3;
	wp41.wp_img='wp041.png';	
	

var wp50 = {};
	wp50.wp_id = 'wp050';
	wp50.wp_name_kr = '전설무기';
	wp50.wp_desc = '전설속에만 존재하는 무기이다.</br>(고유)(획득불가)(파괴불가)';
	wp50.wp_org_damage = 1300;
	wp50.wp_add_damage = 0;
	wp50.wp_adv_no = 0;
	wp50.wp_key ="yyyymmdd + seq";
	wp50.wp_max_socket = 4;
	wp50.wp_img='wp050.png';

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
				full_text+= this[Str].wp_name+' ( + '+this[Str].wp_adv_no+' )'+'</br>';
				full_text+= this[Str].wp_desc+'</br>';
				full_text+= ""+'</br>';
				full_text+= "데미지 "+this[Str].wp_org_damage+' ( + '+this[Str].wp_add_damage+' )'+'</br>';
				full_text+= "최대소켓 "+this[Str].wp_max_socket+'</br>';
			return full_text;
		}
};
