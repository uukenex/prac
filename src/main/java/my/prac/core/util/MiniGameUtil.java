package my.prac.core.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniGameUtil {
	public static final Map<Integer, Double[]> RATE_MAP_WEAPON = new HashMap<>();
	public static final Map<Integer, Double[]> RATE_MAP_ACC = new HashMap<>();
	
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
	}
	
	static {
	    RATE_MAP_ACC.put(0,  new Double[]{30.0, 60.0, 10.0});
	    RATE_MAP_ACC.put(1,  new Double[]{30.0, 60.0, 10.0});
	    RATE_MAP_ACC.put(2,  new Double[]{15.0, 75.0, 10.0});
	    RATE_MAP_ACC.put(3,  new Double[]{15.0, 75.0, 10.0});
	    RATE_MAP_ACC.put(4,  new Double[]{15.0, 75.0, 10.0});
	    RATE_MAP_ACC.put(5,  new Double[]{30.0, 50.0, 20.0});
	    RATE_MAP_ACC.put(6,  new Double[]{15.0, 75.0, 10.0});
	    RATE_MAP_ACC.put(7,  new Double[]{15.0, 70.0, 15.0});
	    RATE_MAP_ACC.put(8,  new Double[]{15.0, 65.0, 20.0});
	    RATE_MAP_ACC.put(9,  new Double[]{10.0, 72.0, 18.0});
	    RATE_MAP_ACC.put(10, new Double[]{10.0, 72.0, 18.0});
	    RATE_MAP_ACC.put(11, new Double[]{10.0, 72.0, 18.0});
	    RATE_MAP_ACC.put(12, new Double[]{7.0 , 74.4, 18.6});
	    RATE_MAP_ACC.put(13, new Double[]{5.0 , 76.0, 19.0});
	    RATE_MAP_ACC.put(14, new Double[]{3.0 , 77.6, 19.4});
	    RATE_MAP_ACC.put(15, new Double[]{1.0 , 79.2, 19.8});
	}



	
	
}
