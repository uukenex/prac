package my.prac.core.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniGameUtil {
	public static final Map<Integer, Double[]> RATE_MAP = new HashMap<>();
	
	static {
	  //0강화에서 시도-> 성공률15%, 장기 쌓이는양 20% 
	    RATE_MAP.put(0,  new Double[]{100.0, 100.0});
	    RATE_MAP.put(1,  new Double[]{90.0, 100.0});
	    RATE_MAP.put(2,  new Double[]{80.0, 100.0});
	    RATE_MAP.put(3,  new Double[]{70.0, 100.0});
	    RATE_MAP.put(4,  new Double[]{60.0, 100.0});
	    RATE_MAP.put(5,  new Double[]{50.0, 50.0});
	    RATE_MAP.put(6,  new Double[]{40.0, 50.0});
	    RATE_MAP.put(7,  new Double[]{30.0, 33.3});
	    RATE_MAP.put(8,  new Double[]{20.0, 19.9});
	    RATE_MAP.put(9,  new Double[]{10.0, 11.1});
	    RATE_MAP.put(10, new Double[]{6.0, 9.9});
	    RATE_MAP.put(11, new Double[]{5.0, 8.8});
	    RATE_MAP.put(12, new Double[]{4.5, 7.7});
	    RATE_MAP.put(13, new Double[]{4.0, 6.6});
	    RATE_MAP.put(14, new Double[]{3.5, 5.5});
	    RATE_MAP.put(15, new Double[]{3.0, 4.4});
	    RATE_MAP.put(16, new Double[]{2.5, 3.3});
	    RATE_MAP.put(17, new Double[]{2.0, 2.28});
	    RATE_MAP.put(18, new Double[]{1.5, 2.13});
	    RATE_MAP.put(19, new Double[]{1.0, 2.03});
	    RATE_MAP.put(20, new Double[]{0.9, 1.99});
	    RATE_MAP.put(21, new Double[]{0.8, 1.88});
	    RATE_MAP.put(22, new Double[]{0.7, 1.77});
	    RATE_MAP.put(23, new Double[]{0.6, 1.66});
	    RATE_MAP.put(24, new Double[]{0.5, 1.55});
	    RATE_MAP.put(25, new Double[]{0.4, 1.44});
	    RATE_MAP.put(26, new Double[]{0.3, 1.33});
	    RATE_MAP.put(27, new Double[]{0.2, 1.22});
	    RATE_MAP.put(28, new Double[]{0.1, 1.11});
	    RATE_MAP.put(29, new Double[]{0.1, 1.00});
	    RATE_MAP.put(30, new Double[]{0.1, 0.93});
	    //30에서의 시도 -> 성공률 0.1, 장기백 0.93
	}
	
	
	public static void itemAddtionalFunction(HashMap<String, Object> map) throws Exception {
		
		int itemNo = Integer.parseInt(map.get("ITEM_NO").toString());
		int itemLv = Integer.parseInt(map.get("ITEM_LV").toString());
		
		/** 
		 ITEM_NO	ITEM_NAME	NOTE	ITEM_DESC	MAX_LV
				1	[부적]		"1레벨 : -50점을 상쇄하여 -25점만 받게함 2레벨 : -50점을 상쇄하여 0점만 받게함 3레벨 : -50점을 상쇄하여 10점을 획득"	주사위 시 마이너스 패널티가 감소한다	3
				2	[럭키세븐]	1레벨 0: -50 => 0 ,7=>77 , 77=>777	주사위 시 부가효과가 추가된다 	1
				3	[달고나]		1레벨: 30차이가 나면 화살표 두개로 표기	뽑기를 강화시켜준다	1
				
				4	[스카우터]	"1레벨 : 보스의 남은체력을 %로 보여줌(확률)  2레벨 : 보스의 남은체력을 %로 보여줌 3레벨 : 보스의 남은체력을 보여줌"	보스 공격시 비공개였던 체력을 알려준다	2
				5	[예리한칼날]	"1레벨 치확 5% 증가 2레벨 치확 10% 증가 3레벨 치확 15% 증가"	보스 공격시 치명타확률을 올려준다	3
				6	[야간투시경]	"1레벨 : 100%확률로 공격성공 "	보스가 숨어있는 시간에도 공격이 가능해진다	2 
				7	[덫]			"1레벨 : 5% 회피율 무시 2레벨 : 10% 회피율 무시"	보스의 회피율을 감소시킨다	4
				
				8	[테메르의 정]	1레벨당 2포인트 할인	강화 비용을 할인한다	5
				9	[더블어택]	"1레벨 : 10% 더블어택 2레벨 : 20% 더블어택 3레벨 : 30% 더블어택"	보스 공격시 두번 공격한다	3
				10	[시간의지배자]	"1레벨 : 10% 초기화 2레벨 : 20% 초기화 3레벨 : 30% 초기화"	보스 공격시 쿨타임이 초기화된다	3
				11	[황금모루]	"1레벨 : 기본강화율 1.5배 2레벨 : 기본강화율 2배"	무료 강화의 기본 강화율이 상승한다	2		  
		 * */
		
		
		switch(itemNo) {
			case 1://부적 
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
			case 8:
			case 9:
			case 10:
			case 11:
				map.put("additional_option", itemNo + "-" + itemLv);
				break;
		}
		
		
		
		return;
	}
	
	
}
