package my.prac.core.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniGameUtil {
	public static final Map<Integer, Double[]> RATE_MAP_WEAPON = new HashMap<>();
	public static final Map<Integer, Double[]> RATE_MAP_ACC = new HashMap<>();
	public static final Map<Integer, int[]> POW_MAP_ACC = new HashMap<>();
	public static final Map<Integer, int[]> GOLD_MAP_ACC = new HashMap<>();
	public static String[] hideRules = { "아침","점심", "저녁", "새벽" };
	public static String[] PatternRules = { "대기","공격", "방어", "필살기" };
	public static final Map<Integer, int[]> MON_PATTERN_WEIGHTS = new HashMap<>();
	static {
		//대기,공격,방어,필살,히든
	    MON_PATTERN_WEIGHTS.put(1, new int[]{100});
	    MON_PATTERN_WEIGHTS.put(2, new int[]{20, 80});
	    MON_PATTERN_WEIGHTS.put(3, new int[]{20, 80});
	    MON_PATTERN_WEIGHTS.put(4, new int[]{20, 80});
	    MON_PATTERN_WEIGHTS.put(5, new int[]{20, 80});
	    MON_PATTERN_WEIGHTS.put(6, new int[]{20, 50, 30});
	    MON_PATTERN_WEIGHTS.put(7, new int[]{20, 50, 30});
	    MON_PATTERN_WEIGHTS.put(8, new int[]{20, 50, 30});
	    MON_PATTERN_WEIGHTS.put(9, new int[]{20, 50, 30});
	    MON_PATTERN_WEIGHTS.put(10,new int[]{0, 60, 25, 15});
	    MON_PATTERN_WEIGHTS.put(11,new int[]{0, 60, 25, 15});
	    MON_PATTERN_WEIGHTS.put(12,new int[]{0, 60, 25, 15});
	    MON_PATTERN_WEIGHTS.put(13,new int[]{0, 60, 25, 15});
	    MON_PATTERN_WEIGHTS.put(14,new int[]{0, 60, 25, 15});
	    MON_PATTERN_WEIGHTS.put(15,new int[]{0, 62, 7, 26,5});
	    MON_PATTERN_WEIGHTS.put(16,new int[]{0, 62, 12, 26,0});
	    MON_PATTERN_WEIGHTS.put(17,new int[]{0, 10, 80, 10,0});
	    MON_PATTERN_WEIGHTS.put(18,new int[]{0, 33, 33, 33, 1});
	    MON_PATTERN_WEIGHTS.put(19,new int[]{0, 85, 0, 10, 5});//공격특화 
	    MON_PATTERN_WEIGHTS.put(20,new int[]{0, 10, 0, 85, 5});//필살특화 
	    MON_PATTERN_WEIGHTS.put(21,new int[]{80,10, 0, 10, 0}); 
	    MON_PATTERN_WEIGHTS.put(22,new int[]{0, 40,40, 10,10}); 
	    MON_PATTERN_WEIGHTS.put(23,new int[]{0, 50, 0, 50, 0}); 
	    MON_PATTERN_WEIGHTS.put(24,new int[]{0, 30,50, 20, 0}); 
	    MON_PATTERN_WEIGHTS.put(25,new int[]{30, 5,30,  5,30});//중간보스
	    MON_PATTERN_WEIGHTS.put(26,new int[]{0, 55, 0, 38, 5,2}); 
	    MON_PATTERN_WEIGHTS.put(27,new int[]{0, 48, 30,20, 0,2}); 
	    MON_PATTERN_WEIGHTS.put(28,new int[]{0, 18, 60, 15,5,2}); 
	    MON_PATTERN_WEIGHTS.put(29,new int[]{0, 70, 10, 10,5,5}); 
	    MON_PATTERN_WEIGHTS.put(30,new int[]{0, 20, 20, 20,20,20}); 
	    

	    // 필요할 때마다 여기 계속 추가하면 됨
	}
	
	//몬스터체력,공격력,경험치,드랍템,드랍템가격
	public static final Map<Integer, Object[]> MON_SPEC = new HashMap<>();
	
	static {
		//1레벨,토끼              체력 / 공격력 / 경험치 / 드랍템 / 드랍템가격 / 패턴갯수
		MON_SPEC.put(1,  new Object[]{ 5   /*체력*/, 0  /*공격력*/, 5    /*경험치*/ ,"토끼고기"/*드랍템*/, 1   /*드랍템가격*/,1/*패턴갯수*/});/*토끼*/
		MON_SPEC.put(2,  new Object[]{ 20  /*체력*/, 2  /*공격력*/, 10   /*경험치*/ ,"도토리" /*드랍템*/, 2   /*드랍템가격*/,2/*패턴갯수*/});/*다람쥐*/
		MON_SPEC.put(3,  new Object[]{ 50  /*체력*/, 5  /*공격력*/, 15   /*경험치*/ ,"쥐고기" /*드랍템*/, 3   /*드랍템가격*/,2/*패턴갯수*/});/*쥐*/
		MON_SPEC.put(4,  new Object[]{ 90  /*체력*/, 9  /*공격력*/, 20   /*경험치*/ ,"뱀고기" /*드랍템*/, 5   /*드랍템가격*/,2/*패턴갯수*/});/*뱀*/
		MON_SPEC.put(5,  new Object[]{ 140 /*체력*/, 14 /*공격력*/, 25   /*경험치*/ ,"사슴고기"/*드랍템*/, 10  /*드랍템가격*/,2/*패턴갯수*/});/*사슴*/
		MON_SPEC.put(6,  new Object[]{ 200 /*체력*/, 20 /*공격력*/, 30   /*경험치*/ ,"곰고기" /*드랍템*/, 20  /*드랍템가격*/,3/*패턴갯수*/});/*곰*/
		MON_SPEC.put(7,  new Object[]{ 300 /*체력*/, 30 /*공격력*/, 35   /*경험치*/ ,"여우고기"/*드랍템*/, 25  /*드랍템가격*/,3/*패턴갯수*/});/*여우*/
		MON_SPEC.put(8,  new Object[]{ 420 /*체력*/, 42 /*공격력*/, 40   /*경험치*/ ,"돼지고기"/*드랍템*/, 30  /*드랍템가격*/,3/*패턴갯수*/});/*돼지*/
		MON_SPEC.put(9,  new Object[]{ 550 /*체력*/, 55 /*공격력*/, 45   /*경험치*/ ,null   /*드랍템*/, 0   /*드랍템가격*/,3/*패턴갯수*/});/*사마귀*/
		MON_SPEC.put(10, new Object[]{ 700 /*체력*/, 70 /*공격력*/, 50   /*경험치*/ ,null   /*드랍템*/, 0   /*드랍템가격*/,3/*패턴갯수*/});/*인형*/
	}
	
	static {
	  //0강화에서 시도-> 성공률15%, 장기 쌓이는양 20% 
	    RATE_MAP_WEAPON.put(0,  new Double[]{100.0, 100.0});
	    RATE_MAP_WEAPON.put(1,  new Double[]{90.0, 100.0});
	    RATE_MAP_WEAPON.put(2,  new Double[]{80.0, 100.0});
	    RATE_MAP_WEAPON.put(3,  new Double[]{70.0, 100.0});
	    RATE_MAP_WEAPON.put(4,  new Double[]{60.0, 100.0});
	    RATE_MAP_WEAPON.put(5,  new Double[]{50.0, 50.0});
	    RATE_MAP_WEAPON.put(6,  new Double[]{40.0, 50.0});
	    RATE_MAP_WEAPON.put(7,  new Double[]{30.0, 33.3});
	    RATE_MAP_WEAPON.put(8,  new Double[]{20.0, 19.9});
	    RATE_MAP_WEAPON.put(9,  new Double[]{10.0, 11.1});
	    RATE_MAP_WEAPON.put(10, new Double[]{6.0, 9.9});
	    RATE_MAP_WEAPON.put(11, new Double[]{5.0, 8.8});
	    RATE_MAP_WEAPON.put(12, new Double[]{4.5, 7.7});
	    RATE_MAP_WEAPON.put(13, new Double[]{4.0, 6.6});
	    RATE_MAP_WEAPON.put(14, new Double[]{3.5, 5.5});
	    RATE_MAP_WEAPON.put(15, new Double[]{3.0, 4.4});
	    RATE_MAP_WEAPON.put(16, new Double[]{2.5, 3.3});
	    RATE_MAP_WEAPON.put(17, new Double[]{2.0, 2.28});
	    RATE_MAP_WEAPON.put(18, new Double[]{1.5, 2.13});
	    RATE_MAP_WEAPON.put(19, new Double[]{1.0, 2.03});
	    RATE_MAP_WEAPON.put(20, new Double[]{0.9, 1.99});
	    RATE_MAP_WEAPON.put(21, new Double[]{0.8, 1.88});
	    RATE_MAP_WEAPON.put(22, new Double[]{0.7, 1.77});
	    RATE_MAP_WEAPON.put(23, new Double[]{0.6, 1.66});
	    RATE_MAP_WEAPON.put(24, new Double[]{0.5, 1.55});
	    RATE_MAP_WEAPON.put(25, new Double[]{0.4, 1.44});
	    RATE_MAP_WEAPON.put(26, new Double[]{0.3, 1.33});
	    RATE_MAP_WEAPON.put(27, new Double[]{0.2, 1.22});
	    RATE_MAP_WEAPON.put(28, new Double[]{0.1, 1.11});
	    RATE_MAP_WEAPON.put(29, new Double[]{0.1, 1.00});
	    RATE_MAP_WEAPON.put(30, new Double[]{0.1, 0.93});
	    //30에서의 시도 -> 성공률 0.1, 장기백 0.93
	    RATE_MAP_WEAPON.put(31, new Double[]{0.1, 0.80});
	    RATE_MAP_WEAPON.put(32, new Double[]{0.1, 0.70});
	    RATE_MAP_WEAPON.put(33, new Double[]{0.1, 0.60});
	    RATE_MAP_WEAPON.put(34, new Double[]{0.1, 0.50});
	    RATE_MAP_WEAPON.put(35, new Double[]{0.1, 0.40});
	}
	
	static {
		//0->1시도시, 30%성공 60%실패 10%파괴 ,
	    RATE_MAP_ACC.put(0,  new Double[]{30.0, 70.0-0.0 , 0.0});
	    RATE_MAP_ACC.put(1,  new Double[]{30.0, 70.0-0.0 , 0.0});
	    RATE_MAP_ACC.put(2,  new Double[]{25.0, 75.0-0.0 , 0.0});
	    RATE_MAP_ACC.put(3,  new Double[]{25.0, 75.0-9.0 , 9.0});
	    RATE_MAP_ACC.put(4,  new Double[]{20.0, 80.0-11.0, 11.0});
	    RATE_MAP_ACC.put(5,  new Double[]{20.0, 80.0-13.0, 13.0});
	    RATE_MAP_ACC.put(6,  new Double[]{15.0, 85.0-15.0, 15.0});
	    RATE_MAP_ACC.put(7,  new Double[]{15.0, 85.0-17.0, 17.0});
	    RATE_MAP_ACC.put(8,  new Double[]{10.0, 90.0-19.0, 19.0});
	    RATE_MAP_ACC.put(9,  new Double[]{10.0, 90.0-21.0, 21.0});
	    RATE_MAP_ACC.put(10, new Double[]{5.0,  95.0-23.0, 23.0});
	    RATE_MAP_ACC.put(11, new Double[]{5.0,  95.0-25.0, 25.0});
	    RATE_MAP_ACC.put(12, new Double[]{3.0 , 97.0-27.0, 27.0});
	    RATE_MAP_ACC.put(13, new Double[]{3.0 , 97.0-29.0, 29.0});
	    RATE_MAP_ACC.put(14, new Double[]{1.0 , 99.0-31.0, 31.0});
	    RATE_MAP_ACC.put(15, new Double[]{1.0 , 99.0-33.0, 33.0});
	}
	static {
		//0강일때 파워: 크리0 민뎀0 맥뎀3,방어력
		POW_MAP_ACC.put(0,   new int[]{0 , 0, 0, 0});
		POW_MAP_ACC.put(1,   new int[]{1 , 0, 1, 1});
		POW_MAP_ACC.put(2,   new int[]{2 , 0, 2, 1});
		POW_MAP_ACC.put(3,   new int[]{3 , 0, 4, 1});
		POW_MAP_ACC.put(4,   new int[]{4 , 0, 6, 2});
		POW_MAP_ACC.put(5,   new int[]{5 , 0, 9, 2});
		POW_MAP_ACC.put(6,   new int[]{6 , 0, 12,2});
		POW_MAP_ACC.put(7,   new int[]{7 , 0, 16,3});
		POW_MAP_ACC.put(8,   new int[]{8 , 0, 20,3});
		POW_MAP_ACC.put(9,   new int[]{9 , 0, 25,3});
		POW_MAP_ACC.put(10,  new int[]{10, 0, 30,4});
		POW_MAP_ACC.put(11,  new int[]{11, 0, 36,4});
		POW_MAP_ACC.put(12,  new int[]{12, 0, 42,4});
		POW_MAP_ACC.put(13,  new int[]{13, 0, 49,5});
		POW_MAP_ACC.put(14,  new int[]{14, 0, 56,5});
		POW_MAP_ACC.put(15,  new int[]{15, 0, 64,5});
	}


	static {
		//0강일때 파워: 크리0 민뎀0 맥뎀3
		GOLD_MAP_ACC.put(0,   new int[]{50});
		GOLD_MAP_ACC.put(1,   new int[]{100});
		GOLD_MAP_ACC.put(2,   new int[]{100});
		GOLD_MAP_ACC.put(3,   new int[]{150});
		GOLD_MAP_ACC.put(4,   new int[]{150});
		GOLD_MAP_ACC.put(5,   new int[]{200});
		GOLD_MAP_ACC.put(6,   new int[]{200});
		GOLD_MAP_ACC.put(7,   new int[]{250});
		GOLD_MAP_ACC.put(8,   new int[]{250});
		GOLD_MAP_ACC.put(9,   new int[]{300});
		GOLD_MAP_ACC.put(10,  new int[]{300});
		GOLD_MAP_ACC.put(11,  new int[]{350});
		GOLD_MAP_ACC.put(12,  new int[]{350});
		GOLD_MAP_ACC.put(13,  new int[]{400});
		GOLD_MAP_ACC.put(14,  new int[]{400});
		GOLD_MAP_ACC.put(15,  new int[]{450});
	}

	
	
}
