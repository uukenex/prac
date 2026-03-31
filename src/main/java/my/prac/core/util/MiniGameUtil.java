package my.prac.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import my.prac.core.game.dto.EquipCategory;
import my.prac.core.game.dto.JobChangeReq;
import my.prac.core.game.dto.JobDef;
import my.prac.core.game.dto.SpecialBuffOption;

public class MiniGameUtil {
	private static final String NL = "♬";
	public static final Map<Integer, Double[]> RATE_MAP_WEAPON = new HashMap<>();
	public static final Map<Integer, Double[]> RATE_MAP_ACC = new HashMap<>();
	public static final Map<Integer, int[]> POW_MAP_ACC = new HashMap<>();
	public static final Map<Integer, int[]> GOLD_MAP_ACC = new HashMap<>();
	public static String[] hideRules = { "아침","점심", "저녁", "새벽" };
	public static String[] PatternRules = { "대기","공격", "방어", "필살기" };
	public static final Map<Integer, int[]> MON_PATTERN_WEIGHTS = new HashMap<>();
	
	public static final LinkedHashMap<Integer, Integer> ACHV_REWARD_MAP = new LinkedHashMap<>();
	public static final Map<String,String> SLOT_MAP = new HashMap<>();
	public static final List<EquipCategory> EQUIP_CATEGORIES = new ArrayList<>();

// 직업 메타데이터 맵 (등록 순서 유지 위해 LinkedHashMap)
	public static final Map<String, JobDef> JOB_DEFS = new LinkedHashMap<>();

	static {
	    JOB_DEFS.put("전사", new JobDef(
	        "전사",
	        "⚔용감한 전사, 누구보다 앞서 싸운다",
	        "▶[기본기강화] 공격 배율 140%"+NL
	       +"▶[방어태세] 몬스터레벨에 따라 방어도 추가"+NL
	       +"▶[패링] 적의 필살기를 반격(20%)"+NL
	    ));
	    
	    JOB_DEFS.put("궁수", new JobDef(
    	    "궁수",
    	    "⚔사냥감을 조준하는 집요한 추적자",
    	    "▶[기본기강화] 공격배율 300%"+NL
    	   +"▶[사냥꾼의경험] EXP +100%"+NL
    	   +"▶[저격] 공격 시 7% 확률 강력한 공격 (7배 데미지)"+NL
    	   +"▶[재장전] 공격 쿨타임 2분 -> 10분"
    	));

    	JOB_DEFS.put("마법사", new JobDef(
    	    "마법사",
    	    "⚔강력한 마법으로 적의 방어를 무력화한다",
    	    "▶[화염구] 몬스터 방어시 방어를 무시하고 100% 추가데미지"+NL
    	   +"▶[마나실드] 몬스터 필살기 시 피해 30% 감소"+NL
    	));

    	JOB_DEFS.put("도적", new JobDef(
    	    "도적",
    	    "⚔날렵하게 공격을 회피하고 아이템을 훔친다",
    	    "▶[스틸] 공격 시 40% 확률 추가 드랍"+NL
    	   +"▶[도적의회피] 몬스터 기본 공격 80% 회피"+NL
    	));
	    
        JOB_DEFS.put("궁사", new JobDef(
    	    "궁사",
    	    "⚔연속공격의 달인",
    	    "▶[기본기강화] 공격배율 120%"+NL
    	   +"▶[연사] (최대데미지-최소데미지)/10(최소280) 마다 연사(최대5연사)"+NL
    	   +"▶[정밀연사] 연사 화살 전체크리시 추가데미지 30%(개별치명타율 최대 80%)"+NL
    	));

        JOB_DEFS.put("용사", new JobDef(
    	    "용사",
    	    "⚔선택 받은 자",
    	    "▶[기본기강화] 공격배율 140%, HP배율 200%"+NL
    	   +"▶[빛의용사] 어둠 몬스터 추가피해 +50%, 언데드 몬스터 추가피해 +25%, 받는 데미지 50% 감소"+NL
    	   +"▶[가난한용사] 공격 시 60% 확률 추가 드랍"+NL
    	));
	    
        JOB_DEFS.put("처단자", new JobDef(
    	    "처단자",
    	    "⚔신을 모독하는 자, 처단한다",
    	    "▶[기본기강화] 공격 배율 140%"+NL
    	   +"▶[화염철퇴] 몬스터 방어시 방어를 무시하고 150% 추가데미지"+NL
    	   +"▶[처단] 처치 시 추가드랍(OVERKILL 시 추가 획득,최대7개)"+NL
    	   +"▶[이단심문] 빛 몬스터 추가피해 +50%"+NL
    	));
        
        JOB_DEFS.put("검성", new JobDef(
    	    "검성",
    	    "⚔검으로 세상 끝에 닿은 자",
    	    "▶[기본기강화] 공격배율 250%, HP배율 200%"+NL
    	   +"▶[패링] 적의 모든공격을 반격(15%)"+NL
    	));
	    
        JOB_DEFS.put("어둠사냥꾼", new JobDef(
    	    "어둠사냥꾼",
    	    "⚔어둠이 있기에 그가 있다",
    	    "▶[기본기강화] 아이템으로상승하는 HP/리젠 효과 +25%"+NL
    	   +"▶[기척차단] 몬스터 일반공격 받는 피해 30% 감소"+NL
    	   +"▶[어둠사냥] 언데드 몬스터 추가피해 +75%"+NL
    	   +"▶[어둠사냥II] 어둠 몬스터 추가피해 +150%"+NL
    	   +"▶[기습] 강제 전투종료 패턴 무시 후 추가데미지"+NL
    	));
        
        JOB_DEFS.put("복수자", new JobDef(
    	    "복수자",
    	    "⚔원념으로 되돌려주는 복수자",
    	    "▶[기본기강화] 공격배율 20%"+NL
    	   +"▶[강화육체] 받는 피해 75% 감소"+NL
    	   +"▶[베르그아베스타] 몬스터 일반,필살 공격 피해반사, 남은체력 추가데미지"+NL
    	));
	    
        JOB_DEFS.put("도박사", new JobDef(
    	    "도박사",
    	    "⚔모든 것을 운에 건 승부사",
    	    "▶[도박] 공격,피격 시 도박하여 공격,회피"+NL
    	));
	    
        JOB_DEFS.put("음양사", new JobDef(
    	    "음양사",
    	    "☯음양의 이치를 깨달은 도사",
    	    "▶[기본기강화] 공격 배율 160%"+NL
    	   +"▶[강화] 공격 시 다음 아군 1명 강화"+NL
    	   +"▶[회복] 공격 시 자신에게 힐 회복"+NL
    	   +"▶[음양조화] 음양 몬스터 출현"+NL
    	));

        JOB_DEFS.put("헌터", new JobDef(
    	    "헌터",
    	    "⚔이세계에서 넘어온 실력자",
    	    "▶[헌터의힘] 공격횟수의 최대 20%만큼 공격력 증가"+NL
    	   +"▶[헌터의체력] 아이템 드랍 획득 수의 최대 20%만큼 체력 증가"+NL
    	   +"▶[헌터의회복] 아이템 드랍 획득 수의 최대 2%만큼 리젠 증가"+NL
    	   +"▶[헌터의죽음] 죽음횟수의 최대 20%만큼 치명타데미지 증가"+NL
    	   +"▶[헌터의치명] 치명타확률 100% 초과분은 치명타데미지로 전환"+NL
    	   +"▶[헌터의자격] 헌터 공격횟수에 따라 등급산정 시 하향적용"
    	));
        
        JOB_DEFS.put("축복술사", new JobDef(
    	    "축복술사",
    	    "✨당신을 축복합니다",
    	    "▶[축복] 공격 시 플레이어 무작위 1명에게 축복 부여 : 회복&데미지증가"+NL
    	   +"▶[추가축복] 100레벨마다 축복 대상 1명 추가"+NL
    	   +"▶[축복휴유증] 공격 쿨타임 30분, 직업변경 불가시간 30분"+NL
    	));

    	JOB_DEFS.put("곰", new JobDef(
    	    "곰",
    	    "⚔만나면 도망가시오",
    	    "▶[곰의힘] 공격력과 치명타데미지를 체력으로 전환"+NL
    	   +"▶[괴력] 자신보다 체력이 낮은 몬스터 즉사"+NL
    	   +"▶[출혈] 공격 시 최대체력의 10% 소모"+NL
    	   +"▶[달의힘] 공격 시 10% 확률로 달의힘을 받아 특수한 힘을 발현"
    	));
	    
	    /*
	    JOB_DEFS.put("프리스트", new JobDef(
    		"프리스트",
    		"▶ 대사제의 축복을 받아 신성의힘으로 적을 물리친다",
    		"⚔ 아이템 HP/리젠 효과 1.25배, 몬스터에게 받는 일반공격 피해 감소(20%), 언데드추가피해(+25%)"
		));
	    */
	    /*
	    JOB_DEFS.put("도사", new JobDef(
	        "도사",
	        "▶ 도를 닦아 깨달음을 얻은 위인",
	        "⚔ 다음 공격하는 아군 강화(레벨*0.5만큼 능력강화,맥뎀*0.1만큼 치명뎀강화,"+NL+"매턴 공격시 자신 회복,자신의 럭키몬스터 등장 확률 증가"
	    ));
	    */
	    /*
        JOB_DEFS.put("사신", new JobDef(
            "사신",
            "▶ 이름하야 죽음의 신, 죽지않는다",
            "⚔ 드랍율-30%, 체력 0에서도 죽지 않음, 다크 몬스터 조우 불가"
        ));*/
        /*
        JOB_DEFS.put("흡혈귀", new JobDef(
            "흡혈귀",
            "▶ 배가고프다, 나는 배가 고프다!",
            "⚔ 공격시 준피해의 20% 흡혈(공격&흡혈 선계산, 후피해)[max: 최대체력의20%], hp리젠 아이템의 증감처리 미적용"
        ));
        */
	    /*
        JOB_DEFS.put("용기사", new JobDef(
    		"용기사",
    		"▶ 용족의 마지막 후예, 배신당한 아픔을 가지고 있다",
    		"⚔ 아이템 HP/리젠 효과 2배, 100% 초과 치명타확률, 기본 치명타 데미지 초과분을 공격력으로 전환,치명타가 발생하지않음, 용족에 5배의 피해"
        ));
        */
        /*
        JOB_DEFS.put("파이터", new JobDef(
    		"파이터",
    		"▶ 강인한 체력의 소유자, 체력이 낮아지면 적의 행동을 저지시킨다",
    		"⚔ 공격력 최대치, 치명타 배율 및 치명타데미지 증가가 체력으로 전환(3배수,치명 미발생)"+NL+"본인의 체력이 낮아질수록 데미지 증가(추가 50%까지), 체력이 30%이하 일 때 적 행동저지(40%)"
        ));
        */
        /*
        JOB_DEFS.put("궁사2", new JobDef(
    		"궁사2",
    		"▶ 연속공격의 달인, 최대데미지와 최소공격력 차이가 클수록 연속공격한다(테스트모드)",
    		"⚔ 최대-최소 데미지 차이 280 마다 1연사 추가공격(추가공격데미지 고정)"
		));
        */
	    /*
        JOB_DEFS.put("저격수", new JobDef(
    		"저격수",
    		"▶ 숨어서 급소를 노리는 암살자, 극강의 공격력을 선사한다",
    		"⚔ 기본공격데미지(+100%), 공격력이 항상 중간값으로 고정, 최대체력-50%"+NL+
    		  "*조우 은엄폐 이후, *저격 - *이동 패턴을 반복"+NL+
    		  "*조우 은엄폐, *저격(13% headShot) 시 모든 행동 무시, *이동 시 20%확률 모든 행동 무시"
        ));
        */
    	/*
	    JOB_DEFS.put("제너럴", new JobDef(
	        "제너럴",
	        "▶ 블랙필드에서는 누구도 따라잡을자가 없다!",
	        "⚔ 조우시 (*은엄폐-저격 or *회피기동전술) 이후 *회피기동전술을 다회 반복"+NL
	        +"*조우 은엄폐(공격x or 폭격[hidden]), *저격(13% headShot) 시 모든 행동 무시, *회피기동전술 시 - hidden -,기본공격력 * 1.2"+NL
	        +"◎선행조건 저격수,전사 직업으로 각 150회 공격"
	    ));*/
    	/*
	    JOB_DEFS.put("어쎄신", new JobDef(
    		"어쎄신",
    		"▶ 그의 암습은 누구도 피할수없다.상대가 누구일 지라도",
    		"⚔ 공격 시 STEAL(30%,100킬 당 5%씩 증가,max 80%), 몬스터 기본 공격 회피, 필살기를 확률 회피, 기본데미지*1.3"+NL
    		+"◎선행조건 도적 직업으로 1000회 공격"
		));
	    */
	}
		
	// 목표직업 -> 요구조건 리스트
	public static final Map<String, List<JobChangeReq>> JOB_CHANGE_REQS = new HashMap<>();
	// 목표직업 -> 전체 공격 횟수 요구
	public static final Map<String, Integer> JOB_CHANGE_TOTAL_REQS = new HashMap<>();
	
	static {
	    // 용사 = 전사 300회 + 도적 300회 공격해야 전직 가능
	    JOB_CHANGE_REQS.put("용사", Arrays.asList(
	        new JobChangeReq("전사", 150),
	        new JobChangeReq("도적", 150)
	        //new JobChangeReq("도사", 150)
	        //new JobChangeReq("프리스트", 150)
	    ));
	    JOB_CHANGE_REQS.put("처단자", Arrays.asList(
    		new JobChangeReq("마법사", 150),
    		new JobChangeReq("도적", 150)
		));
	    /*
	    JOB_CHANGE_REQS.put("제너럴", Arrays.asList(
    		new JobChangeReq("저격수", 150),
    		new JobChangeReq("전사", 150)
		));*/
	    JOB_CHANGE_REQS.put("검성", Arrays.asList(
    		new JobChangeReq("전사", 1000)
		));
	    /*
	    JOB_CHANGE_REQS.put("어쎄신", Arrays.asList(
	    	new JobChangeReq("도적", 1000)
		));*/
	    JOB_CHANGE_REQS.put("어둠사냥꾼", Arrays.asList(
	    	//new JobChangeReq("프리스트", 150),
	    	//new JobChangeReq("용기사", 150)
		));
	    JOB_CHANGE_REQS.put("복수자", Arrays.asList(
			new JobChangeReq("전사", 100)
			//new JobChangeReq("저격수", 100)
		));
	    JOB_CHANGE_REQS.put("도박사", Arrays.asList(
    		new JobChangeReq("어둠사냥꾼", 100),
    		new JobChangeReq("복수자", 100)
		));
	    JOB_CHANGE_REQS.put("음양사", Arrays.asList(
	    	//new JobChangeReq("도사", 1000)
		));
	    
	    // 용사 = 전체 공격 1000회 이상
	    JOB_CHANGE_TOTAL_REQS.put("궁사", 3000);
	    
	}
		
	public static final List<SpecialBuffOption> SPECIAL_BUFF_OPTIONS = Arrays.asList(
		    new SpecialBuffOption(10, "가방", "배율"),
		    new SpecialBuffOption(30, "공격력", "배율"),
		    new SpecialBuffOption(20, "치피", "고정값"),
		    new SpecialBuffOption(15, "치확", "고정값"),
		    new SpecialBuffOption(7,  "쿨타임", "고정값"),
		    new SpecialBuffOption(3,  "나메가방", "고정값")
		);
	
	public static  SpecialBuffOption pickWeightedBuff(List<SpecialBuffOption> options) {
	    int totalWeight = 0;
	    for (SpecialBuffOption opt : options) {
	        totalWeight += opt.weight;
	    }

	    int r = ThreadLocalRandom.current().nextInt(totalWeight);

	    for (SpecialBuffOption opt : options) {
	        r -= opt.weight;
	        if (r < 0) {
	            return opt;
	        }
	    }

	    return options.get(0);
	}
	
	static {

		EQUIP_CATEGORIES.add(
		        new EquipCategory("무기", 5,
		            new int[][]{{100,200},{1100,1200},{2100,2200}},
		            "무기","진무기","극무기"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("투구", 1,
		            new int[][]{{200,300},{1200,1300}},
		            "투구","진투구"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("행운", Integer.MAX_VALUE,
		            new int[][]{{300,400}},
		            "행운"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("갑옷", 1,
		            new int[][]{{400,500},{1400,1500}},
		            "갑옷","진갑옷"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("반지", Integer.MAX_VALUE,
		            new int[][]{{500,600}},
		            "반지"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("토템", Integer.MAX_VALUE,
		            new int[][]{{600,700}},
		            "토템"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("전설", 1,
		            new int[][]{{700,800}},
		            "전설"
		        )
		    );

		    EQUIP_CATEGORIES.add(
		        new EquipCategory("날개", 1,
		            new int[][]{{800,900}},
		            "날개"
		        )
		    );
		    
		    EQUIP_CATEGORIES.add(
		        new EquipCategory("선물", Integer.MAX_VALUE,
		            new int[][]{{900,1000}},
		            "선물"
		        )
		    );
		    EQUIP_CATEGORIES.add(
		        new EquipCategory("물약", 1,
		            new int[][]{{1000,1100}},
		            "물약"
		        )
		    );

	}
	
	public static String getPotionOptionText(int itemId){
	    switch(itemId){

	        case 1001:
	            return "부활 (HP 10% 회복)";

	        case 1002:
	            return "HP 50% 회복";

	        case 1003:
	            return "HP 100% 회복";

	        case 1004:
	            return "HP 10000 회복";

	        case 1005:
	            return "HP 100000 회복";

	        case 1006:
	            return "HP 1000000 회복";
	    }

	    return "";
	}


	public static SP getPotionPrice(int itemId, SP totalSp){

		long total = SP.toBaseValue(totalSp);

	    switch(itemId){

	        case 1001:
	        case 1002:
	        case 1003:

	            long base;

	            switch(itemId){
	                case 1001: base = 50;  break;
	                case 1002: base = 200; break;
	                default:   base = 400; break;
	            }

	            double ratio = Math.sqrt((double)total / 600_000_000D);

	            long price = (long)(base * ratio);

	            return SP.of(price,"a");

	        case 1004: return SP.of(3,"a");
	        case 1005: return SP.of(30,"a");
	        case 1006: return SP.of(300,"a");
	    }

	    return SP.of(0,"");
	}
	
    public static long getPotionHeal(int itemId, long maxHp){

        switch(itemId){
	        case 1001:
	        	return (long)(maxHp * 0.1);
            case 1002:
                return (long)(maxHp * 0.5);
            case 1003:
                return maxHp;
            case 1004:
                return 10000;
            case 1005:
                return 100000;
            case 1006:
                return 1000000;
        }
        return 0;
    }

	
	public static boolean isInstantUseItem(int itemId){
	    return itemId >= 1001 && itemId <= 1100;
	}
	
	static {

	    SLOT_MAP.put("무기","※무기");
	    SLOT_MAP.put("투구","※투구");
	    SLOT_MAP.put("갑옷","※갑옷");
	    SLOT_MAP.put("반지","※반지");
	    SLOT_MAP.put("토템","※토템");
	    SLOT_MAP.put("행운","※행운");
	    SLOT_MAP.put("날개","※날개");
	    SLOT_MAP.put("전설","※전설");

	}
	static {
	    ACHV_REWARD_MAP.put(50 ,8001);
	    ACHV_REWARD_MAP.put(80 ,8002);
	    ACHV_REWARD_MAP.put(100,8003);
	    ACHV_REWARD_MAP.put(120,8004);
	    ACHV_REWARD_MAP.put(150,8005);
	    ACHV_REWARD_MAP.put(170,8006);
	    ACHV_REWARD_MAP.put(200,8007);
	    ACHV_REWARD_MAP.put(220,8008);
	    ACHV_REWARD_MAP.put(250,8009);
	    ACHV_REWARD_MAP.put(300,8010);
	    ACHV_REWARD_MAP.put(320,8011);
	    ACHV_REWARD_MAP.put(350,8012);
	    ACHV_REWARD_MAP.put(400,8013);
	    ACHV_REWARD_MAP.put(500,8014);
	    ACHV_REWARD_MAP.put(550,8015);
	    ACHV_REWARD_MAP.put(600,8016);
	}
	
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
	    MON_PATTERN_WEIGHTS.put(30,new int[]{0, 40, 20, 25,10,5}); 
	    
	    MON_PATTERN_WEIGHTS.put(31,new int[]{0, 40, 20, 25,10,5}); 
	    MON_PATTERN_WEIGHTS.put(32,new int[]{0, 30, 30, 25,10,5}); 
	    MON_PATTERN_WEIGHTS.put(33,new int[]{0, 40, 20, 25,10,5}); 
	    MON_PATTERN_WEIGHTS.put(34,new int[]{0, 30, 30, 25,10,5}); 
	    MON_PATTERN_WEIGHTS.put(35,new int[]{0, 40, 20, 25,10,5}); 
	    

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
