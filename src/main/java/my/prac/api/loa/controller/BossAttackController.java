package my.prac.api.loa.controller;


import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.AchievementConfig;
import my.prac.core.game.dto.AchievementCount;
import my.prac.core.game.dto.AttackCalc;
import my.prac.core.game.dto.AttackDeathStat;
import my.prac.core.game.dto.BagLog;
import my.prac.core.game.dto.BagRewardLog;
import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.DamageOutcome;
import my.prac.core.game.dto.EquipCategory;
import my.prac.core.game.dto.Flags;
import my.prac.core.game.dto.JobDef;
import my.prac.core.game.dto.KillStat;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.SpecialBuffOption;
import my.prac.core.game.dto.SpecialBuffResult;
import my.prac.core.game.dto.User;
import my.prac.core.game.dto.UserBattleContext;
import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;
import my.prac.core.util.MiniGameUtil;
import my.prac.core.util.SP;
import my.prac.core.util.SellResult;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/loa")
public class BossAttackController {

	/* ===== Config / Const ===== */
	private static final int COOLDOWN_SECONDS = 120; // 2분

	// [7013] 어제 공격자 수 일별 캐시
	private volatile int   _yestAttackerCount    = 0;
	private volatile String _yestAttackerDate     = "";
	private int getYesterdayAttackerCountCached() {
	    String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
	    if (!today.equals(_yestAttackerDate)) {
	        try { _yestAttackerCount = botNewService.selectYesterdayAttackerCount(); } catch (Exception e) { _yestAttackerCount = 0; }
	        _yestAttackerDate = today;
	    }
	    return _yestAttackerCount;
	}
	private static final int REVIVE_WAIT_MINUTES = 0;//쿼리에서계산함
	private static final String NL = "♬";
	// 🍀 Lucky: 전투 시작 시 10% 확률 고정(신규 전투에서만 결정)
	private static final double LUCKY_RATE = 0.15;
	private static final double LUCKY_RATE_DOSA = 0.20;
	private static final double DARK_RATE_DARK = 0.20;
	private static final String ALL_SEE_STR = "===";
	private static final int BAG_ITEM_ID = 91;
	private static final int BAG_NM_ITEM_ID = 92;
	private static final int BAG_HELL_ITEM_ID = 93;
	private static final double BAG_DROP_RATE = 0.035;//3.5%
	/** 가방 최대 보유 개수 설정 — 이 개수 이상 보유 중이면 드랍 즉시 자동 오픈 */
	private static final int BAG_MAX_HOLD = 300;


	private static final int NM_MUL_HP_ATK = 100;
	private static final int NM_MUL_EXP = 50;
	private static final int NM_ADD_MON_LV = 200;

	private static final int HEL_ADD_MON_LV = 500; // 
	private static final int HEL_MUL_EXP = 3;     // 헬 추가 배율 (나메에 추가 *3), 총 base*NM*HEL
	private static final long HEL_SP_MULT = 200; // 토끼(10sp) * 50 * 200 =  = 10a

    // [7000번대 보스 아이템 헬너프 면제]
    // true  = 7000번대 아이템(7001 천벌 등) ATK 스탯이 헬너프 적용 후 합산됨 (헬너프 미적용)
    // false = 기존 동작 (헬너프 이전 합산, 헬너프 영향받음)
    private static final boolean BOSS_ITEM_HELL_NERF_EXEMPT = false;

	// [FIX4] selectActiveSpecialBuff 단기 캐시 (서버 전역 버프는 15초간 재사용)
	private static volatile HashMap<String,Object> SPECIAL_BUFF_CACHE = null;
	private static volatile long SPECIAL_BUFF_CACHE_TS = 0L;
	private static final long SPECIAL_BUFF_CACHE_TTL_MS = 15_000L;

	// 장비랭킹 배치 캐시 (1시간마다 크론으로 갱신)
	private static volatile List<HashMap<String,Object>> EQUIP_RANK_CACHE = null;
	private static volatile long EQUIP_RANK_CACHE_TS = 0L;

	// 누적SP 1위 캐시 (1시간마다 갱신, 가방 최대금액 계산용)
	private static volatile long TOP1_SP_CACHE = 0L;
	private static volatile long TOP1_SP_CACHE_TS = 0L;
	private static final long TOP1_SP_CACHE_TTL_MS = 3_600_000L;

	// 다중 구매 시 insertPointRank 합산용 ThreadLocal (합산 모드일 때만 non-null)
	private static final ThreadLocal<SP[]> MULTI_BUY_COST_TL = new ThreadLocal<>();
	// 다중 판매 시 insertPointRank 합산용 ThreadLocal (합산 모드일 때만 non-null)
	private static final ThreadLocal<SP[]> MULTI_SELL_TOTAL_TL = new ThreadLocal<>();
	
	/* ===== DI ===== */
	@Lazy @Autowired BossAttackS3Controller bossAttackS3Controller;
	@Autowired LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")        BotService botService;
	@Resource(name = "core.prjbot.BotDAO")            BotDAO botDAO;
	@Resource(name = "core.prjbot.BotNewService")     BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")  BotSettleService botSettleService;
	
	
	
	
	public String changeMode(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String selRaw  = Objects.toString(map.get("param1"), "").trim();

	    String msg = "";
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    User u = botNewService.selectUser(userName, null);
	    if (u == null)
	        return "유저 정보를 찾을 수 없습니다.";

	    
	    boolean master = false;
	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				master =true;
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}
	    
	    
	    if(selRaw.equals("나이트메어")||selRaw.equals("나메")) {
	    	
	    	
	    	if(!master && !botNewService.isNightmareUnlocked(userName)) {
	    		return "나이트메어 모드 해금 조건 미달성!" + NL
	    		     + "조건: 일반모드에서 15번/25번/30번 몬스터 각 10마리 처치";
	    	}
	    	botNewService.setNightmareMode(userName,roomName,1);
	    	msg ="나이트메어";
	    }else if(selRaw.equals("헬")||selRaw.equals("헬모드")) {
	    	/*
	    	LocalDate today = LocalDate.now();
	    	
	    	if (today.isBefore(LocalDate.of(2026,4,16))) {
	    		return "헬 모드 해금 조건 미달성!" + NL
		    		     + "[4/16 00시 이후 아래 중 하나 충족 시 해금]" + NL
		    		     + "- 25번 몬스터 5마리 이상 처치" + NL
		    		     + "- 업적 400개 이상 보유" + NL
		    		     + "- 유물 아이템 27개 이상 보유" + NL
		    		     + "- 헌터 S급 이상";
	        }*/
	    	
	    	if(!master) {
	    		if(!botNewService.isHellUnlocked(userName)) {
		    		return "헬 모드 해금 조건 미달성!" + NL
		    		     + "- 25번 몬스터 1마리 처치" + NL
		    		     + "- 업적 350개 이상 보유" + NL
		    		     + "- 유물 아이템 25개 이상 보유";
		    	}
	    	}
	    	botNewService.setNightmareMode(userName,roomName,2);
	    	msg ="헬";
	    }else if(selRaw.equals("일반")){
	    	botNewService.setNightmareMode(userName,roomName,0);
	    	msg="일반";
	    }
	    // ── 모드 전환 시 체력 비율 유지 ──────────────────────────────
	    if (!msg.isEmpty()) {
	        try {
	            int baseHp = u.hpMax > 0 ? u.hpMax : my.prac.core.util.MiniGameUtil.calcBaseHpMax(u.lv);
	            double hellMult = my.prac.core.util.MiniGameUtil.getHellNerfMult(u.hunterGrade);
	            // 현재 모드의 유효 최대 체력
	            int curEffMax  = (u.nightmareYn == 2)
	                    ? Math.max(1, (int) Math.round(baseHp * hellMult))
	                    : baseHp;
	            double ratio = Math.min(1.0, Math.max(0.0, u.hpCur / (double) curEffMax));
	            // 전환 후 모드의 유효 최대 체력
	            int newMode    = "헬".equals(msg) ? 2 : ("나이트메어".equals(msg) ? 1 : 0);
	            int newEffMax  = (newMode == 2)
	                    ? Math.max(1, (int) Math.round(baseHp * hellMult))
	                    : baseHp;
	            int newHpCur = Math.min(newEffMax, Math.max(1, (int) Math.round(ratio * newEffMax)));
	            botNewService.updateUserHpOnlyTx(userName, roomName, newHpCur);
	        } catch (Exception ignore) {}
	    }
	    botNewService.closeOngoingBattleTx(userName, roomName);
		return msg+" 모드로 변경완료"+NL+"[일반/나이트메어/헬] 선택가능";
	}
	
	
	/*
	public String roulette(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");

	    if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";

	    // 문의방 제한 동일 패턴
	    if ("람쥐봇 문의방".equals(roomName) && !"일어난다람쥐/카단".equals(userName)) {
	        return "문의방에서는 불가능합니다.";
	    }

	    try {
	        // 1) 오늘 이미 돌렸는지 확인
	        HashMap<String, Object> today = botNewService.selectTodayDailyBuff(userName, roomName);
	        if (today != null && !today.isEmpty()) {
	            int atk  = safeInt(today.get("ATK_BONUS"));
	            int cdmg = safeInt(today.get("CRI_DMG_BONUS"));
	            return " " + userName + "님, 오늘은 이미 룰렛을 돌렸습니다." + NL
	                 + "오늘의 버프: ATK +" + atk + ", CDMG +" + cdmg + "%" + NL
	                 + "(자정에 초기화됩니다)";
	        }

	        // 2) 새로 뽑기
	        int atkBonus = ThreadLocalRandom.current().nextInt(10, 101);    // 10~100
	        int cdmgBonus = ThreadLocalRandom.current().nextInt(30, 301);  // 30~300

	        botNewService.upsertTodayDailyBuff(userName, roomName, atkBonus, cdmgBonus);

	        return " " + userName + "님, 룰렛 결과!" + NL
	             + "오늘의 버프: ATK +" + atkBonus + ", CDMG +" + cdmgBonus + "%" + NL
	             + "(자정에 초기화됩니다)";
	    } catch (Exception e) {
	        e.printStackTrace();
	        return "룰렛 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	    }
	}
	*/

	public String bagLog(HashMap<String, Object> map) {
		List<BagLog> logs = botNewService.selectRecentBagDrops();
		List<BagRewardLog> rewards = botNewService.selectRecentBagRewards();

		if ((logs == null || logs.isEmpty()) && (rewards == null || rewards.isEmpty())) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm");

		// 1) 가방 획득 로그 (기존)
		if (logs != null && !logs.isEmpty()) {
			sb.append("최근 가방 획득 로그 ").append(NL);
			for (BagLog log : logs) {
				String when = (log.getInsertDate() != null ? fmt.format(log.getInsertDate()) : "-");
				sb.append("- ").append(when).append(" : ").append(log.getUserName()).append("님이 가방을 획득~!").append(NL);
			}
			sb.append(NL);
		}

		// 2) 가방 보상 로그 (SP/아이템)
		if (rewards != null && !rewards.isEmpty()) {
			sb.append("최근 가방 보상 로그 ").append(NL);
			for (BagRewardLog r : rewards) {
				String when = (r.getInsertDate() != null ? fmt.format(r.getInsertDate()) : "-");
				sb.append("- ").append(when).append(" : ").append(r.getUserName()).append("님이 ").append(r.getGain())
						.append(" 획득!").append(NL);
			}
		}

		return sb.toString();
	}
	
	UserBattleContext calcUserBattleContext(HashMap<String, Object> map) {
	    UserBattleContext ctx = new UserBattleContext();

	    //final String roomName = Objects.toString(map.get("roomName"), "");
	    String userName = Objects.toString(map.get("userName"), "");
	    String param0   = Objects.toString(map.get("param0"), "").trim();
	    String param1   = Objects.toString(map.get("param1"), "").trim();

	    //ctx.roomName = roomName;
	    ctx.userName = userName;
	    ctx.param1   = param1;

	    if (userName.isEmpty()) {
	        ctx.success = false;
	        ctx.errorMessage = "유저 정보가 누락되었습니다.";
	        return ctx;
	    }

	    // ① param1으로 다른 유저 조회 시도 (두 메서드 동일 로직)
	    String targetUser = userName;
	    
	    switch(param0) {
	    case "/직업" :
	    case "/ㄱ" :
	    case "/ㄱㅁ" :	
	    case "/ㅍㅁ" :	
	    case "/ㅁㄷ" :	
	    	param1 ="";
	    }
	    
    	if (!param1.isEmpty()) {
	        List<String> newUserName = botNewService.selectParam1ToNewUserSearch(map);
	        if (newUserName != null && !newUserName.isEmpty()) {
	            targetUser = newUserName.get(0);
	        } else {
	            ctx.success = false;
	            ctx.errorMessage = "해당 유저(" + param1 + ")를 찾을 수 없습니다.";
	            return ctx;
	        }
	    }
	    
	    ctx.targetUser = targetUser;

	    // [OPT-HUNTER] attackInfo에서 미리 조회한 dropRows를 ctx에 저장 (applyDropBonusToContext 재사용)
	    if (map.containsKey("_preDropRows")) {
	        @SuppressWarnings("unchecked")
	        List<HashMap<String,Object>> preDropRows = (List<HashMap<String,Object>>) map.get("_preDropRows");
	        ctx.preDropRows = preDropRows;
	    }

	    // ② 유저 조회
	    User u = botNewService.selectUser(targetUser,null);
	    if (u == null) {
	        ctx.success = false;
	        ctx.errorMessage = "❌ 유저 정보를 찾을 수 없습니다.";
	        return ctx;
	    }

	    ctx.user = u;
	    ctx.job  = (u.job == null ? "" : u.job.trim());

	    // (선택) 현재 포인트 / 누적 SP도 여기서 같이 조회해두고 싶으면:
	    // [FIX3] selectCurrentPoint는 attackInfo에서만 사용 → calcUserBattleContext에서 제거
	    // ctx.currentPoint / ctx.currentPointStr 는 attackInfo()에서 직접 채움

	    try {
	    	// [OPT5] selectTotalEarnedSp DB 호출 제거 → selectUser에서 이미 계산된 TOTAL_SP 재사용
	    	SP total = SP.fromSp((double) u.totalSp);
	    	ctx.lifetimeSpStr = total.toString();
	    	ctx.lifetimeSp = total;
	    } catch (Exception ignore) {
	        ctx.lifetimeSpStr = "";
	    }

	    String job = ctx.job;

	    // _noJobBonus: 직업 보너스 무시 (랭킹 배치용 — 공평한 순수 장비 비교)
	    boolean noJobBonus = Boolean.TRUE.equals(map.get("_noJobBonus"));
	    if (noJobBonus) job = "";

	    // _forceHell: 헬모드 강제 적용 (랭킹 배치용 — 헬너프 포함 기준)
	    boolean forceHell = Boolean.TRUE.equals(map.get("_forceHell"));
	    if (forceHell) u.nightmareYn = 2;

	    // 1~2) 인벤토리 버프 캐시 (TTL 60초, 인벤토리 변경 시 invalidateInvBuff로 무효화)
	    HashMap<String,Object> invBuffData = getInvBuffCached(targetUser);
	    @SuppressWarnings("unchecked")
	    HashMap<String, Number> buffs      = (HashMap<String,Number>) invBuffData.get("market");
	    @SuppressWarnings("unchecked")
	    HashMap<String, Object> heavenBuff = (HashMap<String,Object>) invBuffData.get("heaven");

	    
	    int mktAtkMin = (buffs != null && buffs.get("ATK_MIN")  != null) ? buffs.get("ATK_MIN").intValue()  : 0;
	    int mktAtkMax = (buffs != null && buffs.get("ATK_MAX")  != null) ? buffs.get("ATK_MAX").intValue()  : 0;
	    int mktCrit    = (buffs != null && buffs.get("ATK_CRI")  != null) ? buffs.get("ATK_CRI").intValue()  : 0;
	    int mktRegen  = (buffs != null && buffs.get("HP_REGEN") != null) ? buffs.get("HP_REGEN").intValue() : 0;
	    int mktHpMax  = (buffs != null && buffs.get("HP_MAX")   != null) ? buffs.get("HP_MAX").intValue()   : 0;
	    int mktCritDmg = (buffs != null && buffs.get("CRI_DMG")  != null) ? buffs.get("CRI_DMG").intValue()  : 0;
	    int mktHpMaxRate  = (buffs != null && buffs.get("HP_MAX_RATE")   != null) ? buffs.get("HP_MAX_RATE").intValue()   : 0;
	    int mktAtkMaxRate  = (buffs != null && buffs.get("ATK_MAX_RATE")   != null) ? buffs.get("ATK_MAX_RATE").intValue()   : 0;

	    // 7000번대 보스 아이템(BOSS_HELL 전체) ATK 스탯 합산
	    // selectHeavenItemBuff: ITEM_TYPE='BOSS_HELL' 전체 합산 (7001~7999 모두 포함)
	    // BOSS_ITEM_HELL_NERF_EXEMPT=true 이면 ATK만 분리해 헬너프 이후 합산
	    int heavenAtkMin = 0, heavenAtkMax = 0; // 헬너프 면제 시 후합산 버퍼
	    if (heavenBuff != null) {
	        int hbAtkMin = heavenBuff.get("ATK_MIN") != null ? ((Number) heavenBuff.get("ATK_MIN")).intValue() : 0;
	        int hbAtkMax = heavenBuff.get("ATK_MAX") != null ? ((Number) heavenBuff.get("ATK_MAX")).intValue() : 0;
	        if (BOSS_ITEM_HELL_NERF_EXEMPT) {
	            heavenAtkMin = hbAtkMin; // 헬너프 이후에 합산
	            heavenAtkMax = hbAtkMax;
	        } else {
	            mktAtkMin += hbAtkMin;
	            mktAtkMax += hbAtkMax;
	        }
	        mktCrit      += heavenBuff.get("ATK_CRI")      != null ? ((Number) heavenBuff.get("ATK_CRI")).intValue()      : 0;
	        mktRegen     += heavenBuff.get("HP_REGEN")     != null ? ((Number) heavenBuff.get("HP_REGEN")).intValue()     : 0;
	        mktHpMax     += heavenBuff.get("HP_MAX")       != null ? ((Number) heavenBuff.get("HP_MAX")).intValue()       : 0;
	        mktCritDmg   += heavenBuff.get("CRI_DMG")      != null ? ((Number) heavenBuff.get("CRI_DMG")).intValue()      : 0;
	        mktHpMaxRate += heavenBuff.get("HP_MAX_RATE")  != null ? ((Number) heavenBuff.get("HP_MAX_RATE")).intValue()  : 0;
	        mktAtkMaxRate+= heavenBuff.get("ATK_MAX_RATE") != null ? ((Number) heavenBuff.get("ATK_MAX_RATE")).intValue() : 0;
	    }

	    // ── 세트 효과 적용 (flat 보너스) ─────────────────────────────────────────
	    @SuppressWarnings("unchecked")
	    List<HashMap<String,Object>> setBonus = (List<HashMap<String,Object>>) invBuffData.get("setBonus");
	    int setAtkFinalRate = 0, setCritFinalRate = 0, setCooldownReduce = 0, setEvasionRate = 0;
	    if (setBonus != null) {
	        for (HashMap<String,Object> b : setBonus) {
	            String bt = Objects.toString(b.get("BONUS_TYPE"), "");
	            int    bv = b.get("BONUS_VALUE") != null ? ((Number) b.get("BONUS_VALUE")).intValue() : 0;
	            switch (bt) {
	                case "ATK_MIN":         mktAtkMin        += bv; break;
	                case "ATK_MAX":         mktAtkMax        += bv; break;
	                case "HP_MAX":          mktHpMax         += bv; break;
	                case "ATK_CRI":         mktCrit          += bv; break;
	                case "CRI_DMG":         mktCritDmg       += bv; break;
	                case "HP_REGEN":        mktRegen         += bv; break;
	                case "ATK_FINAL_RATE":  setAtkFinalRate  += bv; break;
	                case "CRIT_FINAL_RATE": setCritFinalRate += bv; break;
	                case "COOLDOWN_REDUCE": setCooldownReduce += bv; break;
	                case "EVASION_RATE":    setEvasionRate   += bv; break;
	                default:
	                    if (bt.startsWith("SPECIAL_")) {
	                        if (ctx.activeSetSpecials == null) ctx.activeSetSpecials = new ArrayList<>();
	                        ctx.activeSetSpecials.add(bt);
	                    }
	                    break;
	            }
	        }
	    }

	    // 보스 아이템(7000번대) 보유 목록 (캐시에서 로드)
	    @SuppressWarnings("unchecked")
	    List<Integer> ownedBossFromCache = (List<Integer>) invBuffData.get("bossItems");
	    if (ownedBossFromCache != null) ctx.ownedBossItems.addAll(ownedBossFromCache);

	    // GP 잔액 조회 → ctx.gpBalance
	    try {
	        ctx.gpBalance = botNewService.selectGpBalance(targetUser);
	    } catch (Exception ignore) {}

	    // 🔹 직업 보너스 표시용 변수
	    int jobHp = 0;
	    int jobRegen = 0;
	    
	    if ("어둠사냥꾼".equals(job)) {
	    	int hpBase   = mktHpMax;
	        int regenBase= mktRegen;

	        mktHpMax  = (int) Math.round(mktHpMax * 1.25);
	        mktRegen  = (int) Math.round(mktRegen * 1.25);

	        jobHp = mktHpMax  - hpBase;
	        jobRegen = mktRegen  - regenBase;
	    }
	    AttackDeathStat ads = null;
	    // ── 헌터 등급 계산 (전 직업 공통) + 헌터 직업이면 스탯 보너스 적용 ──
	    try {
	        int totalAttacks, totalDeaths;

	        // [OPT-HUNTER] attackInfo에서 미리 계산된 값이 있으면 DB 조회 생략
	        
	        if (map.containsKey("_preHunterAdjAttacks")) {
	            totalAttacks = ((Number) map.get("_preHunterAdjAttacks")).intValue();
	            totalDeaths  = ((Number) map.get("_preHunterAdjDeaths")).intValue();
	        } else {
	            ads = botNewService.selectAttackDeathStats(targetUser, "");
	            ctx.ads = ads; // ma_cooldownAndHp에서 재사용 (2번째 DB 조회 생략)
	            totalAttacks = (ads == null ? 0 : ads.totalAttacks);
	            int hunterAttacks = (ads == null ? 0 : ads.hunterAttacks);
	            totalDeaths  = (ads == null ? 0 : ads.totalDeaths);
	            totalAttacks += hunterAttacks * 2;
	            totalDeaths  += hunterAttacks / 2;
	        }

	        @SuppressWarnings("unchecked")
	        List<HashMap<String,Object>> drops = (ctx.preDropRows != null)
	                ? ctx.preDropRows
	                : (List<HashMap<String,Object>>) invBuffData.get("drops");
	        int totalDrops = 0;
	        int darkQty    = 0;  // 어둠(DROP5) 수량
	        int grayQty    = 0;  // 음양(DROP9) 수량
	        if (drops != null) {
	            for (HashMap<String,Object> d : drops) {
	                Object v    = d.get("TOTAL_QTY");
	                String type = Objects.toString(d.get("GAIN_TYPE"), "");
	                if (v instanceof Number) {
	                    int qty = ((Number) v).intValue();
	                    totalDrops += qty;
	                    if (AchievementConfig.ITEM_TYPE_DARK.equals(type)) darkQty += qty;
	                    else if (AchievementConfig.ITEM_TYPE_GRAY.equals(type)) grayQty += qty;
	                }
	            }
	        }

	        // 음양/다크 아이템 보유량 → 헌터 조건(타격/아이템/죽음) 부가점수
	        // 어둠: 각 조건 +1/개, 음양: 각 조건 +3/개
	        // 음양/다크 아이템 보유량 → 헌터 조건 부가점수 (S 이하에만 적용, SS이상은 제외)
	        // 어둠: 각 조건 +1/개, 음양: 각 조건 +3/개
	        int itemBonus = darkQty * 1 + grayQty * 3;
	        String gradeWithoutBonus = calculateHunterGrade(totalAttacks, totalDrops, totalDeaths);
	        boolean bonusEligible = !"SSS".equals(gradeWithoutBonus) && !gradeWithoutBonus.startsWith("SS");

	        String grade;

	        if (bonusEligible) {
	            int atk = totalAttacks + itemBonus;
	            int drp = totalDrops   + itemBonus;
	            int dth = totalDeaths  + itemBonus;

	            String gradeWithBonus = calculateHunterGrade(atk, drp, dth);

	            if ("SSS".equals(gradeWithBonus)) {
	                gradeWithBonus = "SS";
	            } else {
	                totalAttacks = atk;
	                totalDrops   = drp;
	                totalDeaths  = dth;
	            }

	            grade = gradeWithBonus;
	        } else {
	            grade = gradeWithoutBonus;
	        }

	        ctx.hunterGrade = grade;

	        // 헌터 직업이면 등급 기반 스탯 보너스 적용
	        if ("헌터".equals(job)) {
	            int atkCap, hpCap, regenCap, criCap;
	            switch (ctx.hunterGrade) {
	                case "SSS": atkCap = 8000; hpCap = 80000; regenCap = 8000; criCap = 60; break;
	                case "SS":  atkCap = 6000; hpCap = 60000; regenCap = 6000; criCap = 45; break;
	                case "S":   atkCap = 4000; hpCap = 30000; regenCap = 3000; criCap = 30; break;
	                case "A+":  atkCap = 3300; hpCap = 22000; regenCap = 2200; criCap = 27; break;
	                case "A":   atkCap = 3000; hpCap = 20000; regenCap = 2000; criCap = 25; break;
	                case "B+":  atkCap = 2200; hpCap = 16000; regenCap = 1700; criCap = 22; break;
	                case "B":   atkCap = 2000; hpCap = 15000; regenCap = 1500; criCap = 20; break;
	                case "C+":  atkCap = 1300; hpCap = 9000;  regenCap = 900;  criCap = 16; break;
	                case "C":   atkCap = 1200; hpCap = 8000;  regenCap = 800;  criCap = 15; break;
	                case "D+":  atkCap = 700;  hpCap = 5000;  regenCap = 500;  criCap = 11; break;
	                case "D":   atkCap = 600;  hpCap = 4000;  regenCap = 400;  criCap = 10; break;
	                default:    atkCap = 200;  hpCap = 1000;  regenCap = 100;  criCap = 5;
	            }

	            int hunterAtkBonus    = Math.min(totalAttacks / 5,  atkCap);
	            int hunterHpBonus     = Math.min(totalDrops   / 5,  hpCap);
	            int hunterRegenBonus  = Math.min(totalDrops   / 50, regenCap);
	            int hunterCriDmgBonus = Math.min(totalDeaths  / 5,  criCap);

	            mktAtkMin  += hunterAtkBonus;
	            mktAtkMax  += hunterAtkBonus;
	            jobHp      += hunterHpBonus;
	            jobRegen   += hunterRegenBonus;
	            mktHpMax   += hunterHpBonus;
	            mktRegen   += hunterRegenBonus;
	            mktCritDmg += hunterCriDmgBonus;
	        }
	    } catch (Exception ignore) {}


	    // 기본 스탯
	    int baseAtkMin     = u.atkMin;
	    int baseAtkMax     = u.atkMax;
	    int baseHpMax   = u.hpMax;
	    int baseRegen   = u.hpRegen;
	    int baseCrit    = u.critRate;
	    int baseCritDmg = u.critDmg;

	    ctx.baseAtkMin     = baseAtkMin;
	    ctx.baseAtkMax     = baseAtkMax;
	    ctx.baseHpMax   = baseHpMax;
	    ctx.baseRegen   = baseRegen;
	    ctx.baseCrit= baseCrit;
	    ctx.baseCritDmg = baseCritDmg;

	    ctx.mktAtkMin  = mktAtkMin;
	    ctx.mktAtkMax  = mktAtkMax;
	    ctx.mktCrit     = mktCrit;
	    ctx.mktRegen   = mktRegen;
	    ctx.mktHpMax   = mktHpMax;
	    ctx.mktCritDmg  = mktCritDmg;
	    ctx.mktHpMaxRate  = mktHpMaxRate;
	    ctx.mktAtkMaxRate  = mktAtkMaxRate;

	    int atkMin = baseAtkMin + mktAtkMin;
	    int atkMax = baseAtkMax + mktAtkMax;

	    if ("흡혈귀".equals(job)) {
	        mktRegen = 0;
	    }

	    // 4) 최종 HP
	    int hpMax = baseHpMax + mktHpMax;
	    int finalRegen = baseRegen + mktRegen;
	    if ("전사".equals(job)) {
	        //hpMax += baseHpMax*10; // 기본 HP 추가
	    }
	    if ("검성".equals(job)) {
	        hpMax += baseHpMax*2; // 기본 HP 추가
	    }
	    if ("용사".equals(job)) {
	    	hpMax += baseHpMax*2; // 기본 HP 추가

	        jobHp = baseHpMax*2;
	    }
	    if (hpMax <= 0) hpMax = 1;

	    // 5) 최종 리젠 (기본+아이템+축복)
	    int regen = finalRegen + jobRegen;
	    if (regen < 0) regen = 0;

	    int hpMaxBonus = (int)((long)hpMax * ctx.mktHpMaxRate / 100);
	    hpMax += hpMaxBonus;
	    int atkMinBonus = (int)((long)atkMin * ctx.mktAtkMaxRate / 100);
	    atkMin += atkMinBonus;
	    int atkMaxBonus = (int)((long)atkMax * ctx.mktAtkMaxRate / 100);
	    atkMax += atkMaxBonus;
	    
	    int crit =baseCrit + mktCrit;
	    int critDmg = baseCritDmg + mktCritDmg;
	    
	    // [7009] 진화형 무기: 레벨당 공격력 +150 (최대 Lv300 = +45,000)
	    if (ctx.ownedBossItems.contains(7009)) {
	        int evolveBonus = Math.min(u.lv, 300) * 150;
	        atkMin += evolveBonus;
	        atkMax += evolveBonus;
	    }
	    // [7013] 어제 공격자 수 × 공격력 +500, 치명타 데미지 +5 (최대 30명 = +15,000)
	    if (ctx.ownedBossItems.contains(7013)) {
	        int cappedYest = Math.min(getYesterdayAttackerCountCached(), 30);
	        atkMin  += cappedYest * 500;
	        atkMax  += cappedYest * 500;
	        critDmg += cappedYest * 5;
	    }

	    int hellNerfAtkMin =0;
	    int hellNerfAtkMax =0;
	    int hellNerfHp =0;
	    int hellNerfCrit =0;
	    int hellNerfCritDmg =0;

	    if(u.nightmareYn==2) {
	    	double hellMult = MiniGameUtil.getHellNerfMult(ctx.hunterGrade);
	    	if (ctx.ownedBossItems.contains(7007)) hellMult = Math.max(0.0, hellMult + 0.03);
	    	ctx.hellNerfRate = hellMult;

	    	hellNerfAtkMin = Math.max(0, (int) Math.round(atkMin   * (1-hellMult) ));
	    	hellNerfAtkMax = Math.max(0, (int) Math.round(atkMax   * (1-hellMult) ));
	    	hellNerfHp = Math.max(0, (int) Math.round(hpMax   * (1-hellMult) ));
	    	hellNerfCrit = Math.max(0, (int) Math.round(crit   * (1-hellMult) ));
	    	hellNerfCritDmg = Math.max(0, (int) Math.round(critDmg   * (1-hellMult) ));

	        atkMin   -= hellNerfAtkMin;
	        atkMax   -= hellNerfAtkMax;
	        hpMax -= hellNerfHp;
	        crit -= hellNerfCrit;
	        critDmg -= hellNerfCritDmg;
	    }

	    ctx.hellNerfAtkMin = hellNerfAtkMin;
	    ctx.hellNerfAtkMax = hellNerfAtkMax;
	    ctx.hellNerfHp = hellNerfHp;
	    ctx.hellNerfCrit = hellNerfCrit;
	    ctx.hellNerfCritDmg = hellNerfCritDmg;

	    if ("곰".equals(job)) {

	        int atkSum = atkMin+atkMax;
	        int critMultiplier = baseCritDmg + mktCritDmg;

	        hpMax = (int) Math.min((long)hpMax + (long)atkSum * critMultiplier / 100, Integer.MAX_VALUE);

	        // 공격력은 의미 없음 → HP 기반으로 통일
	        atkMin = hpMax;
	        atkMax = hpMax;

	        // 곰은 크리 사용 안함
	        ctx.crit = 0;
	        ctx.critDmg = 0;
	        baseCritDmg= 0;
	        mktCritDmg=0;
	        
	    }
	    /*
	    // [7000번대 헬너프 면제] 헬너프 이후 7001 천벌 ATK 합산
	    if (BOSS_ITEM_HELL_NERF_EXEMPT && (heavenAtkMin > 0 || heavenAtkMax > 0)) {
	        atkMin += heavenAtkMin;
	        atkMax += heavenAtkMax;
	    }*/
	    // ── 세트 효과: 최종 비율 보너스 (헬너프 포함 최종 수치 기준) ──────────────
	    if (setAtkFinalRate > 0) {
	        atkMin += (int) Math.round((long)atkMin * setAtkFinalRate / 100.0);
	        atkMax += (int) Math.round((long)atkMax * setAtkFinalRate / 100.0);
	    }
	    if (setCritFinalRate > 0) {
	        crit += (int) Math.round(crit * setCritFinalRate / 100.0);
	    }
	    ctx.setAtkFinalRate   = setAtkFinalRate;
	    ctx.setCritFinalRate  = setCritFinalRate;
	    ctx.setCooldownReduce = setCooldownReduce;
	    ctx.setEvasionRate    = setEvasionRate;
	    ctx.atkMin = atkMin;
	    ctx.atkMax = atkMax;
	    ctx.hpMax  = hpMax;
	    ctx.regen    = regen;

	    // 표시용 스탯 (1번 메서드에서 쓰던 값)
	    ctx.crit          = crit;
	    ctx.critDmg       = critDmg;

	    // 🔹 직업 보너스(표시용) 저장
	    ctx.jobHp = jobHp;
	    ctx.jobRegen = jobRegen;
	    
	    ctx.success = true;


	    applyDropBonusToContext(ctx, targetUser, "");
	    applyHellBoxBonusToContext(ctx, targetUser);

	    // hpCur: 리젠 반영 현재 체력 (항상 실시간 DB 조회)
	    final String ctxRoomName = Objects.toString(map.get("roomName"), "");
	    ctx.hpCur = computeEffectiveHpFromLastAttack(targetUser, ctxRoomName, u, hpMax, regen);
	    if (ctx.hpCur > hpMax) ctx.hpCur = hpMax;

	    return ctx;
	}
	
	private String calculateHunterGrade(int totalAttacks, int totalDrops, int totalDeaths) {

// ---------------- 상위 단독 등급 ----------------
		if (totalAttacks >= 50000 && totalDrops >= 100000 && totalDeaths >= 1200) {
			return "SSS";
		}

		if (totalAttacks >= 40000 && totalDrops >= 50000 && totalDeaths >= 700) {
			return "SS";
		}

		if (totalAttacks >= 30000 && totalDrops >= 30000 && totalDeaths >= 500) {
			return "S";
		}

// ---------------- A/B/C/D 처리 ----------------
		String grade = checkPlusGrade(totalAttacks, totalDrops, totalDeaths, 20000, 20000, 400, "A");
		if (grade != null)
			return grade;

		grade = checkPlusGrade(totalAttacks, totalDrops, totalDeaths, 10000, 10000, 200, "B");
		if (grade != null)
			return grade;

		grade = checkPlusGrade(totalAttacks, totalDrops, totalDeaths, 5000, 5000, 100, "C");
		if (grade != null)
			return grade;

		grade = checkPlusGrade(totalAttacks, totalDrops, totalDeaths, 1000, 1000, 50, "D");
		if (grade != null)
			return grade;

		return "F";
	}
	
	private String checkPlusGrade(int atk, int drop, int death, int atkReq, int dropReq, int deathReq,
			String baseGrade) {

		int match = 0;
		if (atk >= atkReq)
			match++;
		if (drop >= dropReq)
			match++;
		if (death >= deathReq)
			match++;

		if (match == 3)
			return baseGrade + "+";
		if (match == 2)
			return baseGrade;

		return null;
	}
	/** 
	 */
	public String getHpStatus(HashMap<String,Object> map) {
		UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }
	    
	    // calcUserBattleContext 에서 채워준 공통 값들 재사용
	    final String targetUser = ctx.targetUser;
	    final String roomName   = ctx.roomName;
	    final User   u          = ctx.user;

	    final int hpMax = ctx.hpMax;  // 최종 HP
	    final int regen   = ctx.regen;    // 실제 적용 리젠(축복 포함/흡혈귀 처리 포함)
	    //final boolean hasBless = ctx.hasBless;  // 운영자 축복 여부

	    int hpCur = ctx.hpCur;

	    StringBuilder sb = new StringBuilder();
	    sb.append("❤️ ").append(targetUser).append("님의 체력 상태").append(NL)
	      .append("현재 체력: ").append(hpCur).append(" / ").append(hpMax).append(NL)
	      .append("5분당 회복: +").append(regen).append(NL);

	    if (hpCur <= hpMax * 0.05) {
	        sb.append("⚠️ 현재 공격 불가").append(NL);
	    } else if (hpCur >= hpMax) {
	        sb.append("✅ 현재 체력은 최대 상태입니다.").append(NL);
	    }

	    // ✅ 회복 예측 스케줄 (예: 60분 범위 내)
	    //   buildRegenScheduleSnippetEnhanced2 시그니처:
	    //   (String targetUser, String roomName, User u,
	    //    int intervalMinutes, int hpCur, int hpMax, int regen, int maxMinutes)
	    String regenInfo = buildRegenScheduleSnippetEnhanced2(
	            targetUser,
	            roomName,
	            u,
	            30,          // intervalMinutes
	            hpCur,
	            hpMax,
	            regen,
	            60           // maxMinutes
	    );

	    if (regenInfo != null && !regenInfo.isEmpty()) {
	        sb.append(regenInfo);
	    }

	    // 🔹 여기서 "공격 로직"에서 쓰는 진행중 전투 계산 재사용
	    boolean nightmare = u.nightmareYn >= 1;
	    boolean hell = u.nightmareYn == 2;
	    try {
	        OngoingBattle ob = botNewService.selectOngoingBattle(targetUser, roomName);
	        if (ob != null) {
	            Monster m = getMonsterCached(ob.monNo);
	            if (m != null) {
	                int monMaxHp    = m.monHp;
	                int monHpRemain = Math.max(0, m.monHp - ob.totalDealtDmg);

	                if(nightmare) {
	                	monMaxHp *=NM_MUL_HP_ATK;
	                }
	                
	                
	                sb.append(NL)
	                  .append("▶ 전투중인 몬스터").append(NL)
	                  .append(m.monName);
                    if(nightmare) {
                    	if(hell) sb.append("[헬]");
                    	else sb.append("[나이트메어]");
	                }
	                sb.append(" (").append(monHpRemain).append(" / ").append(monMaxHp).append(")")
	                  .append(NL);
	            }
	        } else {
	            // 진행중 전투는 없지만 타겟몬은 있을 수 있음 (선택)
	            Monster m = getMonsterCached(u.targetMon);
	            
	            int monMaxHp    = m.monHp;
	            
	            if(nightmare) {
                	monMaxHp *=NM_MUL_HP_ATK;
                }
	            if (m != null) {
	                sb.append(NL)
	                  .append("▶ 타겟 몬스터").append(NL)
	                  .append(m.monName)
	                  .append(" (").append(monMaxHp).append(" / ").append(monMaxHp).append(")")
	                  .append(NL);
	            }
	        }
	    } catch (Exception ignore) {
	        sb.append(NL).append("전투중인 몬스터 정보를 불러오지 못했습니다.").append(NL);
	    }

	    return sb.toString();
	}
	
	private void processBagOpen(
	        int bagItemId,
	        int bagCount,
	        int spMode,
	        String userName,
	        String roomName,
	        SP totalSP,
	        List<String> detail,
	        List<String> itemSummary
	) {

	    if (bagCount <= 0) return;

	    HashMap<String,Object> param = new HashMap<>();
	    param.put("userName", userName);
	    param.put("roomName", roomName);
	    param.put("bagItemId", bagItemId);

	    List<Integer> rewardItemIds =
	            botNewService.selectBagRewardItemIdsUserNotOwned(param);

	    boolean isNightmare = bagItemId == 92;
	    String prefix = isNightmare ? "[나메]" : "";

	    for (int i = 0; i < bagCount; i++) {

	        int remain = (rewardItemIds == null) ? 0 : rewardItemIds.size();

	        // 확률 계산
	        double itemChance;

	        if (isNightmare) {
	            // 나메 가방
	            itemChance = Math.min(0.10, Math.max(0.005, remain * 0.005));
	        } else {
	            // 일반 가방
	            itemChance = Math.min(0.50, Math.max(0.05, remain * 0.03));
	        }

	        double roll = ThreadLocalRandom.current().nextDouble();

	        if (remain <= 0 || rewardItemIds == null || rewardItemIds.isEmpty() || roll > itemChance) {

	            SP sp = rollBagSpWithCeiling(spMode);
	            totalSP.add(sp);
	            detail.add(prefix + "가방" + (i+1) + ": " + sp + "sp");

	        } else {

	            int idx = ThreadLocalRandom.current().nextInt(rewardItemIds.size());
	            int itemId = rewardItemIds.get(idx);

	            giveBagItem(userName, roomName, itemId, itemSummary);

	            // 중복 방지
	            rewardItemIds.remove(idx);

	            detail.add(prefix + "가방" + (i+1) + ": 아이템 획득");
	        }
	    }
	}

	public String openBag(HashMap<String,Object> map) {

	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }

	    // ── 헬상자 DB pending 확인 (DEL_YN='2'=황금, '3'=플래티넘) ──────────────
	    try {
	        HashMap<String,Object> pendingRow = botNewService.selectPendingHellBox(userName);
	        if (pendingRow != null) {
	            String delYn  = Objects.toString(pendingRow.get("DEL_YN"), "");
	            int    itemId = pendingRow.get("ITEM_ID") != null ? ((Number) pendingRow.get("ITEM_ID")).intValue() : 0;
	            my.prac.core.util.MiniGameUtil.HellBoxEntry matchEntry = findPendingEntry(delYn, itemId);
	            String desc = matchEntry != null ? matchEntry.desc : "스탯 " + itemId;
	            if ("2".equals(delYn)) {
	                // 황금 pending: 5% 플래티넘 진화
	                if (ThreadLocalRandom.current().nextDouble() < 0.05) {
	                    my.prac.core.util.MiniGameUtil.HellBoxEntry platEntry =
	                        my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT.get(
	                            ThreadLocalRandom.current().nextInt(my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT.size()));
	                    HashMap<String,Object> upMap = new HashMap<>();
	                    upMap.put("userName",   userName);
	                    upMap.put("newItemId",  platEntry.itemId);
	                    upMap.put("newQty",     platEntry.value);
	                    botNewService.upgradePendingHellBox(upMap);
	                    StringBuilder sb = new StringBuilder();
	                    sb.append("✨ 기적!! 황금 각인이 더욱 빛을 발합니다!!").append(NL);
	                    sb.append("━━━━━━━━━━━━").append(NL);
	                    sb.append("✨ 플래티넘으로 진화하였습니다!! ✨").append(NL);
	                    sb.append("━━━━━━━━━━━━").append(NL);
	                    sb.append("/가방열기 로 개봉하세요!");
	                    return sb.toString();
	                } else {
	                    // 황금 확정
	                    botNewService.confirmPendingHellBox(userName);
	                    invalidateInvBuff(userName);
	                    StringBuilder sb = new StringBuilder();
	                    sb.append("✨ 황금상자 개봉!").append(NL);
	                    sb.append("━━━━━━━━━━━━").append(NL);
	                    sb.append("✨ ").append(desc).append(" 획득!").append(NL);
	                    sb.append("━━━━━━━━━━━━").append(NL);
	                    sb.append(buildHellBoxStatSummary(userName));
	                    return sb.toString();
	                }
	            } else if ("3".equals(delYn)) {
	                // 플래티넘 확정
	                botNewService.confirmPendingHellBox(userName);
	                invalidateInvBuff(userName);
	                StringBuilder sb = new StringBuilder();
	                sb.append("✨ 플래티넘상자 개봉!").append(NL);
	                sb.append("━━━━━━━━━━━━").append(NL);
	                sb.append("✨ ").append(desc).append(" 획득!").append(NL);
	                sb.append("━━━━━━━━━━━━").append(NL);
	                sb.append(buildHellBoxStatSummary(userName));
	                return sb.toString();
	            }
	        }
	    } catch (Exception ignore) {}

	    // 91 / 92 / 93 각각 개수 조회
	    int normalCount    = botNewService.selectBagCountByItemId(userName, roomName, 91);
	    int nightmareCount = botNewService.selectBagCountByItemId(userName, roomName, 92);
	    int hellCount      = botNewService.selectBagCountByItemId(userName, roomName, BAG_HELL_ITEM_ID);

	    // ── 헬상자 오픈 권한 체크 (헬보스1회처치 업적 필요) ──────────────────
	    if (hellCount > 0) {
	        try {
	            if (!Boolean.TRUE.equals(getInvBuffCached(userName).get("hellClearAchv"))) {
	                if (normalCount + nightmareCount <= 0) {
	                    return "❌ 지옥의 유물상자는 헬보스를 1회 이상 처치해야 열 수 있습니다.";
	                }
	                hellCount = 0; // 업적 없으면 헬 가방 제외, 일반/나메만 오픈
	            }
	        } catch (Exception ignore) {}
	    }

	    if (normalCount + nightmareCount + hellCount <= 0) {
	        return "열 수 있는 가방이 없습니다.";
	    }

	    // 각각 소비
	    if (normalCount > 0) {
	        botNewService.consumeBagBulkByItemIdTx(userName, roomName, 91, normalCount);
	    }
	    if (nightmareCount > 0) {
	        botNewService.consumeBagBulkByItemIdTx(userName, roomName, 92, nightmareCount);
	    }
	    if (hellCount > 0) {
	        botNewService.consumeBagBulkByItemIdTx(userName, roomName, BAG_HELL_ITEM_ID, hellCount);
	    }

	    SP normalSP  = new SP(0, "");
	    SP nmSP      = new SP(0, "");
	    SP totalSP   = new SP(0, ""); // 표시용 합계 (헬 제외)
	    List<String> detail = new ArrayList<>();
	    List<String> itemSummary = new ArrayList<>();

	    processBagOpen(
	            91,
	            normalCount,
	            0,
	            userName,
	            roomName,
	            normalSP,
	            detail,
	            itemSummary
	    );

	    processBagOpen(
	            92,
	            nightmareCount,
	            1,
	            userName,
	            roomName,
	            nmSP,
	            detail,
	            itemSummary
	    );

	    totalSP.add(normalSP); totalSP.add(nmSP); // hellSP는 openHellBag 후 합산
	    SP hellSP = new SP(0, "");
	    openHellBag(userName, roomName, hellCount, hellSP, detail, itemSummary);
	    totalSP.add(hellSP);

	    // 🔹 메시지
	    StringBuilder sb = new StringBuilder();
	    sb.append("가방 총 ").append(normalCount + nightmareCount + hellCount)
	      .append("개를 열었습니다!").append(NL);

	 // 🔹 SP 저장 (일반/나메 각각)
	    if (normalSP.getValue() != 0 || !normalSP.getUnit().isEmpty()) {
	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName); pr.put("roomName", roomName);
	        pr.put("score", normalSP.getValue()); pr.put("scoreExt", normalSP.getUnit());
	        pr.put("cmd", "BAG_OPEN_SP");
	        botNewService.insertPointRank(pr);
	    }
	    if (nmSP.getValue() != 0 || !nmSP.getUnit().isEmpty()) {
	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName); pr.put("roomName", roomName);
	        pr.put("score", nmSP.getValue()); pr.put("scoreExt", nmSP.getUnit());
	        pr.put("cmd", "BAG_OPEN_NM_SP");
	        botNewService.insertPointRank(pr);
	    }
        
        // 가방별 SP 표시
        if (normalCount > 0) sb.append("✨ 일반가방 획득: ").append(normalSP).append(NL);
        if (nightmareCount > 0) sb.append("✨ 나메가방 획득: ").append(nmSP).append(NL);
        if (hellCount > 0) sb.append("✨ 헬상자 획득: ").append(hellSP).append(NL);
        sb.append("✨ 총 획득: ").append(totalSP).append(NL);

	    if (!itemSummary.isEmpty()) {
	        sb.append("✨ 아이템 획득: ")
	          .append(String.join(", ", itemSummary)).append(NL);
	    }

	    sb.append(NL).append("▶ 상세 내역").append(NL);
	    for (String d : detail) {
	        sb.append(d).append(NL);
	    }

	    return sb.toString();
	}

	private void giveBagItem(String userName, String roomName, int itemId, List<String> itemSummary) {

		HashMap<String, Object> inv = new HashMap<>();
		inv.put("userName", userName);
		inv.put("roomName", roomName);
		inv.put("itemId", itemId);
		inv.put("qty", 1);
		inv.put("delYn", "0");
		inv.put("gainType", "BAG_OPEN");

		botNewService.insertInventoryLogTx(inv);
		invalidateInvBuff(userName); // 가방 오픈 아이템 획득

		HashMap<String, Object> info = botNewService.selectItemDetailById(itemId);

		String itemName = Objects.toString(info.get("ITEM_NAME"), "");
		itemSummary.add(itemName);
	}

	/**
	 * 지옥의유물상자 오픈 처리
	 * 95% SP / 5% 영구 스탯 상자 (기본90%/황금9%/플래티넘1%)
	 */
	private void openHellBag(String userName, String roomName, int count,
	        SP totalSP, List<String> detail, List<String> itemSummary) {
	    if (count <= 0) return;
	    long top1Sp = getTop1SpCached();
	    long spMin  = top1Sp > 0 ? top1Sp * 3 / 1000 : 1_000_000L;                          // 1등의 0.3%
	    long spMax  = top1Sp > 0 ? Math.min(top1Sp / 100, 100_000_000_000L) : 5_000_000L;   // 1등의 1%, 최대 1000b
	    for (int i = 0; i < count; i++) {
	        double roll = ThreadLocalRandom.current().nextDouble();
	        if (roll < 0.95) {
	            // 95% → SP (HELL_BOX_SP CMD로 직접 저장)
	            SP sp = pickBiasedSp(spMin, spMax);
	            try {
	                HashMap<String,Object> pr = new HashMap<>();
	                pr.put("userName",  userName);
	                pr.put("roomName",  roomName);
	                pr.put("score",     sp.getValue());
	                pr.put("scoreExt",  sp.getUnit());
	                pr.put("cmd",       "HELL_BOX_SP");
	                botNewService.insertPointRank(pr);
	            } catch (Exception ignore) {}
	            totalSP.add(sp); // 표시용 누적
	            detail.add("[지옥의유물상자]" + (i+1) + ": " + sp + "sp");
	        } else {
	            // 5% → 영구 스탯 상자 (기본90%/황금9%/플래티넘1%)
	            double tierRoll = ThreadLocalRandom.current().nextDouble();
	            java.util.List<my.prac.core.util.MiniGameUtil.HellBoxEntry> pool;
	            String tierName;
	            if (tierRoll < 0.01) {
	                pool = my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT;
	                tierName = "✨플래티넘";
	            } else if (tierRoll < 0.10) {
	                pool = my.prac.core.util.MiniGameUtil.HELL_BOX_GOLD;
	                tierName = "✨황금";
	            } else {
	                pool = my.prac.core.util.MiniGameUtil.HELL_BOX_BASIC;
	                tierName = "기본";
	            }
	            if (pool != null && !pool.isEmpty()) {
	                my.prac.core.util.MiniGameUtil.HellBoxEntry entry =
	                        pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	                boolean isBasic = pool == my.prac.core.util.MiniGameUtil.HELL_BOX_BASIC;
	                if (isBasic) {
	                    // 기본상자: 즉시 지급
	                    HashMap<String,Object> inv = new HashMap<>();
	                    inv.put("userName", userName); inv.put("roomName", roomName);
	                    inv.put("itemId",   entry.itemId); inv.put("qty", entry.value);
	                    inv.put("delYn",    "0"); inv.put("gainType", "HELL_BOX");
	                    try { botNewService.insertInventoryLogTx(inv); invalidateInvBuff(userName); } catch (Exception ignore) {}
	                    detail.add("[지옥의유물상자]" + (i+1) + ": " + tierName + "상자 → " + entry.desc);
	                    itemSummary.add(tierName + "(" + entry.desc + ")");
	                } else {
	                    // 황금/플래티넘: DEL_YN='2'(황금) 또는 '3'(플래티넘)으로 DB INSERT (pending)
	                    String pendingDelYn = (pool == my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT) ? "3" : "2";
	                    HashMap<String,Object> pInv = new HashMap<>();
	                    pInv.put("userName", userName); pInv.put("roomName", roomName);
	                    pInv.put("itemId",   entry.itemId); pInv.put("qty", entry.value);
	                    pInv.put("delYn",    pendingDelYn); pInv.put("gainType", "HELL_BOX");
	                    try { botNewService.insertInventoryLogTx(pInv); } catch (Exception ignore) {}
	                    String dramatic = (pool == my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT)
	                            ? "✨ 플래티넘 각인이 빛을 발하고 있습니다!! ✨"
	                            : "✨ 황금 각인이 빛나고 있습니다!! ✨";
	                    detail.add("[지옥의유물상자]" + (i+1) + ": " + dramatic);
	                    detail.add("/가방열기 로 개봉하세요!");
	                    itemSummary.add(tierName + " 보류중 (/가방열기)");
	                }
	            }
	        }
	    }
	}

	/**
	 * pending 헬상자 행의 DEL_YN + ITEM_ID로 MiniGameUtil 풀에서 entry 찾기
	 */
	private my.prac.core.util.MiniGameUtil.HellBoxEntry findPendingEntry(String delYn, int itemId) {
	    java.util.List<my.prac.core.util.MiniGameUtil.HellBoxEntry> pool =
	        "3".equals(delYn) ? my.prac.core.util.MiniGameUtil.HELL_BOX_PLAT
	                           : my.prac.core.util.MiniGameUtil.HELL_BOX_GOLD;
	    for (my.prac.core.util.MiniGameUtil.HellBoxEntry e : pool) {
	        if (e.itemId == itemId) return e;
	    }
	    return null;
	}

	/**
	 * 헬상자 각인 누적 현황 요약 문자열 (확정 후 메시지에 포함)
	 */
	private String buildHellBoxStatSummary(String userName) {
	    try {
	        List<HashMap<String,Object>> stats = botNewService.selectHellBoxStats(userName);
	        if (stats == null || stats.isEmpty()) return "";
	        StringBuilder sb = new StringBuilder();
	        sb.append("[ 지옥 각인 누적 현황 ]").append(NL);
	        for (HashMap<String,Object> row : stats) {
	            int itemId = safeInt(row.get("ITEM_ID"));
	            int qty    = safeInt(row.get("TOTAL_QTY"));
	            if (qty <= 0) continue;
	            String line;
	            switch (itemId) {
	                case 3001: line = "최소공격력 +" + qty;       break;
	                case 3002: line = "최대공격력 +" + qty;       break;
	                case 3003: line = "최소공격력 +" + qty + "%"; break;
	                case 3004: line = "최대공격력 +" + qty + "%"; break;
	                case 3005: line = "최대체력 +" + qty;         break;
	                case 3006: line = "최대체력 +" + qty + "%";   break;
	                case 3007: line = "치명타율 +" + qty + "%";   break;
	                case 3008: line = "치명타피해 +" + qty + "%"; break;
	                default:   line = null;                       break;
	            }
	            if (line != null) sb.append("  ").append(line).append(NL);
	        }
	        return sb.toString();
	    } catch (Exception e) { return ""; }
	}

	/**
	 * HELL_BOX gain_type 아이템을 ctx 스탯에 반영 (영구 보너스)
	 */
	private void applyHellBoxBonusToContext(UserBattleContext ctx, String userName) {
	    try {
	        List<HashMap<String,Object>> stats = botNewService.selectHellBoxStats(userName);
	        if (stats == null || stats.isEmpty()) return;
	        int atkMin = 0, atkMax = 0, hp = 0, regen = 0, crit = 0, critDmg = 0;
	        for (HashMap<String,Object> row : stats) {
	            int itemId = safeInt(row.get("ITEM_ID"));
	            int qty    = safeInt(row.get("TOTAL_QTY"));
	            if (qty <= 0) continue;
	            switch (itemId) {
	            case 3001: atkMin  += qty; break;
	            case 3002: atkMax  += qty; break;
	            case 3003: atkMin  += (int) Math.round(ctx.atkMin * qty / 100.0); break;
	            case 3004: atkMax  += (int) Math.round(ctx.atkMax * qty / 100.0); break;
	            case 3005: hp      += qty; break;
	            case 3006: hp      += (int) Math.round(ctx.hpMax  * qty / 100.0); break;
	            case 3007: crit    += qty; break;
	            case 3008: critDmg += qty; break;
	            case 3009: regen   += qty; break;
	            }
	        }
	        ctx.atkMin  += atkMin;  ctx.atkMax  += atkMax;
	        ctx.hpMax   += hp;      ctx.regen   += regen;
	        ctx.crit    += crit;    ctx.critDmg += critDmg;
	        // 표시용
	        ctx.hellBoxAtkMin  = atkMin;  ctx.hellBoxAtkMax  = atkMax;
	        ctx.hellBoxHp      = hp;      ctx.hellBoxRegen   = regen;
	        ctx.hellBoxCrit    = crit;    ctx.hellBoxCritDmg = critDmg;
	    } catch (Exception ignore) {}
	}

	private long getTop1SpCached() {
	    long now = System.currentTimeMillis();
	    if (now - TOP1_SP_CACHE_TS > TOP1_SP_CACHE_TTL_MS) {
	        try {
	            List<HashMap<String, Object>> list = botNewService.selectSpAndAtkRanking();
	            if (list != null && !list.isEmpty()) {
	                TOP1_SP_CACHE = safeLong(list.get(0).get("TOT_SP"));
	            }
	        } catch (Exception e) { /* 캐시 유지 */ }
	        TOP1_SP_CACHE_TS = now;
	    }
	    return TOP1_SP_CACHE;
	}

	// ── 인벤토리 버프 캐시 ─────────────────────────────────────────────────────
	// TTL 없음. 아이템 변경(획득/구매/판매) 시 invalidateInvBuff()로 무효화.
	private HashMap<String,Object> getInvBuffCached(String userName) {
	    HashMap<String,Object> cached = MiniGameUtil.INV_BUFF_CACHE.get(userName);
	    if (cached != null) return cached;
	    HashMap<String,Object> data = new HashMap<>();
	    try { data.put("market", botNewService.selectOwnedMarketBuffTotals(userName, "")); } catch (Exception ignore) {}
	    try { data.put("heaven", botNewService.selectHeavenItemBuff(userName)); } catch (Exception ignore) {}
	    try {
	        List<Integer> bossItemIds = botNewService.selectBossItemIds();
	        if (bossItemIds != null && !bossItemIds.isEmpty())
	            data.put("bossItems", botNewService.selectInventoryItemsByIds(userName, "", bossItemIds));
	    } catch (Exception ignore) {}
	    try { data.put("drops", botNewService.selectTotalDropItems(userName)); } catch (Exception ignore) {}
	    try { data.put("setBonus", botNewService.selectActiveSetBonuses(userName)); } catch (Exception ignore) {}
	    try { data.put("hellClearAchv", botNewService.hasHellClearAchv(userName)); } catch (Exception ignore) {}
	    MiniGameUtil.INV_BUFF_CACHE.put(userName, data);
	    return data;
	}

	/** 아이템 획득/판매/소비 후 호출 → 해당 유저의 인벤토리 버프 캐시 즉시 무효화 */
	private void invalidateInvBuff(String userName) {
	    if (userName != null) MiniGameUtil.INV_BUFF_CACHE.remove(userName);
	}

	// ─────────────────────────────────────────────────────────────────────────

	private SP rollBagSpWithCeiling(int nightmareYn) {
	    long top1Sp = getTop1SpCached();
	    long top1Ceiling2 = top1Sp > 0 ? top1Sp / 500 : 0; // 1등 누적SP의 0.2%

	    switch (nightmareYn) {
	    case	0:
	    	return pickBiasedSp(10000, top1Ceiling2 > 0 ? top1Ceiling2 : 1000000);
	    case	1: {
	    	// 나메상자: max = 1등SP의 0.5%, 최대 10b (1_000_000_000 raw)
	    	long nmMax = top1Sp > 0 ? Math.min(top1Sp / 200, 1_000_000_000L) : 100_000_000L;
	    	return pickBiasedSp(300000, nmMax);
	    }
	    case	2:
	    	break;
	    default:
	    	break;
	    }
	    return pickBiasedSp(10000, top1Ceiling2 > 0 ? top1Ceiling2 : 1000000);
	}

	/* ===== Public APIs ===== */
	public String changeJob(HashMap<String,Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String selRaw  = Objects.toString(map.get("param1"), "").trim();

	    boolean master =false;
	    if (roomName.isEmpty() || userName.isEmpty())
	        return "방/유저 정보가 누락되었습니다.";

	    User u = botNewService.selectUser(userName, null);
	    if (u == null)
	        return "유저 정보를 찾을 수 없습니다.";

	    UserBattleContext ctx = calcUserBattleContext(map);
	    
	    String curJob = (u.job == null ? "" : u.job.trim());
	    String sel = selRaw;

	    // 1) param1 없이 호출한 경우: 안내
	    if (sel.isEmpty()) {
	        if (curJob.isEmpty()) {
	            // 아직 직업 없음 → 직업 설명
	            return buildJobDescriptionList();
	        } else {
	            // 현재 직업 보여주고 설명
	            return "현재 직업: " + curJob + NL + buildJobDescriptionList();
	        }
	    }
	    
	    if(roomName.equals("람쥐봇 문의방")) {
			
			if(userName.equals("일어난다람쥐/카단")) {
				master =true;
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}


	    // 2) 입력한 직업명 파싱
	    String newJob = normalizeJob(sel);
	    if (newJob == null) {
	        return "존재하지 않는 직업입니다. /직업 으로 확인해주세요.";
	    }

	    // 3) 레벨 제한 (처음/변경 모두 공통 룰)

	    // 4) 동일 직업으로 변경 시도
	    if (!curJob.isEmpty() && newJob.equals(curJob)) {
	        return "이미 [" + curJob + "] 직업입니다.";
	    }
	    
	    // ─────────────────────────────
	    
	    if ("축복술사".equals(curJob)) {
	        // 실제로 축복술사로 공격한 경우에만 여운 적용 (공격 안 하면 즉시 변경 가능)
	        AttackDeathStat blessAds = null;
	        try { blessAds = botNewService.selectAttackDeathStats(userName, roomName); } catch (Exception ignore) {}

	        if (blessAds != null && "축복술사".equals(blessAds.lastAttackJob) && blessAds.lastAttackTime != null) {
	            long diffMinutes = (System.currentTimeMillis() - blessAds.lastAttackTime.getTime()) / (1000 * 60);
	            if (diffMinutes < 30) {
	                long remain = 30 - diffMinutes;
	                return "축복술사는 축복의 여운이 남아 " + remain + "분 동안 직업 변경이 불가능합니다.";
	            }
	        }
	    }

	    if(!master) {
	    	// 5-0) 해당 유저의 직업별 공격횟수 전체 조회 (쿼리 1번)
		    Map<String, Integer> jobCntMap = Collections.emptyMap();
		    int totalCnt = 0;

		    try {
		        jobCntMap = botNewService.selectBattleCountByUser(userName, roomName);
		    } catch (Exception e) {
		        e.printStackTrace();
		        jobCntMap = new HashMap<String, Integer>();
		    }

		    // 전체 공격횟수 = 모든 직업 CNT 합
		    for (Integer v : jobCntMap.values()) {
		        if (v != null) {
		            totalCnt += v;
		        }
		    }

		    // // 5-1) 직업별 전직 조건 체크 (전사 100, 도적 100 같은 것들)
// 		    List<JobChangeReq> reqList = MiniGameUtil.JOB_CHANGE_REQS.get(newJob);
// 		    if (reqList != null && !reqList.isEmpty()) {
// 		        StringBuilder sb = new StringBuilder();
// 
// 		        for (JobChangeReq req : reqList) {
// 		            int curCnt = jobCntMap.getOrDefault(req.baseJob, 0);
// 
// 		            if (curCnt < req.minCount) {
// 		                sb.append("- [")
// 		                  .append(req.baseJob)
// 		                  .append("] 직업으로 ")
// 		                  .append(req.minCount)
// 		                  .append("회 이상 공격 필요 (현재: ")
// 		                  .append(curCnt)
// 		                  .append("회)")
// 		                  .append(NL);
// 		            }
// 		        }
// 
// 		        if (sb.length() > 0) {
// 		            return "[" + newJob + "] 직업은 아래 조건을 모두 만족해야 전직 가능합니다." + NL
// 		                 + sb.toString().trim();
// 		        }
// 		    }
// 
// 		    // 5-2) 전체 공격 횟수 조건 체크
// 		    Integer totalReq = MiniGameUtil.JOB_CHANGE_TOTAL_REQS.get(newJob);
// 		    if (totalReq != null) {
// 		        if (totalCnt < totalReq) {
// 		            return "[" + newJob + "] 직업은 전체 공격 횟수 "
// 		                 + totalReq + "회를 달성해야 전직 가능합니다. (현재: "
// 		                 + totalCnt + "회)";
// 		        }
// 		    }
	    }
	 // 변경 전 HP 비율 저장
	    double hpRatio = (double) ctx.hpCur / (double) ctx.hpMax;
	    
	    // 6) 직업 변경 수행 (JOB + JOB_CHANGE_DATE = SYSDATE)
	    int updated = botNewService.updateUserJobAndChangeDate(userName, roomName, newJob);
	    if (updated <= 0) {
	        return "직업 변경 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
	    }
	    try {
	    	botNewService.closeOngoingBattleTx(userName, roomName);
	    }catch(Exception e){
	    	
	    }
	    
	    UserBattleContext ctx2 = calcUserBattleContext(map);

	    // HP 비율 유지
	    long newHp = (long)Math.max(1, Math.floor(ctx2.hpMax * hpRatio));
	    botNewService.updateUserHpOnlyTx(userName, roomName, (int) newHp);
	    
	    
	    // 7) 완료 메시지
	    return "✨ " + userName + "님, [" + newJob + "] 으로 직업이 변경되었습니다." + NL;
	}

	public String invenInfo(HashMap<String, Object> map) {

	    // [OPT-INVEN] calcUserBattleContext 대신 targetUser/roomName 만 경량 추출
	    final String _uName = Objects.toString(map.get("userName"), "");
	    if (_uName.isEmpty()) return "유저 정보가 누락되었습니다.";
	    String _target = _uName;
	    final String _p1 = Objects.toString(map.get("param1"), "").trim();
	    if (!_p1.isEmpty()) {
	        List<String> found = botNewService.selectParam1ToNewUserSearch(map);
	        if (found != null && !found.isEmpty()) {
	            _target = found.get(0);
	        } else {
	            return "해당 유저(" + _p1 + ")를 찾을 수 없습니다.";
	        }
	    }
	    final String userName = _target;
	    final String roomName = Objects.toString(map.get("roomName"), "");

	    StringBuilder sb = new StringBuilder();
	    List<HashMap<String, Object>> bag =
	            botNewService.selectInventorySummaryAll(userName, roomName);

	    if (bag == null || bag.isEmpty()) {
	        sb.append("✨").append(userName).append(" 인벤토리");
	        sb.append(ALL_SEE_STR);
	        sb.append("- 인벤토리가 비어있습니다.");
	        return sb.toString();
	    }

	    sb.append("✨").append(userName).append(" 인벤토리 (총 ").append(bag.size()).append("개)");
	    sb.append(ALL_SEE_STR);

	    // 카테고리 버킷 (행운/반지/토템/선물은 합계 표기로 통합)
	    Map<String, List<String>> catMap = new LinkedHashMap<>();
	    catMap.put("※무기", new ArrayList<>());
	    catMap.put("※투구", new ArrayList<>());
	    catMap.put("※갑옷", new ArrayList<>());
	    catMap.put("※전설", new ArrayList<>());
	    catMap.put("※유물", new ArrayList<>());
	    catMap.put("※지옥", new ArrayList<>());
	    catMap.put("※날개", new ArrayList<>());
	    catMap.put("※보스", new ArrayList<>());
	    catMap.put("※업적", new ArrayList<>());
	    catMap.put("※기타", new ArrayList<>());

	    for (HashMap<String, Object> row : bag) {

	        int itemId = safeInt(row.get("ITEM_ID"));
	        String itemName = Objects.toString(row.get("ITEM_NAME"), "");
	        String type = Objects.toString(row.get("ITEM_TYPE"), "");
	        int qty = safeInt(row.get("TOTAL_QTY"));

	        if (itemName.isEmpty()) continue;

	        // 행운/반지/토템/선물 → 합계 표기, 개별 나열 생략
	        if ((itemId >= 300 && itemId < 400) || (itemId >= 500 && itemId < 700) || (itemId >= 900 && itemId < 1000)) continue;

	        String cat = resolveItemCategory(itemId);
	        String label = itemName;

	        // ─────────────────
	        // 장비 / 전설 / 날개 / 토템 /업적
	        // ─────────────────
	        if ("MARKET".equalsIgnoreCase(type)
	            || "MASTER".equalsIgnoreCase(type)
	            || "BAG_OPEN".equalsIgnoreCase(type)
	            || "BAG_OPEN_NM".equalsIgnoreCase(type)
	            || "ACHV".equalsIgnoreCase(type)
	        		) {
	        	
	        	
	        	String opt = MiniGameUtil.buildEnhancedOptionLine(row, 1);
	            if (!opt.isEmpty()) {
	                label += opt;
	            }
	        }
	        // ─────────────────
	        // 보스 아이템 (7000번대)
	        // ─────────────────
	        else if ("BOSS_HELL".equalsIgnoreCase(type) || "BOSS_GACHA".equalsIgnoreCase(type)) {
	            if (qty > 1) label += "x" + qty;
	            String opt = MiniGameUtil.buildEnhancedOptionLine(row, 1);
	            if (!opt.isEmpty()) label += opt;
	            String desc = Objects.toString(row.get("ITEM_DESC"), "").trim();
	            if (!desc.isEmpty()) label += " (" + desc + ")";
	            label += "BOSS_GACHA".equalsIgnoreCase(type) ? " [뽑기]" : " [보스처치]";
	        }
	        // ─────────────────
	        // 지옥 각인 (3000번대)
	        // ─────────────────
	        else if ("HELL_BOX".equalsIgnoreCase(type) && itemId >= 3000 && itemId < 4000) {
	            label += " +" + qty + " [지옥]";
	        }
	        else {
	            if (qty > 1) {
	                label += "x" + qty;
	            }
	        }

	        List<String> bucket = catMap.getOrDefault(cat, catMap.get("※기타"));
	        bucket.add(label);
	    }

	    // 출력 (※날개 뒤에 행운/반지/토템/선물 합계 삽입)
	    for (Map.Entry<String, List<String>> e : catMap.entrySet()) {
	        List<String> list = e.getValue();
	        if (!list.isEmpty()) {
	            sb.append(e.getKey()).append(":").append(NL);
	            for (String s : list) {
	                sb.append(", ").append(s).append(NL);
	            }
	        }
	        // 날개 출력 직후 합계 삽입
	        if ("※날개".equals(e.getKey())) {
	            for (int[] gr : new int[][]{{300,400},{500,600},{600,700},{900,1000}}) {
	                String gl = gr[0]==300?"행운":gr[0]==500?"반지":gr[0]==600?"토템":"선물";
	                String line = buildGroupSummaryLine(bag, gr[0], gr[1], gl);
	                if (line != null) sb.append(line).append(NL);
	            }
	        }
	    }

	    sb.append(NL);
	    try {
	        List<HashMap<String,Object>> drops =
	                botNewService.selectTotalDropItems(userName);

	        if (drops != null && !drops.isEmpty()) {

	            sb.append(NL);

	            Map<String, DropSummary> summaryMap = new LinkedHashMap<>();

	            for (HashMap<String,Object> row : drops) {

	                String rawName  = Objects.toString(row.get("ITEM_NAME"), "");
	                String gainType = Objects.toString(row.get("GAIN_TYPE"), "");
	                int qty         = safeInt(row.get("TOTAL_QTY"));

	                if (qty <= 0 || rawName.isEmpty()) continue;

	                // 🔹 아이템명 정규화 (접두/접미 제거)
	                String itemName = rawName
	                        .replace("조각", "")
	                        .replace("빛", "")
	                        .replace("어둠", "")
	                        .replace("음양", "");

	                DropSummary s = summaryMap.computeIfAbsent(itemName, k -> new DropSummary());

	                switch (gainType) {
	                    case "DROP":   // 일반 드랍
	                        s.normal += qty;
	                        break;
	                    case "STEAL":  // 조각
	                        s.fragment += qty;
	                        break;
	                    case "DROP3":  // 빛
	                        s.light += qty;
	                        break;
	                    case "DROP5":  // 어둠
	                        s.dark += qty;
	                        break;
	                    case "DROP9":  // 음양
	                        s.gray += qty;
	                        break;
	                }
	            }

	            // 잡템 총 갯수 계산
	            long totalDropQty = summaryMap.values().stream()
	                    .mapToLong(s -> s.normal + s.fragment + s.light + s.dark + s.gray)
	                    .sum();
	            sb.append("▶ 누적 획득 드랍 아이템 : 총 ").append(totalDropQty).append("개").append(NL)
	              .append("{ 일반 / 조각 / 빛 / 어둠 / 음양 }").append(NL);

	            // 출력
	            for (Map.Entry<String, DropSummary> e : summaryMap.entrySet()) {
	                DropSummary s = e.getValue();

	                sb.append(e.getKey())
	                  .append(" : { ")
	                  .append(s.normal).append(" / ")
	                  .append(s.fragment).append(" / ")
	                  .append(s.light).append(" / ")
	                  .append(s.dark).append(" / ")
	                  .append(s.gray)
	                  .append(" }")
	                  .append(NL);
	            }
	        }

	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }

	    
	    return sb.toString();
	}

	
	public String attackInfo(HashMap<String, Object> map) {

	    // [OPT-HUNTER] calcUserBattleContext 호출 전에 ctx.targetUser 해석 + 통계/드랍 미리 조회
	    // - selectBattleStatsByJob 1회로 totalAttacks/totalDeaths/jobAtkMap/lastAtkTime 모두 확보
	    // - selectTotalDropItems 1회로 헌터 bonus + applyDropBonusToContext 공유
	    // → calcUserBattleContext 내부의 중복 DB 조회(selectAttackDeathStats, selectTotalDropItems×2) 제거
	    {
	        final String _uName = Objects.toString(map.get("userName"), "");
	        String _target = _uName;
	        final String _p1 = Objects.toString(map.get("param1"), "").trim();
	        if (!_uName.isEmpty()) {
	            if (!_p1.isEmpty()) {
	                List<String> found = botNewService.selectParam1ToNewUserSearch(map);
	                if (found != null && !found.isEmpty()) _target = found.get(0);
	            }
	            // 통합 전투 통계 미리 조회 (1회 스캔)
	            List<HashMap<String,Object>> statRows = null;
	            try { statRows = botNewService.selectBattleStatsByJob(_target); } catch (Exception ignore) {}
	            int _totalAtk = 0, _totalDth = 0;
	            int _hunterCnt = 0;
	            if (statRows != null) {
	                for (HashMap<String,Object> r : statRows) {
	                    int cnt = safeInt(r.get("CNT"));
	                    String j = Objects.toString(r.get("JOB"), "").trim();
	                    _totalAtk += cnt;
	                    _totalDth += safeInt(r.get("DEATH_CNT"));
	                    if ("헌터".equals(j)) _hunterCnt = cnt;
	                }
	            }
	            // 헌터 bonus 계산용 조정값 (calcUserBattleContext에서 selectAttackDeathStats 대체)
	            map.put("_preHunterAdjAttacks", _totalAtk + _hunterCnt * 2);
	            map.put("_preHunterAdjDeaths",  _totalDth + _hunterCnt / 2);
	            // statRows 자체도 저장 (attackInfo에서 totalAttacks/jobAtkMap/lastAtkTs 도출용)
	            map.put("_preStatRows", statRows);
	            // 드랍 아이템 미리 조회 (헌터 bonus + applyDropBonusToContext 공유)
	            List<HashMap<String,Object>> preDropRows = null;
	            try { preDropRows = botNewService.selectTotalDropItems(_target); } catch (Exception ignore) {}
	            map.put("_preDropRows", preDropRows);
	        }
	    }

	    // [OPT-HUNTER] pre-fetched statRows에서 totalAttacks/totalDeaths/jobAtkMap/lastAtkTs 도출
	    // (selectAttackDeathStats + selectBattleCountByUser 2회 → 0회)
	    int totalAttacks = 0;
	    int totalDeaths  = 0;
	    java.sql.Timestamp lastAtkTs = null;
	    Map<String, Integer> jobAtkMap = new HashMap<>();
	    {
	        @SuppressWarnings("unchecked")
	        List<HashMap<String,Object>> statRows = (List<HashMap<String,Object>>) map.get("_preStatRows");
	        if (statRows != null) {
	            for (HashMap<String,Object> r : statRows) {
	                int cnt = safeInt(r.get("CNT"));
	                totalAttacks += cnt;
	                totalDeaths  += safeInt(r.get("DEATH_CNT"));
	                String j = Objects.toString(r.get("JOB"), "").trim();
	                if (!j.isEmpty()) jobAtkMap.put(j, cnt);
	                Object dtObj = r.get("MAX_DATE");
	                if (dtObj instanceof java.sql.Timestamp) {
	                    java.sql.Timestamp dt = (java.sql.Timestamp) dtObj;
	                    if (lastAtkTs == null || dt.after(lastAtkTs)) lastAtkTs = dt;
	                }
	            }
	        }
	    }
	    UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        return ctx.errorMessage;
	    }

	    final User   u          = ctx.user;



	    // [FIX3] calcUserBattleContext에서 제거된 selectCurrentPoint를 여기서 직접 조회
	    try {
	        HashMap<String,Object> pointRow = botNewService.selectCurrentPoint(ctx.targetUser, "");
	        double curValue = Double.parseDouble(Objects.toString(pointRow.get("SCORE"), "0"));
	        String curExt = Objects.toString(pointRow.get("SCORE_EXT"), "");
	        SP userPoint = new SP(curValue, curExt);
	        ctx.currentPointStr = userPoint.toString();
	        ctx.currentPoint = userPoint;
	    } catch (Exception ignore) {}



	    // ⑧ 킬 통계
	    List<KillStat> kills = botNewService.selectKillStats(ctx.targetUser, ctx.roomName);
	    int totalKills = 0;
	    for (KillStat ks : kills) totalKills += ks.killCount;

	    
	    
	 // === NEW: 일별 공격 통계 (어제 자정까지) ===
	    Date firstAttackDay = null;
	    Date maxAttackDay   = null;
	    int  maxAttackCnt   = 0;
	    int  avgAttackPerDay = 0;
	    int  todayAttackCnt  = 0;   // ★ 추가
	    Date today = truncateToDate(new Date()); // ★ 오늘 기준일


	    try {
	        List<HashMap<String,Object>> dailyList =
	                botNewService.selectDailyAttackCounts(ctx.targetUser, ctx.roomName);

	        if (dailyList != null && !dailyList.isEmpty()) {
	            int totalAtkBeforeToday = 0;
	            int activeDays = 0; // ★ 어제까지 실제 일수

	            for (int i = 0; i < dailyList.size(); i++) {
	                HashMap<String,Object> row = dailyList.get(i);
	                if (row == null) continue;

	                Object dayObj = row.get("ATTACK_DAY");
	                Date day = null;
	                if (dayObj instanceof Date) {
	                    day = truncateToDate((Date) dayObj);
	                } else if (dayObj instanceof java.sql.Date) {
	                    day = truncateToDate(new Date(((java.sql.Date)dayObj).getTime()));
	                }

	                int cnt = safeInt(row.get("ATK_CNT"));

	                if (day == null) continue;

	                // 최초 공격일
	                if (firstAttackDay == null) {
	                    firstAttackDay = day;
	                }

	                // 최대 공격일
	                if (cnt > maxAttackCnt) {
	                    maxAttackCnt = cnt;
	                    maxAttackDay = day;
	                }

	                // ★ 오늘 공격
	                if (day.equals(today)) {
	                    todayAttackCnt = cnt;
	                } else {
	                    // ★ 어제까지 누적/평균용
	                    totalAtkBeforeToday += cnt;
	                    activeDays++;
	                }
	            }

	            // ★ 일평균 = 어제까지 기준
	            if (activeDays > 0) {
	                avgAttackPerDay = totalAtkBeforeToday / activeDays;
	            }
	        }
	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }
	    // 🔹 몬스터 캐시 — MONSTER_CACHE 직접 사용 (selectAllMonsters DB 조회 제거)
	    if (MiniGameUtil.MONSTER_CACHE.isEmpty()) {
	        // 서버 기동 시 initCache 미실행 대비 fallback
	        List<Monster> monList = botNewService.selectAllMonsters();
	        if (monList != null) {
	            for (Monster mm : monList) MiniGameUtil.MONSTER_CACHE.put(mm.monNo, mm);
	        }
	    }
	    Map<Integer, Monster> monMap = MiniGameUtil.MONSTER_CACHE;

	    Monster target = (u.targetMon > 0) ? monMap.get(u.targetMon) : null;
	    String targetName;
	    if (u.targetMon == 99)       targetName = "상급악마 (헬보스)";
	    else if (target == null)     targetName = "-";
	    else                         targetName = target.monName;

	    
	    List<HashMap<String, Object>> bag = botNewService.selectInventorySummaryAll(ctx.targetUser, ctx.roomName);
	    
	    // ⑨ 출력
	    StringBuilder sb = new StringBuilder();
	    sb.append("✨").append(ctx.targetUser).append(" 공격 정보").append(NL)
	      .append("Lv: ").append(u.lv);
	    if (!ctx.job.isEmpty()) {
	        sb.append(" (").append(ctx.job).append(")");
	        
	        //헌터랭크추가
        	sb.append("(hunter"+ctx.hunterGrade+")");
	        
	    }
	    sb.append(", EXP ").append(u.expCur).append("/").append(u.expNext).append(NL);
	    sb.append("포인트: ").append(ctx.currentPointStr).append(NL);
	    if (ctx.gpBalance > 0) {
	        sb.append("GP: ").append(String.format("%.2f", ctx.gpBalance)).append(NL);
	    }
	    sb.append("누적 획득 포인트: ").append(ctx.lifetimeSpStr).append(NL).append(NL);

	    if ("곰".equals(ctx.job)) {
			sb.append("⚔ATK: ").append("최대체력으로 공격").append(NL);
		}else {
			sb.append("⚔ATK: ").append(ctx.atkMin).append(" ~ ").append(ctx.atkMax).append(NL);
			sb.append("⚔CRIT: ").append(ctx.crit).append("%  CDMG ").append(ctx.critDmg).append("%").append(NL);
		}
	    
	    sb.append("❤️HP: ").append(ctx.hpCur).append(" / ").append(ctx.hpMax)
	      .append(",5분당회복+").append(ctx.regen).append(NL);

	    // 헬모드 삭감 정보 표시
	    if (u.nightmareYn == 2) {
	        double hellMult = MiniGameUtil.getHellNerfMult(ctx.hunterGrade);
	        int reductionPct = (int) Math.round((1.0 - hellMult) * 100);
	        //int basePct = (int) Math.round((1.0 - MiniGameUtil.HEL_NERF_BASE) * 100);
	        sb.append("[헬모드] 능력치 삭감 ").append(reductionPct).append("%");
	        sb.append(" (hunter").append(ctx.hunterGrade).append(")");
	        sb.append(NL);
	    }
	    sb.append(NL);

	    /*
	    if (ctx.isJobMaster) {
	        sb.append(ctx.job).append(" 마스터 보너스: ATK 10%, HP 15%, 리젠+1000").append(NL);
	    }
	     */
        sb.append("▶ 현재 타겟: ").append(targetName)
	      .append(" (MON_NO=").append(u.targetMon).append(")").append(NL);

	    // 누적 전투
        sb.append("http://rgb-tns.dev-apc.com/loa/user-info-view?userName="+ctx.targetUser);
	    sb.append( ALL_SEE_STR);

	    JobDef jobDef = MiniGameUtil.JOB_DEFS.get(ctx.job);
	    if (jobDef != null && jobDef.attackLine != null && !jobDef.attackLine.isEmpty()) {
	        sb.append(jobDef.attackLine).append(NL).append(NL);
	    }
		// ─ ATK 상세 ─
		if ("곰".equals(ctx.job)) {
			sb.append("⚔ATK: ").append("최대체력으로 공격").append(NL);
		} else {
			sb.append("⚔ATK: ").append(ctx.atkMin).append(" ~ ").append(ctx.atkMax).append(NL).append("   └ 기본 (")
					.append(ctx.baseAtkMin).append("~").append(ctx.baseAtkMax).append(")").append(NL)
					.append("   └ 아이템 (min").append(formatSigned(ctx.mktAtkMin)).append(", max")
					.append(formatSigned(ctx.mktAtkMax)).append(")").append(NL);
			if (ctx.mktAtkMaxRate > 0) {
				sb.append("   └ 최종공격력 (").append(formatSigned(ctx.mktAtkMaxRate)).append("%)").append(NL);
			}
			if (ctx.hellNerfAtkMax > 0) {
				sb.append("   └ 모드 (min-").append(ctx.hellNerfAtkMin).append("~, max-").append(ctx.hellNerfAtkMax).append(")")
						.append(NL);
			}

		}
	    
	    if("곰".equals(ctx.job)) {
	    	
	    }else {
	    	// ─ CRIT 상세 ─
		    sb.append("⚔CRIT: ").append(ctx.crit).append("%  CDMG ").append(ctx.critDmg).append("%").append(NL)
		      .append("   └ 기본 (").append(u.critRate).append("%, ").append(ctx.critDmg).append("%)").append(NL);
			sb.append("   └ 아이템 (CRIT").append(formatSigned(ctx.mktCrit)).append("%, CDMG ").append(formatSigned(ctx.mktCritDmg)).append("%)").append(NL);
			if (ctx.hellNerfCrit != 0 ) {
				sb.append("   └ 모드 (CRIT-").append(ctx.hellNerfCrit).append("%, CDMG -").append(ctx.hellNerfCritDmg).append("%)").append(NL);
		    }
	    }
	    
	    // ─ HP 상세 ─
	    sb.append("❤️HP: ").append(ctx.hpCur).append(" / ").append(ctx.hpMax)
	      .append(",5분당회복+").append(ctx.regen).append(NL)
	      .append("   └ 기본 (HP+").append(ctx.baseHpMax)
	      .append(",5분당회복+").append(u.hpRegen).append(")").append(NL)
	      .append("   └ 아이템 (HP").append(formatSigned(ctx.mktHpMax))
	      .append(",5분당회복").append(formatSigned(ctx.mktRegen)).append(")").append(NL);
	    if (ctx.mktHpMaxRate > 0) {
	    	sb.append("   └ 최종체력 (").append(formatSigned(ctx.mktHpMaxRate)).append("%)").append(NL);
	    }

	    if (ctx.jobHp != 0 || ctx.jobRegen != 0) {
	        sb.append("   └ 직업 (HP")
	          .append(formatSigned(ctx.jobHp))
	          .append(",5분당회복")
	          .append(formatSigned(ctx.jobRegen))
	          .append(")").append(NL);
	    }
	    if (ctx.hellNerfHp != 0 ) {
	        sb.append("   └ 모드 (HP -")
	          .append(ctx.hellNerfHp)
	          .append(")").append(NL);
	    }
	    
	    String relicSummary = buildRelicSummaryLine(bag,9000);
        if (relicSummary != null) {
            sb.append(NL).append(relicSummary).append(NL);
        }
        String relicSummary2 = buildRelicSummaryLine(bag,8000);
        if (relicSummary2 != null) {
        	sb.append(NL).append(relicSummary2).append(NL);
        }
        
        
        if (ctx.dropAtkMin +ctx.dropAtkMax +  ctx.dropHp + ctx.dropRegen
                + ctx.dropCrit + ctx.dropCritDmg > 0) {

        	sb.append(NL).append("✨어둠 부가 효과: ");
            if (ctx.dropAtkMin > 0) sb.append("min_ATK+").append(ctx.dropAtkMin).append(" ");
            if (ctx.dropAtkMax > 0) sb.append("max_ATK+").append(ctx.dropAtkMax).append(" ");
            if (ctx.dropHp > 0) sb.append("HP+").append(ctx.dropHp).append(" ");
            if (ctx.dropRegen > 0) sb.append("체젠+").append(ctx.dropRegen).append(" ");
            if (ctx.dropCrit > 0) sb.append("치확+").append(ctx.dropCrit).append("% ");
            if (ctx.dropCritDmg > 0) sb.append("치피+").append(ctx.dropCritDmg).append("% ");
            sb.append(NL);
        }


	    // ─ 세트 효과 ─
	    boolean hasSetEffect = ctx.setCooldownReduce > 0 || ctx.setAtkFinalRate > 0 || ctx.setCritFinalRate > 0
	            || ctx.setEvasionRate > 0 || (ctx.activeSetSpecials != null && !ctx.activeSetSpecials.isEmpty());
	    if (hasSetEffect) {
	        sb.append(NL).append("※ 세트 효과:").append(NL);
	        if (ctx.setAtkFinalRate > 0)
	            sb.append("  └ 최종공격력 +").append(ctx.setAtkFinalRate).append("%").append(NL);
	        if (ctx.setCritFinalRate > 0)
	            sb.append("  └ 최종크리율 +").append(ctx.setCritFinalRate).append("%").append(NL);
	        if (ctx.setCooldownReduce > 0)
	            sb.append("  └ 쿨타임 -").append(ctx.setCooldownReduce).append("%").append(NL);
	        if (ctx.setEvasionRate > 0)
	            sb.append("  └ 회피율 ").append(ctx.setEvasionRate).append("%").append(NL);
	        if (ctx.activeSetSpecials != null) {
	            for (String sp : ctx.activeSetSpecials) sb.append("  └ ").append(sp).append(NL);
	        }
	    }

	    // ─ 인벤토리 ─
	    try {
	        sb.append(NL).append("▶ 인벤토리<옵션:/인벤>").append(NL);
	        if (bag == null || bag.isEmpty()) {
	            sb.append("- (비어있음)").append(NL);
	        } else {
	            // 1) ITEM_ID ASC 정렬
	            bag.sort((a, b) -> {
	                int noA = MiniGameUtil.parseIntSafe(Objects.toString(a.get("ITEM_ID"), "0"));
	                int noB = MiniGameUtil.parseIntSafe(Objects.toString(b.get("ITEM_ID"), "0"));
	                return Integer.compare(noA, noB);
	            });

	            // 2) 카테고리별 버킷 생성
	            Map<String, List<String>> catMap = new LinkedHashMap<>();
	            catMap.put("※무기", new ArrayList<>());
	            catMap.put("※투구", new ArrayList<>());
	            catMap.put("※갑옷", new ArrayList<>());
	            catMap.put("※전설", new ArrayList<>());
	            catMap.put("※유물", new ArrayList<>());
	            catMap.put("※지옥", new ArrayList<>());
	            catMap.put("※날개", new ArrayList<>());
	            catMap.put("※보스", new ArrayList<>());
	            catMap.put("※업적", new ArrayList<>());
	            catMap.put("※기타", new ArrayList<>());

	            // 3) 인벤토리 한 줄씩 카테고리 분류
	            for (HashMap<String, Object> row : bag) {
	                if (row == null) continue;

	                String itemName = Objects.toString(row.get("ITEM_NAME"), "-");
	                String qtyStr   = Objects.toString(row.get("TOTAL_QTY"), "0");
	                String typeStr  = Objects.toString(row.get("ITEM_TYPE"), "");
	                int itemId      = MiniGameUtil.parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));

	                if (itemName == null || itemName.trim().isEmpty()) continue;

	                // 행운/반지/토템/선물 → 합계 표기, 개별 나열 생략
	                if ((itemId >= 300 && itemId < 400) || (itemId >= 500 && itemId < 700) || (itemId >= 900 && itemId < 1000)) continue;

	                // 수량 파싱
	                int qtyVal = MiniGameUtil.parseIntSafe(qtyStr);
	                if (qtyVal < 1) qtyVal = 1; // 최소 1

	                String label = itemName;
	                boolean isEquipType =
	                        "MARKET".equalsIgnoreCase(typeStr) ||
	                        "BAG_OPEN".equalsIgnoreCase(typeStr) ||
	                        "BAG_OPEN_NM".equalsIgnoreCase(typeStr) ||
	                        "MASTER".equalsIgnoreCase(typeStr) || 
	                        "ACHV".equalsIgnoreCase(typeStr) 
	                        ;

	                if ("BOSS_HELL".equalsIgnoreCase(typeStr) || "BOSS_GACHA".equalsIgnoreCase(typeStr)) {
	                    if (qtyVal > 1) label += "x" + qtyVal;
	                    String opt = MiniGameUtil.buildEnhancedOptionLine(row, 1);
	                    if (!opt.isEmpty()) label += opt;
	                    String bossDesc = Objects.toString(row.get("ITEM_DESC"), "").trim();
	                    if (!bossDesc.isEmpty()) label += " (" + bossDesc + ")";
	                    label += "BOSS_GACHA".equalsIgnoreCase(typeStr) ? " [뽑기]" : " [보스처치]";
	                } else if ("HELL_BOX".equalsIgnoreCase(typeStr) && itemId >= 3000 && itemId < 4000) {
	                    label += " +" + qtyVal + " [지옥]";
	                } else if (isEquipType) {
	                	
	                } else {
	                    if (qtyVal > 1) {
	                        label = label + "x" + qtyVal;
	                    }
	                }

	                String cat = resolveItemCategory(itemId);

	                List<String> bucket = catMap.get(cat);
	                if (bucket == null) {
	                    bucket = catMap.get("※기타");
	                }
	                bucket.add(label);
	            }

	            // 4) 카테고리별 출력 (※날개 뒤에 행운/반지/토템/선물 합계 삽입)
	            for (Map.Entry<String, List<String>> e : catMap.entrySet()) {
	                List<String> list = e.getValue();
	                if (list != null && !list.isEmpty()) {
	                    int max = getMaxAllowedByCategoryLabel(e.getKey());
	                    if (max != Integer.MAX_VALUE) {
	                        sb.append(e.getKey()).append("(최대").append(max).append("개)").append(": ");
	                    } else {
	                        sb.append(e.getKey()).append(": ");
	                    }
	                    sb.append(String.join(", ", list));
	                    sb.append(NL);
	                }
	                // 날개 출력 직후 합계 삽입
	                if ("※날개".equals(e.getKey())) {
	                    for (int[] gr : new int[][]{{300,400},{500,600},{600,700},{900,1000}}) {
	                        String gl = gr[0]==300?"행운":gr[0]==500?"반지":gr[0]==600?"토템":"선물";
	                        String line = buildGroupSummaryLine(bag, gr[0], gr[1], gl);
	                        if (line != null) sb.append(line).append(NL);
	                    }
	                }
	            }

	            sb.append(NL);
	        }
	    } catch (Exception ignore) {
	        ignore.printStackTrace();
	    }

	    // 물약 사용 횟수 (캐시 우선, 없으면 DB)
	    int potionUseCount = 0;
	    try {
	        potionUseCount = MiniGameUtil.POTION_USE_CACHE.containsKey(ctx.targetUser)
	                ? MiniGameUtil.POTION_USE_CACHE.get(ctx.targetUser)
	                : botNewService.selectPotionUseCount(ctx.targetUser);
	        MiniGameUtil.POTION_USE_CACHE.putIfAbsent(ctx.targetUser, potionUseCount);
	    } catch (Exception ignore) {}

	    sb.append("누적 전투 기록").append(NL)
	      .append("- 총 공격 횟수: ").append(totalAttacks).append("회").append(NL)
	      .append("- 총 사망 횟수: ").append(totalDeaths).append("회").append(NL)
	      .append("- 물약 사용 횟수: ").append(potionUseCount).append("회").append(NL).append(NL);

	    if (firstAttackDay != null) {
	        sb.append("시작일: ")
	          .append(formatDateYMD(firstAttackDay))
	          .append(NL);
	    } else {
	        sb.append("시작일: -").append(NL);
	    }

	    sb.append("- 일별 평균 공격(어제까지): ")
	      .append(avgAttackPerDay)
	      .append("회/일").append(NL);

	    if (maxAttackDay != null && maxAttackCnt > 0) {
	        sb.append("- 최고 공격: ")
	          .append(formatDateMD(maxAttackDay))
	          .append(" ")
	          .append(maxAttackCnt).append("회").append(NL);
	    } else {
	        sb.append("- 최고 공격: -").append(NL);
	    }
	    sb.append("- 오늘 공격: ")
	      .append(todayAttackCnt)
	      .append("회")
	      .append(NL);

		sb.append(NL);
		// === NEW: 직업별 공격 횟수 출력 ===
		if (jobAtkMap != null && !jobAtkMap.isEmpty()) {
			sb.append("직업별 공격 횟수").append(NL);

			List<String> rows = new ArrayList<>();
			List<String> jobNames = new ArrayList<>(jobAtkMap.keySet());
			Collections.sort(jobNames);

			for (String j : jobNames) {
				rows.add(j + ": " + String.format("%,d", jobAtkMap.get(j)) + "회");
			}

			for (int i = 0; i < rows.size(); i += 3) {
				sb.append("- ").append(String.join(" / ", rows.subList(i, Math.min(i + 3, rows.size())))).append(NL);
			}

			sb.append(NL);
		}

		// 누적 처치
		sb.append("누적 처치 기록 (총 ").append(totalKills).append("마리)").append(NL);

		if (kills == null || kills.isEmpty()) {
			sb.append("기록 없음").append(NL);
		} else {
			List<String> rows = new ArrayList<>();

			for (KillStat ks : kills) {
				String monName = ks.monName;
				if ((monName == null || monName.isEmpty()) && monMap != null) {
					Monster mm = monMap.get(ks.monNo);
					if (mm != null)
						monName = mm.monName;
				}
				rows.add(monName + ": " + String.format("%,d", ks.killCount) + "마리");
			}

			for (int i = 0; i < rows.size(); i += 3) {
				sb.append("- ").append(String.join(" / ", rows.subList(i, Math.min(i + 3, rows.size())))).append(NL);
			}
		}

	    // 업적
	    int achvCnt = 0;
	    int _nmBagCount = 0;
	    try {
	        HashMap<String,Object> invCounts = botNewService.selectAchievementInventoryCounts(ctx.targetUser);
	        if (invCounts != null) {
	            _nmBagCount = ((Number) invCounts.getOrDefault("NM_BAG_COUNT", 0)).intValue();
	        }
	    } catch (Exception ignore) {}
	    try {
	        List<HashMap<String,Object>> achv = botNewService.selectAchievementsByUser(ctx.targetUser, ctx.roomName);
	        achvCnt = (achv == null ? 0 : achv.size());

	        sb.append(NL).append("▶ 업적").append(" [").append(achvCnt).append("개]").append(NL);
	        if (achv == null || achv.isEmpty()) {
	            sb.append("- 달성된 업적이 없습니다.").append(NL);
	        } else {
	        	//renderAchievementSummary(sb, achv);
	        	//sb.append("(상세: /가방상세)").append(NL);
	            renderAchievementLinesCompact(sb, achv, monMap, _nmBagCount);
	        }
	    } catch (Exception ignore) {}

	    return sb.toString();
	}

	/**
	 * 특별 업적 소급 지급 (룰렛/직업마스터/학살자)
	 * - 업적 조회(/업적) 시 호출되어 아직 지급되지 않은 업적을 체크하고 지급
	 */
	private String grantSpecialHistoricalAchievements(String userName, String roomName) {
	    StringBuilder msg = new StringBuilder();

	    // 기달성 CMD 목록 로드
	    Set<String> doneCmds = new HashSet<>();
	    try {
	        List<HashMap<String,Object>> achvList = botNewService.selectAchievementsByUser(userName, roomName);
	        if (achvList != null) {
	            for (HashMap<String,Object> a : achvList) {
	                Object cmd = a.get("CMD");
	                if (cmd != null) doneCmds.add(cmd.toString());
	            }
	        }
	    } catch (Exception ignore) {}

	    final int ONE_A_SP   = 10_000;          // 1a
	    final int SLAYER_SP  = 10_000 * 1_000;  // 1000a

	    // ── 1. 룰렛 업적 ──────────────────────────────────────────
	    try {
	        HashMap<String,Object> buffStats = botNewService.selectMaxDailyBuffStats(userName);
	        if (buffStats != null) {
	            int rullet_max_atk_cnt = toSafeInt(buffStats.get("ATK_FULL_CNT"));
	            int rullet_max_cri_cnt = toSafeInt(buffStats.get("CRI_FULL_CNT"));
	            if (rullet_max_atk_cnt > 0 && !doneCmds.contains("ACHV_ROULETTE_ATK_100_"+rullet_max_atk_cnt)) {
	                String r = grantOnceIfEligibleFast(userName, roomName, "ACHV_ROULETTE_ATK_100_"+rullet_max_atk_cnt, ONE_A_SP, doneCmds);
	                if (r != null) msg.append(r).append(NL);
	            }
	            if (rullet_max_cri_cnt > 0 && !doneCmds.contains("ACHV_ROULETTE_CRI_300_"+rullet_max_cri_cnt)) {
	                String r = grantOnceIfEligibleFast(userName, roomName, "ACHV_ROULETTE_CRI_300_"+rullet_max_cri_cnt, ONE_A_SP, doneCmds);
	                if (r != null) msg.append(r).append(NL);
	            }
	        }
	    } catch (Exception ignore) {}

	    // ── 2. 직업마스터 시즌 업적 ────────────────────────────────
	    try {
	    	 List<HashMap<String,Object>> seasons = botNewService.selectJobMasterSeasons(userName);
	    	    if (seasons != null) {
	    	        for (HashMap<String,Object> s : seasons) {
	    	            String seasonKey = Objects.toString(s.get("SEASON_KEY"), "");
	    	            int masterCnt = Integer.parseInt(Objects.toString(s.get("MASTER_CNT"), "0"));
	    	            if (seasonKey.isEmpty()) continue;
	    	            String cmd = "ACHV_MASTER_SEASON_" + seasonKey+"_"+masterCnt;
	    	            if (!doneCmds.contains(cmd)) {
							String r = grantOnceIfEligibleFast(userName, roomName, cmd, ONE_A_SP, doneCmds);

	    	                if (r != null) msg.append(r).append(NL);
	    	            }
	    	        }
	    	    }
	    } catch (Exception ignore) {}

	    // ── 3. 학살자 시즌 업적 (TODO: 몬스터별 1위 구조로 재설계 예정 - 공격 시 체크로 이동)
	    try {

	        LocalDate today = LocalDate.now();

	        if (today.isBefore(LocalDate.of(2026,4,16))) {
	            return msg.toString().trim();
	        }
	        //이전데이터강제생성용
	        //if (today.getDayOfMonth() == 1 || today.getDayOfMonth() == 16) {

            LocalDate seasonStart;
            LocalDate seasonEnd;

            if (today.getDayOfMonth() >= 16) {
                // 16일~말일 실행 → 이번달 1~15 조회
                seasonStart = today.withDayOfMonth(1);
                seasonEnd = today.withDayOfMonth(15);
            } else {
                // 1일~15일 실행 → 전달 16~말일 조회
                LocalDate prevMonth = today.minusMonths(1);
                seasonStart = prevMonth.withDayOfMonth(16);
                seasonEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());
            }

            String seasonId = seasonStart.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 이미 생성된 시즌인지 체크 (몬스터1 기준)
            boolean seasonAlreadyDone = false;

            for (String c : doneCmds) {
                if (c.startsWith("ACHV_SLAYER_SEASON_" + seasonId + "_")) {
                    seasonAlreadyDone = true;
                    break;
                }
            }

            if (!seasonAlreadyDone) {

                HashMap<String,Object> param = new HashMap<>();
                param.put("seasonStart", seasonId);
                param.put("seasonEnd", seasonEnd.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

                List<HashMap<String,Object>> ranking =
                    botNewService.selectSlayerSeasonRank(param);

                if (ranking != null) {

                    for (HashMap<String,Object> row : ranking) {

                        int monLv = toSafeInt(row.get("TARGET_MON_LV"));
                        String topUser = Objects.toString(row.get("USER_NAME"), "");

                        if (!userName.equals(topUser)) continue;

                        String cmd = "ACHV_SLAYER_SEASON_" + seasonId + "_" + monLv;

                        if (!doneCmds.contains(cmd)) {

                            String r = grantOnceIfEligibleFast(
                                userName,
                                roomName,
                                cmd,
                                SLAYER_SP,
                                doneCmds
                            );

                            if (r != null) msg.append(r).append(NL);
                        }
                    }
                }
            }

	    } catch (Exception ignore) {}

	    return msg.toString().trim();
	}

	/** 안전한 int 변환 */
	private int toSafeInt(Object o) {
	    if (o == null) return 0;
	    try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; }
	}


	/** 타겟 변경 (번호/이름 허용) */
	public String changeTarget(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");
		final String input = Objects.toString(map.get("monNo"), "").trim();
		int nightmareYnVal = botNewService.getNightmareYn(userName, roomName);

		if (roomName.isEmpty() || userName.isEmpty()) return "방/유저 정보가 누락되었습니다.";
		if (input.isEmpty()) {
		    User u = botNewService.selectUser(userName, null);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("공격 타겟 목록입니다:").append(NL).append(NL)
		      .append("http://rgb-tns.dev-apc.com/loa/monster-view?userName="+userName).append(NL).append(NL)
		      .append("/ㄱㄱㅌㄱ 1 또는 /ㄱㄱㅌㄱ 토끼 로 설정").append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterSelectLine(mm, nightmareYnVal));
		    }
		    if (nightmareYnVal == 2) {
		        sb.append(ALL_SEE_STR).append(NL)
		          .append("▶ [헬전용] 99 또는 보스 → [상급악마]").append(NL);
		    }

		    return sb.toString();
		}

		if(roomName.equals("람쥐봇 문의방")) {

			if(userName.equals("일어난다람쥐/카단")) {
			}else {
				return "문의방에서는 불가능합니다.";
			}
		}

		// 헬보스(상급악마) 타겟 설정: 헬모드 전용, 곰 불가
		if ("99".equals(input) || "보스".equals(input) || "상급악마".equals(input)) {
		    if (nightmareYnVal != 2)
		        return userName + "님, [상급악마]는 헬모드 유저만 타겟으로 설정할 수 있습니다.";
		    User uForJob = botNewService.selectUser(userName, roomName);
		    String jobVal = (uForJob != null && uForJob.job != null) ? uForJob.job.trim() : "";
		    if ("곰".equals(jobVal))
		        return userName + "님, [곰]은 상급악마를 공격할 수 없습니다.";
		    botNewService.closeOngoingBattleTx(userName, roomName);
		    botNewService.updateUserTargetMonTx(userName, roomName, 99);
		    // 현재 스페셜타임 정보 추가
		    String hellTargetMsg = userName + "님, 공격 타겟을 [상급악마](MON_NO=99) 으로 설정했습니다." + NL
		         + "※ /ㄱ 으로 헬보스를 공격합니다.";
		    try {
		        long nowMs2 = System.currentTimeMillis();
		        HashMap<String,Object> spBuff = (nowMs2 - SPECIAL_BUFF_CACHE_TS < SPECIAL_BUFF_CACHE_TTL_MS)
		                ? SPECIAL_BUFF_CACHE : botNewService.selectActiveSpecialBuff();
		        if (spBuff != null) {
		            String fc   = Objects.toString(spBuff.get("FLAG_CODE"), "");
		            String et   = Objects.toString(spBuff.get("EFFECT_TYPE"), "");
		            double ev   = spBuff.get("EFFECT_VALUE") != null
		                    ? Double.parseDouble(spBuff.get("EFFECT_VALUE").toString()) : 0;
		            java.util.Date endT = (java.util.Date) spBuff.get("END_TIME");
		            String endStr = endT != null
		                    ? endT.toInstant().atZone(java.time.ZoneId.systemDefault())
		                        .toLocalDateTime()
		                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
		                    : "?";
		            hellTargetMsg += NL + "✨스페셜타임 진행중! [" + buildBuffDescription(fc, et, ev) + ", " + endStr + "까지]";
		        }
		    } catch (Exception ignore) {}
		    return hellTargetMsg;
		}

		Monster m = input.matches("\\d+")
		        ? getMonsterCached(Integer.parseInt(input))
		        : botNewService.selectMonsterByName(input);

		if (m == null) {
			 // 유저 레벨 조회 (없으면 Lv1 기준)
		    User u = botNewService.selectUser(userName, null);
		    int userLv = (u != null ? u.lv : 1);

		    List<Monster> monsters = botNewService.selectAllMonsters();
		    StringBuilder sb = new StringBuilder();
		    sb.append("해당 몬스터(").append(input).append(")를 찾을 수 없습니다.").append(NL)
		      .append("아래 목록 중에서 선택해주세요:").append(NL).append(NL)
		      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);

		    for (Monster mm : monsters) {
		        sb.append(renderMonsterSelectLine(mm,  nightmareYnVal));
		    }
		    return sb.toString();
		}

		User u = botNewService.selectUser(userName, null);
		if (u == null) {
		    botNewService.insertUserWithTargetTx(userName, roomName, m.monNo);
		    return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		         + "▶ 선택: " + renderMonsterSelectLine(m,  nightmareYnVal);
		}
		if (u.targetMon == m.monNo) return "현재 타겟이 이미 " + m.monName + "(MON_NO=" + m.monNo + ") 입니다.";

		


		// 예: 사용자가 /공격타겟 13 입력 → newMonNo = 13
		/*
		int newMonNo = m.monNo; // 네가 사용하는 변수명에 맞게 치환

		if(newMonNo > 1  && newMonNo < 50) {
			// 1) 바로 아래 등급 몬스터 번호 계산
			int prevMonNo = Math.max(1, newMonNo - 1);

			// 2) 해당 몬스터를 내가 몇 마리 잡았는지 조회 (기존 selectKillStats 재사용)
			int killsOnPrev = 0;
			List<KillStat> killStats = botNewService.selectKillStats(userName, roomName);
			if (killStats != null) {
			    for (KillStat ks : killStats) {
			        if (ks.monNo == prevMonNo) {          // KillStat의 필드명에 맞게 조정
			            killsOnPrev = ks.killCount;      // getKillCount() 쓰는 구조면 그걸로
			            break;
			        }
			    }
			}

			// 3) 조건 미달 시 거부
			if(!master) {
				if (killsOnPrev < 5 ) {
				    Monster prev = getMonsterCached(prevMonNo);
				    String prevName = (prev == null ? ("Lv " + prevMonNo) : prev.monName);
				    return "상위 등급으로 올리려면 [" + prevName + "]을(를) 최소 5마리 처치해야 합니다. (현재 "
				         + killsOnPrev + "마리)";
				}
			}
		}
		*/
		
		botNewService.closeOngoingBattleTx(userName, roomName);
		botNewService.updateUserTargetMonTx(userName, roomName, m.monNo);
		int userLvForView = (u != null ? u.lv : 1);
		return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다." + NL
		     + "▶ 선택: " + NL + renderMonsterSelectLine(m, nightmareYnVal);
	}
	// 엔트리 포인트: 기존 /구매 명령이 들어오는 곳
	public String buyItem(HashMap<String, Object> map) {
	    final String roomName = Objects.toString(map.get("roomName"), "");
	    final String userName = Objects.toString(map.get("userName"), "");
	    final String rawParam = Objects.toString(map.get("param1"), "").trim();

	    if (roomName.isEmpty() || userName.isEmpty()) {
	        return "방/유저 정보가 누락되었습니다.";
	    }


	    // 파라미터 없으면: 구매 가능 목록 노출 (기존 로직 유지)
	    if (rawParam.isEmpty() || "전체".equals(rawParam)) {
	    	return buildCustomMarketAllMessage(userName, roomName);
	    }
	    
	    // 2) /구매 신규  (또는 /구매 000 같이 쓰고 싶으면 OR 유지)
	    if ("신규".equals(rawParam) || "000".equals(rawParam)) {
	        // 전체 목록 조회 (캐시 사용)
	        List<HashMap<String,Object>> list = getMarketItemsWithOwnedCached(userName, roomName);
	        if (list == null || list.isEmpty()) {
	            return "신규 등록 아이템이 없습니다.";
	        }

	        // INSERT_DATE 기준으로 내림차순 정렬 (최근 등록 순)
	        list.sort(new Comparator<HashMap<String,Object>>() {
	            @Override
	            public int compare(HashMap<String,Object> o1, HashMap<String,Object> o2) {
	                java.sql.Timestamp t1 = toTimestamp(o1.get("INSERT_DATE"));
	                java.sql.Timestamp t2 = toTimestamp(o2.get("INSERT_DATE"));
	                // null 안전 처리: null 은 가장 오래된 것으로 취급
	                if (t1 == null && t2 == null) return 0;
	                if (t1 == null) return 1;
	                if (t2 == null) return -1;
	                // 최근 것이 앞으로 오도록 내림차순
	                return t2.compareTo(t1);
	            }
	        });

	        // 상위 10개만 사용
	        int limit = Math.min(10, list.size());
	        List<HashMap<String,Object>> newestList = new ArrayList<>(list.subList(0, limit));

	        String compact = renderMarketListForBuy(newestList, userName, false);
	        return "▶ 신규 등록 아이템 목록" + NL + compact;
	    }
	    
	    
	 // ➊ 카테고리 목록 모드 체크
	    int[] range = resolveCategoryRange(rawParam);  // ex) "무기" → [100, 200]
	    if (range != null) {
	        int min = range[0];
	        int max = range[1];

	        // 캐시에서 아이템 목록 가져온 뒤 ID 범위로 필터
	        List<HashMap<String,Object>> list = getMarketItemsWithOwnedCached(userName, roomName);

	        List<HashMap<String,Object>> filtered = new ArrayList<>();
	        for (HashMap<String,Object> row : list) {
	            int id = MiniGameUtil.parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));
	            if (id >= min && id < max) {
	                filtered.add(row);
	            }
	        }

	        return "▶ " + rawParam + " 카테고리 목록" + NL
	             + renderMarketListForBuy(filtered, userName, false);
	    }

	    // 문의방 제한 (기존 로직 유지)
	    if (roomName.equals("람쥐봇 문의방")) {
	        if (!userName.equals("일어난다람쥐/카단")) {
	            return "문의방에서는 불가능합니다.";
	        }
	    }

	    // 멀티 구매: 콤마 포함 시
	    if (!botNewService.tryAcquireUserActionLock(userName)) {
	        return "⏳ 처리 중입니다. 잠시 후 다시 시도해 주세요.";
	    }
	    try {
	        if (rawParam.contains(",")) {
	            return buyMultiItems(roomName, userName, rawParam);
	        }
	        return buySingleItem(roomName, userName, rawParam);
	    } finally {
	        botNewService.releaseUserActionLock(userName);
	    }
	}

	
	// 콤마 기반 멀티 구매 + x / * 수량 지원
	// 예) "101,102,백화검*3,200x2"
	// 동일 아이템이 여러 번 등장하면 합산 후 배치 처리 (SQL 중복 방지)
	private String buyMultiItems(String roomName, String userName, String raw) {
	    String[] tokens = raw.split(",");

	    // 1) 토큰 파싱 & 동일 아이템 합산 (LinkedHashMap으로 입력 순서 유지)
	    java.util.LinkedHashMap<String, Integer> itemQtyMap = new java.util.LinkedHashMap<>();
	    for (String t : tokens) {
	        String token = (t == null ? "" : t.trim());
	        if (token.isEmpty()) continue;

	        int qty = 1;
	        String itemToken = token;
	        java.util.regex.Matcher m =
	            java.util.regex.Pattern.compile("(.+?)[xX\\*](\\d+)$").matcher(token);
	        if (m.matches()) {
	            itemToken = m.group(1).trim();
	            qty = MiniGameUtil.parseIntSafe(m.group(2));
	            if (qty <= 0) qty = 1;
	        }
	        itemQtyMap.merge(itemToken, qty, Integer::sum);
	    }

	    if (itemQtyMap.isEmpty()) return "구매할 대상이 없습니다.";

	    StringBuilder sb = new StringBuilder("▶ 일괄 구매 결과").append(NL);

	    // ThreadLocal 설정: 각 buySingleItem/buyBatch 가 insertPointRank 를 직접 호출하지 않고
	    // 여기서 한 번만 합산하여 PK 충돌 방지
	    MULTI_BUY_COST_TL.set(new SP[]{new SP(0, "")});
	    try {
	        // 2) 아이템별 처리: qty=1이면 기존 단일 구매, qty>1이면 배치 구매
	        for (java.util.Map.Entry<String, Integer> entry : itemQtyMap.entrySet()) {
	            String itemToken = entry.getKey();
	            int qty = entry.getValue();
	            String label = resolveItemLabel(itemToken);

	            sb.append(NL).append("[").append(label);
	            if (qty > 1) sb.append(" ×").append(qty);
	            sb.append("]").append(NL);

	            if (qty == 1) {
	                sb.append(buySingleItem(roomName, userName, itemToken)).append(NL);
	            } else {
	                sb.append(buyBatch(roomName, userName, itemToken, qty)).append(NL);
	            }
	        }

	        // 합산 비용 한 번에 차감
	        SP totalCost = MULTI_BUY_COST_TL.get()[0];
	        if (totalCost.getValue() > 0 || totalCost.getUnit().length() > 0) {
	            HashMap<String, Object> pr = new HashMap<>();
	            pr.put("userName", userName);
	            pr.put("roomName", roomName);
	            pr.put("score",    -totalCost.getValue());
	            pr.put("scoreExt", totalCost.getUnit());
	            pr.put("cmd",      "BUY");
	            botNewService.insertPointRank(pr);
	        }
	    } finally {
	        MULTI_BUY_COST_TL.remove();
	    }

	    return sb.toString();
	}

	/**
	 * 동일 아이템 qty개 일괄 구매.
	 * calcUserBattleContext, selectCurrentPoint, selectAchvCountsGlobal 등 무거운 SQL을 1회만 실행.
	 * - POTION : qty번 사용 효과 누적 후 HP/SP를 한 번에 반영
	 * - MARKET : 1개 초과 구매 불가 → 단일 구매로 위임
	 */
	private String buyBatch(String roomName, String userName, String itemToken, int qty) {
	    // ── 아이템 ID 해석 ──────────────────────────────────────────────
	    Integer itemId = null;
	    if (itemToken != null && itemToken.matches("\\d+")) {
	        try { itemId = Integer.valueOf(itemToken); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try { itemId = getItemIdCached(itemToken); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + itemToken;
	    }

	    // ── 아이템 상세 (캐시) ─────────────────────────────────────────
	    HashMap<String, Object> item = getItemDetailCached(itemId);
	    String itemType = (item == null) ? "" : Objects.toString(item.get("ITEM_TYPE"), "");
	    String itemName = (item == null) ? String.valueOf(itemId) : Objects.toString(item.get("ITEM_NAME"), String.valueOf(itemId));

	    // MARKET 아이템은 1개 초과 구매 불가 → 단건으로 위임
	    if ("MARKET".equalsIgnoreCase(itemType) || "MARKET2".equalsIgnoreCase(itemType)) {
	        return buySingleItem(roomName, userName, itemToken);
	    }

	    // POTION 외 미지원 타입
	    if (!"POTION".equalsIgnoreCase(itemType)) {
	        return "구매할 수 없는 아이템입니다. (MARKET/POTION 유형만 구매 가능)";
	    }

	    // 물약 한 번에 최대 10개 제한
	    if (qty > 10) {
	        return "물약은 한 번에 최대 10개까지만 사용 가능합니다. (요청: " + qty + "개)";
	    }

	    // ── 공통 조회 1회 ────────────────────────────────────────────────
	    // UserBattleContext (유저 정보, 장비, HP 등) - 가장 무거운 SQL, 1회만
	    HashMap<String, Object> ctxMap = new HashMap<>();
	    ctxMap.put("userName", userName);
	    ctxMap.put("roomName", roomName);
	    UserBattleContext ctx = calcUserBattleContext(ctxMap);

	    // 현재 포인트 1회 조회
	    HashMap<String, Object> pointRow = botNewService.selectCurrentPoint(userName, roomName);
	    SP userPoint = new SP(
	        Double.parseDouble(Objects.toString(pointRow.get("SCORE"), "0")),
	        Objects.toString(pointRow.get("SCORE_EXT"), "")
	    );

	    // 단가 계산 (lifetimeSp 기반, 배치 중 불변)
	    SP unitPrice = MiniGameUtil.getPotionPrice(itemId, ctx.lifetimeSp);
	    SP totalCost = unitPrice.multiply(qty);

	    int hpCur = ctx.hpCur;
	    if(hpCur >= ctx.hpMax ) {
	    	return "최대체력에선 포션구매불가!";
	    }
	    // 데스 상태 체크 1회
	    boolean isDead = isDeadState(userName);
	    if (itemId == 1001) {
	        if (!isDead) return "이그드라실의씨앗은 플레이어 데스 상태에서만 구매할 수 있습니다.";
	    } else {
	        if (isDead) return "플레이어 데스 상태에서는 해당 포션을 사용할 수 없습니다.";
	    }

	    // 전체 비용 감당 가능 여부 체크
	    if (!userPoint.canAfford(totalCost)) {
	        // 몇 개까지 살 수 있는지 계산
	        int affordable = 0;
	        SP acc = new SP(0, unitPrice.getUnit());
	        for (int i = 0; i < qty; i++) {
	            acc = acc.add(unitPrice);
	            if (userPoint.canAfford(acc)) affordable++;
	            else break;
	        }
	        return userName + "님, 포인트가 부족합니다."
	            + " (필요: " + totalCost + "sp, 보유: " + userPoint + ")"
	            + (affordable > 0 ? "\n※ " + affordable + "개는 구매 가능합니다." : "");
	    }

	    // ── 배치 처리 (HP 계산 + 인벤토리 로그 삽입만 qty회) ───────────
	    long currentHp = ctx.user.hpCur;
	    StringBuilder healSb = new StringBuilder();

	    for (int i = 0; i < qty; i++) {
	        long heal = MiniGameUtil.getPotionHeal(itemId, ctx.hpMax);
	        long newHp = Math.min(currentHp + heal, ctx.hpMax);

	        if (i > 0) healSb.append(NL);
	        healSb.append("#").append(i + 1).append(" ")
	              .append(currentHp).append(" → ").append(newHp)
	              .append(" (+").append(newHp - currentHp).append(")");

	        currentHp = newHp;

	        
	    }
	    
	 // 인벤토리 로그 삽입 (경량 INSERT, qty회 실행)
        HashMap<String, Object> inv = new HashMap<>();
        inv.put("userName", userName);
        inv.put("roomName", roomName);
        inv.put("itemId",   itemId);
        inv.put("qty",      qty);
        inv.put("delYn",    "1");
        inv.put("gainType", "BUY");
        botNewService.insertInventoryLogTx(inv);
        invalidateInvBuff(userName); // 아이템 판매(BUY del_yn=1)

	    // ── 최종 HP 반영 1회 ────────────────────────────────────────────
	    botNewService.updateUserHpOnlyTx(userName, "", (int) currentHp);

	    // ── SP 차감 1회 (다중구매 모드이면 ThreadLocal 에 누적, 단건이면 즉시 처리) ────
	    SP[] buyTl = MULTI_BUY_COST_TL.get();
	    if (buyTl != null) {
	        buyTl[0] = buyTl[0].add(totalCost);
	    } else {
	        HashMap<String, Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score",    -totalCost.getValue());
	        pr.put("scoreExt", totalCost.getUnit());
	        pr.put("cmd",      "BUY");
	        botNewService.insertPointRank(pr);
	    }

	    // ── 물약 사용 업적 체크 1회 ─────────────────────────────────────
	    String achvMsg = "";
	    try {
	        int newPotionCnt = MiniGameUtil.POTION_USE_CACHE.compute(
	                userName, (k, v) -> (v == null ? qty : v + qty));
	        List<AchievementCount> achvList = botNewService.selectAchvCountsGlobal(userName, roomName);
	        Set<String> achvSet = new HashSet<>();
	        if (achvList != null) for (AchievementCount ac : achvList) achvSet.add(ac.getCmd());
	        String msg = grantPotionUseAchievements(userName, roomName, achvSet, newPotionCnt);
	        if (msg != null && !msg.isEmpty()) achvMsg = NL + msg;
	    } catch (Exception ignore) {}

	    // ── 잔여 포인트 조회 1회 ────────────────────────────────────────
	    SP afterPoint = userPoint.subtract(totalCost);
	    try {
	        HashMap<String, Object> afterRow = botNewService.selectCurrentPoint(userName, roomName);
	        afterPoint = new SP(
	            Double.parseDouble(Objects.toString(afterRow.get("SCORE"), "0")),
	            Objects.toString(afterRow.get("SCORE_EXT"), "")
	        );
	    } catch (Exception ignore) {}

	    return "▶ 포션 일괄 사용 (×" + qty + ")" + NL
	         + userName + "님이 " + itemName + "을(를) " + qty + "개 사용했습니다." + NL
	         + "↘단가: " + unitPrice + "sp  합계: " + totalCost + "sp" + NL
	         + healSb + NL
	         + "HP: " + ctx.user.hpCur + " → " + currentHp + " / " + ctx.hpMax + NL
	         + "✨포인트: " + afterPoint
	         + achvMsg;
	}

	private String buySingleItem(String roomName, String userName, String raw) {
		String potionMsg = null;
	    // 입력 → itemId 해석
	    Integer itemId = null;
	    if (raw != null && raw.matches("\\d+")) {
	        try { itemId = Integer.valueOf(raw); } catch (Exception ignore) {}
	    }
	    if (itemId == null) {
	        try { itemId = getItemIdCached(raw); } catch (Exception ignore) {}
	    }
	    
	    if (itemId == null) {
	        return "해당 아이템을 찾을 수 없습니다: " + raw + NL
	             + "(/구매 입력만으로 목록을 확인하세요)";
	    }

	    // 이미 소유 여부 체크 (실시간 조회)
	    boolean alreadyOwnedThisItem = false;
	    try {
	        List<HashMap<String,Object>> inv = botNewService.selectInventorySummaryAll(userName, roomName);
	        if (inv != null) {
	            for (HashMap<String,Object> row : inv) {
	                if (row == null) continue;

	                int rowItemId = MiniGameUtil.parseIntSafe(Objects.toString(row.get("ITEM_ID"), "0"));
	                if (rowItemId != itemId) continue;

	                int q = MiniGameUtil.parseIntSafe(Objects.toString(row.get("TOTAL_QTY"), "0"));
	                if (q > 0) {
	                    alreadyOwnedThisItem = true;  // 이미 이 아이템은 가지고 있음 → 업그레이드 구매
	                }
	                break;
	            }
	        }
	    } catch (Exception ignore) {}

	    // 장비 카테고리 수량 제한 체크 (새 장비일 때만)
	    if (!alreadyOwnedThisItem) {
	        String limitMsg = checkEquipCategoryLimit(userName, roomName, itemId, 1);
	        if (limitMsg != null) {
	            return limitMsg;
	        }
	    }

	    // 아이템 상세 조회
	    HashMap<String, Object> item = getItemDetailCached(itemId);
	    String itemType = (item == null) ? "" : Objects.toString(item.get("ITEM_TYPE"), "");

	    boolean isBagShop = MiniGameUtil.isBagShopItem(itemId);
	    if (item == null || !("POTION".equalsIgnoreCase(itemType) || "MARKET".equalsIgnoreCase(itemType) || "MARKET2".equalsIgnoreCase(itemType) || isBagShop)) {
	        return "구매할 수 없는 아이템입니다. (MARKET 유형만 구매 가능)";
	    }

	    String itemName = Objects.toString(item.get("ITEM_NAME"), String.valueOf(itemId));

	    // 레벨 제한 체크
	    int targetLv = MiniGameUtil.parseIntSafe(Objects.toString(item.get("TARGET_LV"), "0"));
	    if (targetLv > 0) {
	        try {
	            User u = botNewService.selectUser(userName, roomName);
	            int userLv = (u == null) ? 0 : u.lv;
	            if (userLv < targetLv) {
	                return "⚠ [" + itemName + "] 구매 불가 — Lv." + targetLv + " 이상 필요 (현재 Lv." + userLv + ")";
	            }
	        } catch (Exception ignore) {}
	    }

	    // 단가
	    HashMap<String,Object> priceRow = getItemPriceCached(itemId);
	    double priceValue = safeDouble(priceRow == null ? null : priceRow.get("ITEM_SELL_PRICE"));
	    String priceExt = Objects.toString(priceRow == null ? null : priceRow.get("ITEM_SELL_PRICE_EXT"), "");
	    
	    SP itemPrice = new SP(priceValue, priceExt);
	    

	    // 포인트 확인
	    
	    HashMap<String,Object> pointRow =
	            botNewService.selectCurrentPoint(userName, roomName);

	    double curValue = Double.parseDouble(
	        Objects.toString(pointRow.get("SCORE"), "0")
	    );

	    String curExt = Objects.toString(pointRow.get("SCORE_EXT"), "");

	    SP userPoint = new SP(curValue, curExt);
	    
	    if (!userPoint.canAfford(itemPrice)) {
	    	return userName + "님, [" + itemName + "] 구매에 필요한 포인트가 부족합니다."
		             + " (가격: " + itemPrice + "sp, 보유: " + userPoint + ")";
	    }
	    
	    
	    int buyQty = 1; // 현재 /구매는 1개씩 구매
	    int itemIdInt = itemId; // 위에서 구한 itemId 그대로 사용

	    if ("MARKET".equalsIgnoreCase(itemType)||"MARKET2".equalsIgnoreCase(itemType)) {
	        // 장비: 같은 ITEM_ID 가진 행이 있으면 QTY만 증가
	        List<HashMap<String, Object>> rows =
	                botNewService.selectInventoryRowsForSale(userName, roomName, itemId);

	        int currentQty = 0;

	        if (rows != null) {
	            for (HashMap<String, Object> row : rows) {
	                if (row == null) continue;

	                String delYn = Objects.toString(row.get("DEL_YN"), "0");
	                if (!"0".equals(delYn)) continue; // 삭제된 건 스킵

	                String rid = (row.get("RID") != null ? row.get("RID").toString() : null);
	                if (rid == null) continue;

	                int q = MiniGameUtil.parseIntSafe(Objects.toString(row.get("QTY"), "0"));
	                if (q <= 0) continue;

	                // 같은 ITEM_ID 한 줄만 관리한다고 가정 → 첫 행 사용
	                currentQty = q;
	                break;
	            }
	        }

            if (currentQty > 0) {
                return "⚠ 이미 보유중인 아이템입니다. [" + itemName + "] 은(는) 1개만 보유 가능합니다.";
            }

            // 최초 구매만 허용 (QTY=1)
            HashMap<String, Object> inv = new HashMap<>();
            inv.put("userName", userName);
            inv.put("roomName", roomName);
            inv.put("itemId",  itemIdInt);
            inv.put("qty",     buyQty);
            inv.put("delYn",   "0");
            inv.put("gainType","BUY");
            botNewService.insertInventoryLogTx(inv);
            invalidateInvBuff(userName); // 마켓 아이템 구매

	    }else if ("POTION".equalsIgnoreCase(itemType)) {
	    	HashMap<String,Object> map = new HashMap<>();
	        map.put("userName", userName);
	        map.put("roomName", roomName);

	        UserBattleContext ctx = calcUserBattleContext(map);

	        //int userLv = ctx.user.lv;

	        // 가격 계산
	        itemPrice = MiniGameUtil.getPotionPrice(itemId, ctx.lifetimeSp);

	        // 포인트 확인
	        if (!userPoint.canAfford(itemPrice)) {
	            return userName + "님, 포인트가 부족합니다. (가격: " + itemPrice + ")";
	        }

	        int hpCur = ctx.hpCur;
		    if(hpCur >= ctx.hpMax ) {
		    	return "최대체력에선 포션구매불가!";
		    }
	        boolean isDead = isDeadState(userName);

	        if (itemId == 1001) {
	            if (!isDead) {
	                return "이그드라실의씨앗은 플레이어 데스 상태에서만 구매할 수 있습니다.";
	            }
	        } else {
	            if (isDead) {
	                return "플레이어 데스 상태에서는 해당 포션을 사용할 수 없습니다.";
	            }
	        }

	        // 인벤토리 기록
	        HashMap<String, Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId", itemIdInt);
	        inv.put("qty", 1);
	        inv.put("delYn", "1");
	        inv.put("gainType", "BUY");

	        botNewService.insertInventoryLogTx(inv);
	        // 포션 사용
	        potionMsg = usePotion(ctx, userName, roomName, itemId);

	        // 물약 사용 횟수 캐시 갱신 + 업적 즉시 체크
	        try {
	            int newPotionCnt = MiniGameUtil.POTION_USE_CACHE.compute(
	                    userName, (k, v) -> (v == null ? 1 : v + 1));
	            List<AchievementCount> potionAchvList = botNewService.selectAchvCountsGlobal(userName, roomName);
	            Set<String> potionAchvSet = new HashSet<>();
	            if (potionAchvList != null) {
	                for (AchievementCount ac : potionAchvList) potionAchvSet.add(ac.getCmd());
	            }
	            String potionAchvMsg = grantPotionUseAchievements(userName, roomName, potionAchvSet, newPotionCnt);
	            if (potionAchvMsg != null && !potionAchvMsg.isEmpty()) {
	                potionMsg = potionMsg + NL + potionAchvMsg;
	            }
	        } catch (Exception ignore) {}
	    } else if (isBagShop) {
	        // 가방 상점: 동적 가격 (각 가방 최대드랍치 × 4)
	        long top1SpRaw = getTop1SpCached();
	        itemPrice = MiniGameUtil.getBagPrice(itemId, top1SpRaw);

	        // 포인트 재확인 (동적 가격 기준)
	        if (!userPoint.canAfford(itemPrice)) {
	            return userName + "님, 포인트가 부족합니다. (가격: " + itemPrice + "sp, 보유: " + userPoint + "sp)";
	        }

	        // 인벤토리에 가방 추가
	        HashMap<String, Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId",   itemId);
	        inv.put("qty",      1);
	        inv.put("delYn",    "0");
	        inv.put("gainType", "BUY");
	        botNewService.insertInventoryLogTx(inv);
	        invalidateInvBuff(userName);
	    }

	    // 결제 (포인트 차감 — 다중구매 모드이면 ThreadLocal 에 누적, 단건이면 즉시 처리)
	    SP[] singleBuyTl = MULTI_BUY_COST_TL.get();
	    if (singleBuyTl != null) {
	        singleBuyTl[0] = singleBuyTl[0].add(itemPrice);
	    } else {
	        HashMap<String, Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score", -itemPrice.getValue());
	        pr.put("scoreExt", itemPrice.getUnit());
	        pr.put("cmd", "BUY");
	        botNewService.insertPointRank(pr);
	    }

	    // 구매 후 포인트
	    SP afterUserPoint=null;
	    try { 
	    HashMap<String,Object> tmpAfterRow =
	            botNewService.selectCurrentPoint(userName, roomName);

	    double afterCurValue = Double.parseDouble(
	        Objects.toString(tmpAfterRow.get("SCORE"), "0")
	    );

	    String afterCurExt = Objects.toString(tmpAfterRow.get("SCORE_EXT"), "");

	    afterUserPoint = new SP(afterCurValue, afterCurExt);
	    
	    
	    } catch (Exception ignore) {}

	    // 표시용 이름
	    String shownName = itemName;
	    // 옵션 문자열 결정
	    String optionStr;
	    
	    optionStr = MiniGameUtil.buildEnhancedOptionLine(item, 1); 
	    //buildOptionTokensFromMap(item);

	    // 결과 메시지
	    StringBuilder sb = new StringBuilder();
	    if(MiniGameUtil.isInstantUseItem(itemId)) {
	    	sb.append("▶ 포션 사용").append(NL)
	        .append(userName).append("님이 ").append(shownName).append("을(를) 사용했습니다.").append(NL)
	        .append("↘가격: ").append(itemPrice.toString()).append("sp").append(NL)
	        .append(potionMsg).append(NL)
	        .append("✨포인트: ").append(afterUserPoint.toString());
	    	
	    }else {
	    	sb.append("▶ 구매 완료").append(NL)
		      .append(userName).append("님, ").append(shownName).append("을(를) 구매했습니다.").append(NL)
		      .append("↘가격: ").append(itemPrice.toString()).append("").append(NL)
		      .append("↘옵션: ").append(optionStr).append(NL)
		      .append("✨포인트: ").append(afterUserPoint.toString()).append("");

	    	// ── 세트 효과 달성 알림 ───────────────────────────────────────────────
	    	String setId = Objects.toString(item.get("SET_ID"), "");
	    	if (!setId.isEmpty()) {
	    	    try {
	    	        List<HashMap<String,Object>> activeBonus = botNewService.selectActiveSetBonuses(userName);
	    	        for (HashMap<String,Object> b : activeBonus) {
	    	            if (!setId.equals(Objects.toString(b.get("SET_ID"), ""))) continue;
	    	            int ownedCnt    = b.get("OWNED_CNT")    != null ? ((Number) b.get("OWNED_CNT")).intValue()    : 0;
	    	            int requiredCnt = b.get("REQUIRED_CNT") != null ? ((Number) b.get("REQUIRED_CNT")).intValue() : 0;
	    	            // 보유 수 == 발동 조건 = 이번 구매로 새로 달성
	    	            if (ownedCnt == requiredCnt) {
	    	                sb.append(NL).append("※ [").append(setId).append("] ").append(requiredCnt).append("세트 효과 달성!");
	    	                String desc = Objects.toString(b.get("BONUS_DESC"), "");
	    	                if (!desc.isEmpty()) sb.append(NL).append("  ▸ ").append(desc);
	    	            }
	    	        }
	    	    } catch (Exception ignore) {}
	    	}

	    	try {
	    		botNewService.closeOngoingBattleTx(userName, roomName);
	    	} catch(Exception e) {
	    		// 무시
	    	}
	    }

	    return sb.toString();
	}
	
	/*
	private String useRevivePotion(String userName,String roomName){
		HashMap<String,Object> map = new HashMap<>();
		map.put("userName", userName);
		UserBattleContext ctx = calcUserBattleContext(map);
		User u = ctx.user;
	    //int hpCur = u.hpCur;
	    int hpMax = ctx.hpMax;

	    if(!canRevive(userName)){
	        return "이미 부활상태입니다.";
	    }

	    int reviveHp = (int)Math.ceil(hpMax * 0.1);

	    botNewService.updateUserHpOnlyTx(userName, null, reviveHp);

	    botNewService.insertBattleLogTx(new BattleLog()
                .setUserName(userName)
                .setRoomName(roomName)
                .setLv(u.lv)
                .setTargetMonLv(0)
                .setGainExp(-1)  // 물약로그 구분용 (쿨타임/부활시간 계산 제외)
                .setAtkDmg(0)
                .setMonDmg(0)
                .setAtkCritYn(0)
                .setMonPatten(0)
                .setKillYn(0)
                .setNowYn(0)
                .setDropYn(0)
                .setDeathYn(0)
                .setLuckyYn(0)
                .setJobSkillYn(0)
                .setJob(u.job)
                .setNightmareYn(0)
        );
	    return "부활의 성수를 사용했습니다. (체력 10% 부활)"; 
	}
	
	private boolean canRevive(String userName){

	    Timestamp baseline = botNewService.selectLastDamagedTime(userName, "");

	    if(baseline == null){
	        return false;
	    }

	    long diff = System.currentTimeMillis() - baseline.getTime();

	    return diff <= (5 * 60 * 1000);
	}
	*/
	private String usePotion(UserBattleContext ctx,String userName,String roomName, int itemId){
		User u = ctx.user;
		
		
		
		
	    long heal = MiniGameUtil.getPotionHeal(itemId, ctx.hpMax);
	    long newHp = u.hpCur + heal;
	    if(newHp > ctx.hpMax){
	        newHp = ctx.hpMax;
	    }
	    
	    botNewService.updateUserHpOnlyTx(userName, "", (int)newHp);
	    if(itemId == 1001) {
	    	botNewService.insertBattleLogTx(new BattleLog()
	                .setUserName(userName)
	                .setRoomName(roomName)
	                .setLv(u.lv)
	                .setTargetMonLv(0)
	                .setGainExp(0)
	                .setAtkDmg(0)
	                .setMonDmg(0)
	                .setAtkCritYn(0)
	                .setMonPatten(0)
	                .setKillYn(0)
	                .setNowYn(0)
	                .setDropYn(0)
	                .setDeathYn(0)
	                .setLuckyYn(0)
	                .setJobSkillYn(0)
	                .setJob(u.job)
	                .setNightmareYn(0)
	        );
	    	 return userName+"님, 부활했습니다. (+" + heal + ")"+NL
		    		 +u.hpCur +" → "+newHp+" / "+ctx.hpMax;
	    }else {
	    	 return userName+"님, 체력이 회복되었습니다. (+" + heal + ")"+NL
		    		 +u.hpCur +" → "+newHp +" / "+ ctx.hpMax;
	    }
	}
	
	public boolean isDeadState(String userName){

	    HashMap<String,Object> map = new HashMap<>();
	    map.put("userName", userName);

	    HashMap<String,Object> row = null;

	    try{
	    	//5분내 죽었엇는지, 부활기록이 있는지 최근로그가져옴
	        row = botNewService.selectLastBattleLog(map);
	    }catch(Exception e){
	        return false;//에러시 살아있는상태 (보통 NULL)
	    }

	    if(row == null){
	        return false;
	    }

	    int targetMonLv = safeInt(row.get("TARGET_MON_LV"));
	    String deathYn  = Objects.toString(row.get("DEATH_YN"), "0");

	    // 부활 포션 사용
	    if(targetMonLv == 0){
	        return false;
	    }

	    // 사망 상태
	    if("1".equals(deathYn)){
	        return true;
	    }

	    return false;
	}
	
	private void applyDropBonusToContext(
	        UserBattleContext ctx,
	        String userName,
	        String roomName
	) {

	    // [OPT-HUNTER] attackInfo에서 미리 조회한 dropRows 재사용 (중복 DB 조회 방지)
	    List<HashMap<String,Object>> drops = (ctx.preDropRows != null)
	            ? ctx.preDropRows
	            : botNewService.selectTotalDropItems(userName);

	    if (drops == null || drops.isEmpty()) return;

	    int bonusMinAtk     = 0;
	    int bonusMaxAtk     = 0;
	    int bonusHp      = 0;
	    int bonusRegen   = 0;
	    int bonusCrit    = 0;
	    int bonusCritDmg = 0;

	    for (HashMap<String,Object> row : drops) {

	        String name     = Objects.toString(row.get("ITEM_NAME"), "");
	        String gainType = Objects.toString(row.get("GAIN_TYPE"), "");
	        int itemId = safeInt(row.get("ITEM_ID"));
	        int qty         = safeInt(row.get("TOTAL_QTY"));

	        if (qty <= 0 || name.isEmpty()) continue;

	        // 👉 어둠 아이템만 적용 (원하면 조건 제거 가능)
	        if (!"DROP5".equals(gainType)) continue;
	        
	        switch(itemId) {
	        case 1: 
	        	bonusCritDmg += qty /10;
	        	break;
	        case 15: case 30:
	        	bonusCritDmg += qty /5;
	        	break;
	        case 25:  
	        	bonusCritDmg += qty ;
	        	break;
	        	
	        case 27:
	        	bonusRegen+=qty/2;
	        	break;
	        case 20:  
	        	bonusRegen+=qty/5;
	        	break;
	        case 12:  case 7: case 8:  
	        	bonusRegen+=qty/10;
	        	break;
	        	
	        case 23: case 28:
	        	bonusCrit+=qty/5;
	        	break;
	        case 17: case 9: case 11: case 19:  
	        	bonusCrit+=qty/10;
	        	break;
	        	
	        case 24: 
	        	bonusMinAtk+=qty/2;
	        	break;
	        case 2: case 3: case 5: case 16:  
	        	bonusMinAtk+=qty/10;
	        	break;
	        	
	        case 26: case 29:
	        	bonusMaxAtk+=qty/2;
	        	break;
	        case 13: case 4: case 6: case 14: 
	        	bonusMaxAtk+=qty/10;
	        	break;
	        	
	        case 10: case 18: case 21: case 22: 
	        	bonusHp += qty/2;
	        	break;
	        }
	        
	        
	        //상한초과방지
	        bonusCrit = Math.min(bonusCrit, 100);
	        bonusCritDmg = Math.min(bonusCritDmg, 200);
	        bonusMinAtk = Math.min(bonusMinAtk, 150);
	        bonusMaxAtk = Math.min(bonusMaxAtk, 200);
	    }

	    // ctx 에 바로 반영
	    ctx.atkMin += bonusMinAtk;
	    ctx.atkMax += bonusMaxAtk;

	    ctx.hpMax     += bonusHp;
	    ctx.regen         += bonusRegen;

	    ctx.crit          += bonusCrit;
	    ctx.critDmg       += bonusCritDmg;

	    // 표시용 (선택)
	    ctx.dropAtkMin     = bonusMinAtk;
	    ctx.dropAtkMax     = bonusMaxAtk;
	    ctx.dropHp      = bonusHp;
	    ctx.dropRegen   = bonusRegen;
	    ctx.dropCrit    = bonusCrit;
	    ctx.dropCritDmg = bonusCritDmg;
	}

	
	
	private String buildCustomMarketAllMessage(String userName, String roomName) {


	    StringBuilder sb = new StringBuilder();
	      sb.append("■ 람쥐봇 게임즈 시즌2 상점").append(NL)
		    .append("────────────────").append(NL)
		    .append("■ 구매 방법").append(NL)
		    .append(" - /구매 [아이템ID]").append(NL)
		    .append(" - /구매 [카테고리]").append(NL)
		    .append("■ 다중 구매").append(NL)
	        .append(" - /구매 101,102,401").append(NL)
	        .append(" - /구매 목검,도씨검").append(NL)
	        .append(NL)
		    .append("■ 카테고리 바로가기").append(NL)
		    .append(" - /구매 [전체, 신규 , 무기 , 투구 , 갑옷 , 반지 , 토템 , 행운 , 전설 , 날개 , 선물 , 물약]").append(NL)
		    .append(NL)
		    .append("■ 카테고리 범위").append(NL);
	      
	      
	      for (EquipCategory c : MiniGameUtil.EQUIP_CATEGORIES) {

	    	  sb.append(" - ");

	    	    for (int i = 0; i < c.ranges.length; i++) {

	    	        int[] r = c.ranges[i];

	    	        sb.append(r[0]);   // 시작값만 출력

	    	        if (i < c.ranges.length - 1) {
	    	            sb.append(",");
	    	        }
	    	    }

	    	    sb.append(" : ").append(c.name).append(NL);
	      }
	      /*
		    .append(" - 000 : 신규 아이템").append(NL)
		    .append(" - 100/1100/2100 : 무기").append(NL)
		    .append(" - 200 : 투구").append(NL)
		    .append(" - 300 : 갑옷").append(NL)
		    .append(" - 400 : 반지").append(NL)
		    .append(" - 500 : 토템").append(NL)
		    .append(" - 600 : 행운").append(NL)
		    .append(" - 700 : 전설").append(NL)
		    .append(" - 800 : 날개").append(NL)
		    .append(" - 900 : 선물").append(NL)
		    .append(" - 1000 : 물약").append(NL).append(NL);
		    */
	    return sb.toString();
	}
	// 멀티 구매 출력용: "101" → "목검" 같은 ITEM_NAME으로 바꿔줌
	private String resolveItemLabel(String itemToken) {
	    if (itemToken == null || itemToken.trim().isEmpty()) {
	        return "";
	    }

	    String token = itemToken.trim();
	    Integer itemId = null;

	    // 1) 숫자면 ID로 시도
	    if (token.matches("\\d+")) {
	        try { itemId = Integer.valueOf(token); } catch (Exception ignore) {}
	    }

	    // 2) 이름으로 시도
	    if (itemId == null) {
	        try { itemId = getItemIdCached(token); } catch (Exception ignore) {}
	    }

	    // 3) 코드로 시도
	    if (itemId == null) {
	        try { itemId = botNewService.selectItemIdByCode(token); } catch (Exception ignore) {}
	    }

	    if (itemId == null) {
	        // 끝까지 못 찾으면 그냥 원래 토큰 리턴
	        return token;
	    }

	    // 4) ITEM_NAME 조회
	    try {
	        HashMap<String, Object> item = botNewService.selectItemDetailById(itemId);
	        if (item != null) {
	            String itemName = Objects.toString(item.get("ITEM_NAME"), "");
	            if (!itemName.isEmpty()) {
	                return itemName;
	            }
	        }
	    } catch (Exception ignore) {}

	    // 조회 실패 시 토큰 그대로
	    return token;
	}
	
	
	public String monsterAttack(HashMap<String, Object> map) {
		map.put("cmd", "monster_attack");
		AttackSession s = new AttackSession(map);
		String earlyMsg;

		// 0~1) 입력 검증 / 매크로 잠금
		if ((earlyMsg = ma_validate(s)) != null) return earlyMsg;

		// 2~4) 공통 스탯 + 직업 공격배율
		if ((earlyMsg = ma_calcStats(s)) != null) return earlyMsg;

		// 5~6) 부활 처리 / 진행중·신규 몬스터 설정
		if ((earlyMsg = ma_resolveMonster(s)) != null) return earlyMsg;

		// 7) 쿨타임·8) HP 확정 / [S3] 헰보스 분기
		if ((earlyMsg = ma_cooldownAndHp(s)) != null) return earlyMsg;

		// 8-후) berserkMul + Flags 롤
		ma_preDmgJobBuffs(s);

		// 9~11) HP5% 제한 / 도사버프 / 스페셀버프 / 데미지 계산
		if ((earlyMsg = ma_applyBuffsAndCalcDmg(s)) != null) return earlyMsg;

		// [도적] 2타 사전 계산
		ma_thiefDoubleAtkPreCalc(s);

		// 12) 사망 처리
		if ((earlyMsg = ma_deathCheck(s)) != null) return earlyMsg;

		// 13) 처치·드랍 판단 + 직업별 스킬
		ma_resolveKillAndJobSkills(s);

		// 14) DB 반영 + 업적 부여
		ma_persistAndAchv(s);

		// 15~16) 메시지 구성 + 포인트 출력
		return ma_buildMessage(s);
	}

	/**
	 * [COMPACT] 축약 메시지 버전 — 원본 monsterAttack 유지, 비교용 신규 메서드
	 * 상단 4줄 요약 + === + 원문 전체
	 */
	public String monsterAttackCompact(HashMap<String, Object> map) {
		map.put("cmd", "monster_attack");
		AttackSession s = new AttackSession(map);
		String earlyMsg;

		if ((earlyMsg = ma_validate(s))              != null) return earlyMsg;
		if ((earlyMsg = ma_calcStats(s))             != null) return earlyMsg;
		if ((earlyMsg = ma_resolveMonster(s))        != null) return earlyMsg;
		if ((earlyMsg = ma_cooldownAndHp(s))         != null) return earlyMsg;
		ma_preDmgJobBuffs(s);
		if ((earlyMsg = ma_applyBuffsAndCalcDmg(s))  != null) return earlyMsg;
		ma_thiefDoubleAtkPreCalc(s);

		// 12) 사망 처리
		if ((earlyMsg = ma_deathCheck(s)) != null)
			return ma_deathMsgShort(s, earlyMsg);

		// 13~14) 처치·드랍 + DB 반영 + 업적
		ma_resolveKillAndJobSkills(s);
		ma_persistAndAchv(s);

		// 15~16) 축약 메시지
		return ma_buildMessageShort(s);
	}

	// ────────────────────────────────────────────────────────────
	//  AttackSession — monsterAttack 섬션 간 공유 상태
	// ────────────────────────────────────────────────────────────
	private static class AttackSession {
		/* 입력 */
		HashMap<String,Object> map;
		String userName, roomName, param1;
		boolean master;

		/* 스탯 / 직업 */
		UserBattleContext ctx;
		User u;
		String job;
		int effAtkMin, effAtkMax, critRate, critDmg, hpMax, regen;
		double berserkMul = 1.0;

		/* 몬스터 */
		Monster m;
		int monMaxHp, monHpRemainBefore, monAtk, monLv;
		boolean lucky, dark, gray, shadow, nightmare, hell;
		int beforeJobSkillYn;
		int killCountForThisMon, nmKillCountForThisMon, hellKillCountForThisMon;

		/* 버프 / 쿨타임 */
		HashMap<String,Object> activeBuff;
		SpecialBuffResult buff;
		int cooldownBuff;
		int itemCdReduction = 0; // [7004] + 세트효과 쿨타임 감소 합산값
		String cdJob;
		AttackDeathStat cachedAds;
		boolean revivedThisTurn;

		/* 데미지 */
		Flags flags;
		DamageOutcome dmg, dmg2;
		AttackCalc calc, calc2;
		boolean willKill, thiefDoubleAtk;

		/* 처치 / 보상 */
		Resolve res;
		LevelUpResult up;
		int buffStart, buffIng;
		String buffCode;

		/* 업적 */
		List<KillStat> cachedKillStats;
		List<AchievementCount> userAchvList;
		Set<String>         achievedCmdSet = new HashSet<>();
		Map<String,Integer> globalAchvMap  = new HashMap<>();
		Map<String,Integer> userAchvMap    = new HashMap<>();

		/* 메시지 조각 */
		String dosabuffMsg = "";
		String stealMsg = "", stealPoint = "", stealBonus = "";
		String newPoint = "", newBonus  = "";
		String dosaCastMsg = null;
		String bagDropMsg  = "";
		String bonusMsg    = "";

		AttackSession(HashMap<String,Object> map) {
			this.map      = map;
			this.userName = Objects.toString(map.get("userName"), "");
			this.roomName = Objects.toString(map.get("roomName"), "");
		}
	}

	// ─ 0~1) 입력 검증 / 매크로 잠금 ──────────────────────────────────
	private String ma_validate(AttackSession s) {
		if (s.roomName.isEmpty() || s.userName.isEmpty())
			return "방/유저 정보가 누락되었습니다.";

		s.master = "람쥐봇 문의방".equals(s.roomName) && "일어난다람쥐/카단".equals(s.userName);
		if (s.master) s.map.put("param1", "test");

		if ("람쥐봇 문의방".equals(s.roomName) && !s.master)
			return "문의방에서는 불가능합니다.";

		/*
		HashMap<String,Object> lockParam = botNewService.lockMacroUser(s.userName);
		int lockCode = (Integer) lockParam.get("outCode");
		if (lockCode == 1 || lockCode == 2)
			return "공격불가 상태입니다 code:" + lockParam.get("outMsg");
		 */
		s.param1 = Objects.toString(s.map.get("param1"), "");
		return null;
	}

	// ─ 2~4) 공통 스탯 + 직업 공격배율 ────────────────────────────
	private String ma_calcStats(AttackSession s) {
		HashMap<String,Object> statMap = new HashMap<>(s.map);
		statMap.put("param1", "");
		s.ctx = calcUserBattleContext(statMap);
		if (!s.ctx.success) return s.ctx.errorMessage;

		s.u   = s.ctx.user;
		s.job = (s.u.job == null ? "" : s.u.job.trim());
		if (s.job.isEmpty())
			return s.userName + " 님, /직업 을 통해 먼저 전직해주세요." + NL
				 + "12/15 업데이트 이후 가방으로 능력치 변경을 확인해주세요.";

		int atkMin = s.ctx.atkMin;
		int atkMax = s.ctx.atkMax;
		s.regen   = s.ctx.regen;
		s.hpMax   = s.ctx.hpMax;
		s.critRate = s.ctx.crit;
		s.critDmg  = s.ctx.critDmg;

		double jobDmgMul  = 1.0;
		int    jobBonusMin = 0, jobBonusMax = 0;
		if      ("궁수".equals(s.job))   jobDmgMul = 3.0;
		else if ("사냥꾼".equals(s.job))  jobDmgMul = 3.0;
		else if ("궁사".equals(s.job))   jobDmgMul = 1.0;
		else if ("전사".equals(s.job))   jobDmgMul = 1.4;
		else if ("검성".equals(s.job))   jobDmgMul = 2.5;
		else if ("어쓰신".equals(s.job))  jobDmgMul = 1.3;
		else if ("제너럴".equals(s.job))  jobDmgMul = 1.2;
		else if ("저격수".equals(s.job))  jobDmgMul = 2.0;
		else if ("처단자".equals(s.job))  jobDmgMul = 1.4;
		else if ("용사".equals(s.job))   jobDmgMul = 1.4;
		else if ("복수자".equals(s.job))  jobDmgMul = 0.2;
		else if ("음양사".equals(s.job))  jobDmgMul = 1.6;

		s.effAtkMin = (int)Math.round(atkMin * jobDmgMul + jobBonusMin);
		s.effAtkMax = (int)Math.round(atkMax * jobDmgMul + jobBonusMax);
		if (s.effAtkMax < s.effAtkMin) s.effAtkMax = s.effAtkMin;
		return null;
	}

	// ─ 5~6) 부활 / 진행중·신규 몬스터 ────────────────────────────────
	private String ma_resolveMonster(AttackSession s) {
		String reviveMsg = reviveAfter1hIfDead(s.userName, s.roomName, s.u, s.hpMax, s.regen);
		if (reviveMsg != null) {
			if (!reviveMsg.isEmpty()) return reviveMsg;
			s.revivedThisTurn = true;
		}

		List<AchievementCount> globalList = getAchvGlobalCached();
		if (globalList != null) {
			for (AchievementCount ac : globalList) {
				if (ac == null || ac.getCmd() == null) continue;
				s.globalAchvMap.put(ac.getCmd(), ac.getCnt());
			}
		}

		s.nightmare = s.ctx.user.nightmareYn >= 1;
		s.hell      = s.ctx.user.nightmareYn == 2;

		try { s.cachedKillStats = botNewService.selectKillStats(s.userName, s.roomName); } catch (Exception ignore) {}

		OngoingBattle ob = botNewService.selectOngoingBattle(s.userName, s.roomName);
		return (ob != null) ? ma_resolveOngoing(s, ob) : ma_resolveNew(s);
	}

	private String ma_resolveOngoing(AttackSession s, OngoingBattle ob) {
		s.m = getMonsterCached(ob.monNo);
		if (s.m == null) return "진행중 몬스터 정보를 찾을 수 없습니다.";
		s.beforeJobSkillYn = ob.beforeJobSkillYn;
		s.monMaxHp = s.m.monHp;  s.monAtk = s.m.monAtk;  s.monLv = s.m.monLv;
		if (s.nightmare) {
			s.monMaxHp *= NM_MUL_HP_ATK;
			s.monAtk   *= NM_MUL_HP_ATK;
			s.monLv    += s.hell ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
		}
		s.lucky  = (ob.luckyYn != null && ob.luckyYn == 1);
		s.dark   = (ob.luckyYn != null && ob.luckyYn == 2);
		s.gray   = (ob.luckyYn != null && ob.luckyYn == 3);
		s.shadow = (ob.luckyYn != null && ob.luckyYn == 4);
		if (s.dark) applyDarkMonsterScale(s);
		s.monHpRemainBefore = Math.max(0, s.monMaxHp - ob.totalDealtDmg);
		resolveKillCounts(s);
		return null;
	}

	private String ma_resolveNew(AttackSession s) {
		if (s.u.targetMon == 99) {
			s.m = null; s.monMaxHp = 0; s.monHpRemainBefore = 0; s.monAtk = 0; s.monLv = 0;
			return null;
		}
		s.m = getMonsterCached(s.u.targetMon);
		if (s.m == null) return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";
		s.beforeJobSkillYn  = -1;
		s.monMaxHp          = s.m.monHp;
		s.monHpRemainBefore = s.m.monHp;
		s.monAtk            = s.m.monAtk;
		s.monLv             = s.m.monLv;
		if (s.nightmare) {
			s.monMaxHp          *= NM_MUL_HP_ATK;
			s.monHpRemainBefore *= NM_MUL_HP_ATK;
			s.monAtk            *= NM_MUL_HP_ATK;
			s.monLv             += s.hell ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
		}
		resolveKillCounts(s);
		rollDarkAndLucky(s);
		return null;
	}

	private void resolveKillCounts(AttackSession s) {
		if (s.cachedKillStats == null || s.m == null) return;
		for (KillStat ks : s.cachedKillStats) {
			if (ks.monNo == s.m.monNo) {
				s.killCountForThisMon     = ks.killCount;
				s.nmKillCountForThisMon   = ks.nmKillCount;
				s.hellKillCountForThisMon = ks.hellKillCount;
				break;
			}
		}
	}

	private void applyDarkMonsterScale(AttackSession s) {
		if      (s.m.monNo < 15)  { s.monMaxHp = s.monMaxHp * 3;                              s.monAtk = (int)Math.round(s.monAtk * 1.50); }
		else if (s.m.monNo >= 25) { s.monMaxHp = (int)Math.round(s.monMaxHp * 1.75); s.monAtk = (int)Math.round(s.monAtk * 1.10); }
		else if (s.m.monNo >= 15) { s.monMaxHp = (int)Math.round(s.monMaxHp * 2.50); s.monAtk = (int)Math.round(s.monAtk * 1.25); }
	}

	private void rollDarkAndLucky(AttackSession s) {
		Monster m = s.m;
		int levelGap = s.u.lv - s.monLv;
		double darkRate = Math.max(0, levelGap / 100) * 0.20;
		if ("어둠사냥꾼".equals(s.job)) darkRate += DARK_RATE_DARK;
		// 일반: killCount 기준 / 나이트메어: nmKillCount 기준 / 헬: hellKillCount 기준
		if (!s.nightmare) {
		    if (s.killCountForThisMon >= 350 && m.monNo >= 15) darkRate += 0.05;
		    if (s.killCountForThisMon >= 300 && m.monNo <  15) darkRate += 0.10;
		} else if (s.hell) {
		    if (s.hellKillCountForThisMon > 150 && m.monNo >= 15) darkRate += 0.05;
		    if (s.hellKillCountForThisMon > 150 && m.monNo <  15) darkRate += 0.10;
		} else {
		    if (s.nmKillCountForThisMon > 150 && m.monNo >= 15) darkRate += 0.05;
		    if (s.nmKillCountForThisMon > 150 && m.monNo <  15) darkRate += 0.10;
		}
		if (ThreadLocalRandom.current().nextDouble() < darkRate) s.dark = true;

		double luckyRate = "도사".equals(s.job) ? LUCKY_RATE_DOSA : LUCKY_RATE;
		s.lucky = (s.killCountForThisMon >= 50) && ThreadLocalRandom.current().nextDouble() < luckyRate;

		int globalCnt = s.globalAchvMap.getOrDefault("ACHV_FIRST_CLEAR_MON_" + m.monNo, 0);
		if (s.dark  || globalCnt == 0 || m.monNo > 50)                         s.lucky = false;
		if (s.lucky || globalCnt == 0 || m.monNo > 50 || "사신".equals(s.job)) s.dark  = false;

		if ("음양사".equals(s.job)) s.gray = ThreadLocalRandom.current().nextDouble() < 0.05;
		if (s.gray) { s.lucky = false; s.dark = false; }
		if ("곰".equals(s.job))  { s.lucky = false; s.dark = false; }

		if (s.dark) { applyDarkMonsterScale(s); s.monHpRemainBefore = s.monMaxHp; }

		// 그림자 몬스터: 헬에서 해당 몬스터 500회+ 처치 시 10% 확률
		if (s.hell && s.hellKillCountForThisMon >= 500
		        && ThreadLocalRandom.current().nextDouble() < 0.10) {
		    s.shadow = true;
		    s.lucky = false;
		    s.dark  = false;
		    s.gray  = false;
		    // 능력치는 원본 그대로 (스케일 없음)
		}
	}

	// ─ 7) 쿨타임 / 8) HP 확정 / [S3] 분기 ───────────────────────────────
	private String ma_cooldownAndHp(AttackSession s) {
		s.buff = handleSpecialBuff(s.userName);
		s.activeBuff   = s.buff.activeBuff;
		s.cooldownBuff = 0;
		if (s.activeBuff != null) {
			if ("쿨타임".equals(s.activeBuff.get("FLAG_CODE")))
				s.cooldownBuff = (int)Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
			if ("쿨타임감소".equals(s.activeBuff.get("FLAG_CODE")))
				s.cooldownBuff = -1;
		}

		// ctx.ads에 calcUserBattleContext에서 이미 조회한 값 재사용 (DB 중복 조회 방지)
		s.cachedAds = (s.ctx != null && s.ctx.ads != null)
		        ? s.ctx.ads
		        : botNewService.selectAttackDeathStats(s.userName, s.roomName);
		Timestamp cachedLastAtk = (s.cachedAds != null) ? s.cachedAds.lastAttackTime : null;
		s.cdJob = (s.cachedAds != null && s.cachedAds.lastAttackJob != null) ? s.cachedAds.lastAttackJob : s.job;

		// [7004] 모래시계: 쿨타임 20% 감소 / 세트: DB값(%) 직접 합산
		s.itemCdReduction  = s.ctx.ownedBossItems.contains(7004) ? 20 : 0;
		s.itemCdReduction += s.ctx.setCooldownReduce; // DB BONUS_VALUE 그대로 % 적용 (2세트+3세트 각 10%)
		CooldownCheck cd = checkCooldown(s.userName, s.roomName, s.param1, s.cdJob, s.cooldownBuff, cachedLastAtk, s.itemCdReduction);
		if (!cd.ok) {
			long min = cd.remainSeconds / 60;
			long sec = cd.remainSeconds % 60;
			return String.format("%s님, 공격 쿨타임 %d분 %d초 남았습니다.", s.userName, min, sec);
		}

		int effectiveHp = s.revivedThisTurn
				? s.u.hpCur
				: computeEffectiveHpFromLastAttack(s.userName, s.roomName, s.u, s.hpMax, s.regen);
		s.u.hpCur = effectiveHp;

		if (s.u.targetMon == 99) {
			if ("곰".equals(s.job)) return s.userName + "님, [곰]은 상급악마를 공격할 수 없습니다.";
			return bossAttackS3Controller.attackBossS3(s.map, s.ctx);
		}

		s.userAchvList = botNewService.selectAchvCountsGlobal(s.userName, s.roomName);
		if (s.userAchvList != null) {
			for (AchievementCount ac : s.userAchvList) {
				s.achievedCmdSet.add(ac.getCmd());
				if (ac.getCmd() != null) s.userAchvMap.put(ac.getCmd(), ac.getCnt());
			}
		}
		return null;
	}

	// ─ 8-후) berserkMul + Flags 롤 ──────────────────────────────────────────
	private void ma_preDmgJobBuffs(AttackSession s) {
		if ("파이터".equals(s.job) && s.hpMax > 0) {
			double hpRatio = (double) s.u.hpCur / s.hpMax;
			if (hpRatio < 1) s.berserkMul = 1.0 + (1 - hpRatio) * 0.5;
		}
		if ("용사".equals(s.job)    && s.dark)              s.berserkMul = 1.5;
		if ("처단자".equals(s.job)  && s.lucky)             s.berserkMul = 1.5;
		if ("음양사".equals(s.job)  && (s.lucky || s.dark)) s.berserkMul = 1.5;
		if ("어둠사냥꾼".equals(s.job) && s.dark)   s.berserkMul = 3.0;
		s.flags = rollFlags(s.u, s.m);
	}

	// ─ 9~11) HP5% 제한 / 도사버프 / 스페셀버프 / 데미지 계산 ──────────
	private String ma_applyBuffsAndCalcDmg(AttackSession s) {
		// 9) HP 5% 제한 체크
		int origHpMax  = s.u.hpMax;
		int origRegen  = s.u.hpRegen;
		s.u.hpMax   = s.hpMax;
		s.u.hpRegen = s.regen;
		try {
			String hpMsg = buildBelowHalfMsg(s.userName, s.roomName, s.u, s.param1, s.cooldownBuff, s.cdJob, s.itemCdReduction);
			if (!"사신".equals(s.job) && hpMsg != null) return hpMsg;
		} finally {
			s.u.hpMax   = origHpMax;
			s.u.hpRegen = origRegen;
		}

		// 10) 도사 버프 감지 (적용은 데미지 계산 이후 — 헬/나메 구분 없음, 전체방 기준)
		DosaBuffEffect dosaSelf = null, dosaRoom = null;
		if ("도사".equals(s.job) || "음양사".equals(s.job)) {
			dosaSelf = new DosaBuffEffect();
		}
		dosaRoom = loadGlobalDosaBuffAndBuild();
		if (dosaRoom != null) {
			botNewService.clearRoomBuff();
		}

		s.u.hunterGrade = s.ctx.hunterGrade;

		// 스페셀버프 적용 (공격력/치피/치확)
		if (s.activeBuff != null) {
			if ("공격력".equals(s.activeBuff.get("FLAG_CODE"))) {
				String effectType = (String) s.activeBuff.get("EFFECT_TYPE");
				double value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
				if ("배율".equals(effectType)) { s.effAtkMin = (int)Math.round(s.effAtkMin * value); s.effAtkMax = (int)Math.round(s.effAtkMax * value); }
				else                                   { s.effAtkMin += (int)value; s.effAtkMax += (int)value; }
			}
			if ("치피".equals(s.activeBuff.get("FLAG_CODE"))) {
				double value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
				if (s.hell) value = Math.max(0, (int)Math.round(value * MiniGameUtil.getHellNerfMult(s.ctx.hunterGrade)));
				s.critDmg += (int)value;
			}
			if ("치확".equals(s.activeBuff.get("FLAG_CODE"))) {
				double value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
				if (s.hell) value = Math.max(0, (int)Math.round(value * MiniGameUtil.getHellNerfMult(s.ctx.hunterGrade)));
				s.critRate += (int)value;
			}
		}

		boolean hasBless = (s.u.blessYn == 1);
		if (hasBless) {
			s.effAtkMin = (int)Math.round(s.effAtkMin * 1.5);
			s.effAtkMax = (int)Math.round(s.effAtkMax * 1.5);
		}

		// 11) 데미지 계산
		s.dmg = calculateDamage(s.u, s.m, s.flags,
				s.effAtkMin, s.effAtkMax, s.critRate, s.critDmg,
				s.berserkMul, s.monHpRemainBefore, s.hpMax, s.beforeJobSkillYn, s.nightmare,
				s.ctx.ownedBossItems);
		s.calc     = s.dmg.calc;
		s.flags    = s.dmg.flags;
		s.willKill = s.dmg.willKill;

		// 11-후) 도사 버프 최종 데미지 적용 (+1000 flat, +5%, 최대체력 5% 회복)
		if (dosaSelf != null || dosaRoom != null) {
		    int buffCount = (dosaSelf != null ? 1 : 0) + (dosaRoom != null ? 1 : 0);
		    // [7012] 도사의 가르침: 도사/음양사 버프 계수 3배
		    int coef = s.ctx.ownedBossItems.contains(7012) ? 3 : 1;
		    if (s.calc.atkDmg > 0) {
		        s.calc.atkDmg += buffCount * 1000 * coef;
		        s.calc.atkDmg += (int) Math.round(s.calc.atkDmg * (buffCount * 5 * coef) / 100.0);
		    }
		    int heal = (int) Math.round(s.hpMax * (buffCount * 5 * coef) / 100.0);
		    int beforeHpDosa = s.u.hpCur;
		    s.u.hpCur = Math.min(s.hpMax, s.u.hpCur + heal);
		    s.dosabuffMsg = buildUnifiedDosaBuffMessage(dosaSelf, dosaRoom, s.u.hpCur - beforeHpDosa, coef);
		}

		// ── 세트 회피: monPattern 1,4,5,6 회피 판정 ──────────────────────────────
		if (s.ctx.setEvasionRate > 0) {
		    int mp = s.flags.monPattern;
		    if ((mp == 1 || mp == 4 || mp == 5 || mp == 6)
		            && ThreadLocalRandom.current().nextInt(100) < s.ctx.setEvasionRate) {
		        String origMsg = s.calc.patternMsg != null ? s.calc.patternMsg : "";
		        s.calc.monDmg   = 0;
		        s.calc.endBattle = false;
		        s.calc.patternMsg = origMsg + " → [회피!]";
		    }
		}

		// 11-후) 축복술사
		if ("축복술사".equals(s.job) && s.dmg.calc.atkDmg > 0) {
			int blessCount = (s.u.lv / 100) + 1;
			int cnt = botNewService.updateRandomBlessUser(s.userName, blessCount);
			if (cnt > 0) s.dmg.dmgCalcMsg += NL + "✨랜덤한 " + blessCount + "명에게 축복이 내려졌습니다!";
		}

		// 11-후) 축복 힐
		if (hasBless) {
			int heal = (int)Math.round(s.hpMax * 0.3);
			int beforeHp = s.u.hpCur;
			s.u.hpCur = Math.min(s.hpMax, s.u.hpCur + heal);
			if (s.u.hpCur > beforeHp) s.dmg.dmgCalcMsg += NL + "✨ 축복의 치유! " + (s.u.hpCur - beforeHp) + " 회복";
			botNewService.clearBlessYn(s.userName);
		}

		// 11-후) 스페셀버프 회복
		if (s.activeBuff != null && "회복".equals(s.activeBuff.get("FLAG_CODE"))) {
			double value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
			int heal = (int)Math.round(s.hpMax * value);
			int beforeHp = s.u.hpCur;
			s.u.hpCur = Math.min(s.hpMax, s.u.hpCur + heal);
			if (s.u.hpCur > beforeHp) s.dmg.dmgCalcMsg += NL + "✨ 스페셜타임-회복! " + (s.u.hpCur - beforeHp);
		}

		// 패턴 6: 전투 종료
		if (s.calc.endBattle) {
			botNewService.closeOngoingBattleTx(s.userName, s.roomName);
			Resolve empty = new Resolve(); empty.killed = false; empty.gainExp = 0; empty.dropCode = "0";
			return buildAttackMessage(s.userName, s.u, s.m, s.flags, s.calc,
					empty, null, s.monHpRemainBefore, s.monMaxHp,
					s.effAtkMin, s.effAtkMax, s.hpMax, null, null, null, s.nightmare, s.ctx);
		}
		return null;
	}

	// ─ [도적] 2타 사전 계산 ──────────────────────────────────────────
	private void ma_thiefDoubleAtkPreCalc(AttackSession s) {
		if (!"도적".equals(s.job)) return;
		double thiefProb = s.ctx.ownedBossItems.contains(7002) ? 0.50 : 0.30;
		s.thiefDoubleAtk = ThreadLocalRandom.current().nextDouble() < thiefProb;
		if (s.thiefDoubleAtk) {
			Flags f2 = rollFlags(s.u, s.m);
			s.dmg2  = calculateDamage(s.u, s.m, f2, s.effAtkMin, s.effAtkMax, s.critRate, s.critDmg,
					s.berserkMul, s.monHpRemainBefore, s.hpMax, s.beforeJobSkillYn, s.nightmare,
					s.ctx.ownedBossItems);
			s.calc2 = s.dmg2.calc;
		}
	}

	// ─ 12) 사망 처리 ───────────────────────────────────────────────────
	private String ma_deathCheck(AttackSession s) {
		int newHpPreview = Math.max(0, s.u.hpCur - s.calc.monDmg);
		if (newHpPreview > 0) return null;

		int dealtThisTurn  = Math.max(0, s.calc.atkDmg);
		int monRemainAfter = Math.max(0, s.monHpRemainBefore - dealtThisTurn);

		botNewService.closeOngoingBattleTx(s.userName, s.roomName);
		botNewService.updateUserHpOnlyTx(s.userName, s.roomName, 0);
		botNewService.insertBattleLogTx(new BattleLog()
				.setUserName(s.userName).setRoomName(s.roomName).setLv(s.u.lv)
				.setTargetMonLv(s.m.monNo).setGainExp(0)
				.setAtkDmg(s.calc.atkDmg).setMonDmg(s.calc.monDmg)
				.setAtkCritYn(s.flags.atkCrit ? 1 : 0).setMonPatten(s.flags.monPattern)
				.setKillYn(0).setNowYn(0).setDropYn(0).setDeathYn(1).setLuckyYn(0)
				.setJobSkillYn(0).setJob(s.job).setNightmareYn(s.ctx.user.nightmareYn));

		String deathAchvMsg = grantDeathAchievements(s.userName, s.roomName);
		return s.userName + "님, 이번전투에서 패배하여, 전투 불능이 되었습니다." + NL
				+ s.calc.monDmg + " 피해로 사망!" + NL
				+ "▶ 이번에 준 피해: " + dealtThisTurn + NL
				+ "▶ 몬스터 남은 체력: " + monRemainAfter + " / " + s.monMaxHp + NL
				+ "현재 체력: 0 / " + s.hpMax + NL
				+ "5분 뒤 최대 체력의 10%로 부활하며," + NL
				+ "이후 5분마다 HP_REGEN 만큼 서서히 회복됩니다." + NL
				+ deathAchvMsg;
	}

	// ─ 13) 처치·드랍 판단 + 직업별 스킬 ─────────────────────────────
	private void ma_resolveKillAndJobSkills(AttackSession s) {
		s.res = resolveKillAndDrop(s.m, s.calc, s.willKill, s.u, s.lucky, s.dark, s.gray, s.shadow, s.ctx.user.nightmareYn, s.ctx.ownedBossItems);
		if ("궁수".equals(s.u.job) || "사냥꾼".equals(s.u.job)) s.res.gainExp *= 3;

		// SP 누적용 — baroSellItem은 INSERT 없이 outSp만 반환, 여기서 합산 후 단건 INSERT
		SP stealSpTotal = new SP(0, "");
		SP dropSpTotal  = new SP(0, "");

		// —— 도적: 더블어택 + 스틸 ——
		if ("도적".equals(s.job) && !(s.m.monNo > 50)) {
			if (ThreadLocalRandom.current().nextDouble() < 0.50) {
				String dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
				if (!dn.isEmpty()) try {
					Integer id = getItemIdCached(dn);
					if (id != null) {
						botNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
						s.stealMsg += "✨ [1타] " + s.m.monName + "의 아이템을 훔쳤습니다! (" + dn + "조각)";
						s.calc.jobSkillUsed = true;
					}
					SP[] sp=new SP[1]; String[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b, sp); s.stealBonus += b[0];
					if (sp[0] != null) stealSpTotal = stealSpTotal.add(sp[0]);
				} catch (Exception ignore) {}
			}
			if (s.thiefDoubleAtk && s.calc2 != null && ThreadLocalRandom.current().nextDouble() < 0.50) {
				String dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
				if (!dn.isEmpty()) try {
					Integer id = getItemIdCached(dn);
					if (id != null) {
						botNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
						s.stealMsg += (s.stealMsg.isEmpty() ? "" : NL) + "✨ [2타] " + s.m.monName + "의 아이템을 훔쳤습니다! (" + dn + "조각)";
						s.calc2.jobSkillUsed = true;
					}
					SP[] sp=new SP[1]; String[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b, sp); s.stealBonus += b[0];
					if (sp[0] != null) stealSpTotal = stealSpTotal.add(sp[0]);
				} catch (Exception ignore) {}
			}
		}

		// —— 처단자: 추가 드랍 ——
		if ("처단자".equals(s.job) && !(s.m.monNo > 50) && s.willKill) {
			int monHp = s.m.monHp * (s.nightmare ? NM_MUL_HP_ATK : 1);
			int extra = Math.min((s.calc.atkDmg / monHp) - 1, 5);
			String dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
			if (!dn.isEmpty()) try {
				Integer id = getItemIdCached(dn);
				int qty = 2 + extra;
				if (id != null) {
					boolean bonus = ThreadLocalRandom.current().nextDouble() < 0.10;
					if (bonus) qty *= 2;
					HashMap<String,Object> inv = buildStealInv(s.userName, s.roomName, id);
					inv.put("qty", qty);
					botNewService.insertInventoryLogTx(inv);
					s.stealMsg = "✨ 날카로운 처단으로 추가획득 (+" + dn + "조각" + qty + ")" + (bonus ? "✨ 보너스!" : "");
					s.calc.jobSkillUsed = true;
				}
				SP[] sp=new SP[1]; String[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", qty, s.nightmare, b, sp); s.stealBonus += b[0];
				if (sp[0] != null) stealSpTotal = stealSpTotal.add(sp[0]);
			} catch (Exception ignore) {}
		}

		// —— 용사: 스틸 ——
		if ("용사".equals(s.job) && !(s.m.monNo > 50) && ThreadLocalRandom.current().nextDouble() < 0.60) {
			String dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
			if (!dn.isEmpty()) try {
				Integer id = getItemIdCached(dn);
				if (id != null) {
					botNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
					s.stealMsg += ThreadLocalRandom.current().nextDouble() < 0.5
						? "✨ " + s.m.monName + "와  싸우던 마을주민에게서 약탈했다! (" + dn + "조각)"
						: "✨ 촌장 집에서 " + s.m.monName + "의 아이템을 발견했다! (" + dn + "조각)";
					s.calc.jobSkillUsed = true;
				}
				SP[] sp=new SP[1]; String[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b, sp); s.stealBonus += b[0];
				if (sp[0] != null) stealSpTotal = stealSpTotal.add(sp[0]);
			} catch (Exception ignore) {}
		}

		// STEAL SP 단건 INSERT
		if (stealSpTotal.getValue() > 0 || !stealSpTotal.getUnit().isEmpty()) {
			try {
				HashMap<String, Object> pr = new HashMap<>();
				pr.put("userName", s.userName); pr.put("roomName", s.roomName);
				pr.put("score", stealSpTotal.getValue()); pr.put("scoreExt", stealSpTotal.getUnit());
				pr.put("cmd", "DROP_SP_STEAL");
				botNewService.insertPointRank(pr);
			} catch (Exception ignore) {}
		}

		// —— 음양사: 기원 메시지 ——
		if ("음양사".equals(s.job)) s.dosaCastMsg = "✨" + s.job + "의 기원! 다음 공격자 강화!";

		// DROP SP
		if (s.res.killed && !"0".equals(s.res.dropCode)) {
			String dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
			if (!dn.isEmpty()) {
				SP[] sp=new SP[1]; String[] nb={""}; s.newPoint += " +" + baroSellItem(dn, 0, s.res, s.userName, s.roomName, s.ctx, s.u, "DROP", 1, s.nightmare, nb, sp); s.newBonus += nb[0];
				if (sp[0] != null) dropSpTotal = dropSpTotal.add(sp[0]);
				// [7008] 추가 드랍 +1 → 기본 드랍코드 1로 SP 추가 지급
				if (s.res.bonusNormalDrop) {
					Resolve bonusRes = new Resolve();
					bonusRes.dropCode = "1";
					SP[] sp2=new SP[1]; String[] nb2={""}; s.newPoint += " +" + baroSellItem(dn, 0, bonusRes, s.userName, s.roomName, s.ctx, s.u, "DROP", 1, s.nightmare, nb2, sp2); s.newBonus += nb2[0];
					if (sp2[0] != null) dropSpTotal = dropSpTotal.add(sp2[0]);
				}
				// DROP SP 단건 INSERT
				if (dropSpTotal.getValue() > 0 || !dropSpTotal.getUnit().isEmpty()) {
					try {
						HashMap<String, Object> pr = new HashMap<>();
						pr.put("userName", s.userName); pr.put("roomName", s.roomName);
						pr.put("score", dropSpTotal.getValue()); pr.put("scoreExt", dropSpTotal.getUnit());
						pr.put("cmd", "DROP_SP_DROP");
						botNewService.insertPointRank(pr);
					} catch (Exception ignore) {}
				}
			}
		}

		// 경험치 스페셀버프
		if (s.activeBuff != null && "경험치".equals(s.activeBuff.get("FLAG_CODE"))) {
			double pct = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
			s.res.gainExp = (int)(s.res.gainExp * (1 + pct / 100.0));
		}
	}

	/** buildStealInv 헬퍼 */
	private HashMap<String,Object> buildStealInv(String userName, String roomName, Integer itemId) {
		HashMap<String,Object> inv = new HashMap<>();
		inv.put("userName", userName); inv.put("roomName", roomName);
		inv.put("itemId", itemId);     inv.put("qty", 1);
		inv.put("delYn", "1");         inv.put("gainType", "STEAL");
		return inv;
	}

	// ─ 14) DB 반영 + 업적 부여 ────────────────────────────────────────
	private void ma_persistAndAchv(AttackSession s) {
		s.buffStart = s.buff.started ? 1 : 0;
		s.buffIng   = s.activeBuff != null ? 1 : 0;
		s.buffCode  = s.activeBuff != null ? (String) s.activeBuff.get("FLAG_CODE") : null;
		s.up = persist(s.userName, s.roomName, s.u, s.m, s.flags, s.calc, s.res, s.hpMax, s.nightmare, s.buffStart, s.buffIng, s.buffCode);

		// 레벨 업적 (레벨업 시 체크)
		if (s.up != null && s.up.levelUpCount > 0) {
			String lvAchvMsg = grantLevelAchievements(s.userName, s.roomName, s.achievedCmdSet, s.up.afterLv);
			if (lvAchvMsg != null && !lvAchvMsg.isEmpty()) s.bonusMsg += NL + lvAchvMsg;
		}

		// [도적] 2타 배틀로그 (PK 충돌 방지: Batch INSERT → SYSTIMESTAMP - (shotIndex+2)초)
		if (s.thiefDoubleAtk && s.calc2 != null && s.m != null) {
			try {
				List<BattleLog> thiefLogs = new ArrayList<>();
				thiefLogs.add(new BattleLog()
						.setUserName(s.userName).setRoomName(s.roomName).setLv(s.up.beforeLv)
						.setTargetMonLv(s.m.monNo).setGainExp(0)
						.setAtkDmg(s.calc2.atkDmg).setMonDmg(0)
						.setAtkCritYn(s.dmg2.flags != null && s.dmg2.flags.atkCrit ? 1 : 0)
						.setMonPatten(0).setKillYn(0).setNowYn(1).setDropYn(0)
						.setDeathYn(0).setLuckyYn(0)
						.setJobSkillYn(s.calc2.jobSkillUsed ? 1 : 0).setJob(s.job)
						.setNightmareYn(s.ctx.user.nightmareYn)
						.setSpecialBuffStart(0).setSpecialBuffIng(s.buffIng)
						.setSpecialBuffCode(s.buffCode).setShotIndex(1));
				botNewService.insertBattleLogsBatch(thiefLogs);
			} catch (Exception ignore) {}
		}

		if (s.res.killed) {
			// 처치 후 드랍 아이템이 인벤토리에 추가되므로 캐시 무효화
			invalidateInvBuff(s.userName);
			botNewService.closeOngoingBattleTx(s.userName, s.roomName);
			HashMap<String,Object> achvInvCounts = null;
			try { achvInvCounts = botNewService.selectAchievementInventoryCounts(s.userName); } catch (Exception ignore) {}
			List<HashMap<String,Object>> achvGainRows = buildGainRowsFromCounts(achvInvCounts);
			int achvBagTotal    = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("BAG_COUNT",    0)).intValue() : 0;
			int achvSoldCount   = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("SOLD_COUNT",   0)).intValue() : 0;
			int achvPotionCount = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("POTION_COUNT", 0)).intValue() : 0;
			MiniGameUtil.POTION_USE_CACHE.put(s.userName, achvPotionCount);

			List<HashMap<String,Object>> achvJobSkillRows = null;
			try { achvJobSkillRows = botNewService.selectJobSkillUseCountAllJobs(s.userName, s.roomName); } catch (Exception ignore) {}
			List<HashMap<String,Object>> achvBuffRows = null;
			try { achvBuffRows = botNewService.selectSpecialBuffAchvStats(s.userName); } catch (Exception ignore) {}

			String killAchvMsg     = grantKillAchievements(s.userName, s.roomName, s.achievedCmdSet, s.cachedKillStats);
			String itemAchvMsg     = grantLightDarkItemAchievements(s.userName, s.roomName, s.achievedCmdSet, achvGainRows);
			String bagAchvMsg      = grantBagAcquireAchievementsFast(s.userName, s.roomName, s.achievedCmdSet, achvBagTotal);
			String attackAchvMsg   = grantAttackCountAchievements(s.userName, s.roomName, s.achievedCmdSet, s.cachedAds);
			String jobSkillAchvMsg = grantJobSkillUseAchievementsAllJobs(s.userName, s.roomName, s.achievedCmdSet, achvJobSkillRows);
			String shopSellAchvMsg = grantShopSellAchievementsFast(s.userName, s.roomName, s.achievedCmdSet, achvSoldCount);
			String potionAchvMsg   = grantPotionUseAchievements(s.userName, s.roomName, s.achievedCmdSet, achvPotionCount);
			String buffAchvMsg     = grantSpecialBuffAchievements(s.userName, s.roomName, s.achievedCmdSet, achvBuffRows);
			String achvRewardMsg   = grantAchievementBasedReward(s.userName, s.roomName, s.userAchvList);
			String specialAchvMsg  = grantSpecialHistoricalAchievements(s.userName, s.roomName);

			if (isAnyNonEmpty(killAchvMsg, itemAchvMsg, attackAchvMsg, jobSkillAchvMsg, shopSellAchvMsg, potionAchvMsg, achvRewardMsg, bagAchvMsg, specialAchvMsg, buffAchvMsg)) {
				s.bonusMsg = NL + killAchvMsg + itemAchvMsg + attackAchvMsg + jobSkillAchvMsg
						 + shopSellAchvMsg + potionAchvMsg + achvRewardMsg + bagAchvMsg + specialAchvMsg + buffAchvMsg;
			}
		}

		s.bagDropMsg = tryDropBag(s.userName, s.roomName, s.m, s.nightmare, s.hell, s.buff);
		if (s.thiefDoubleAtk && s.m != null) {
			String bag2 = tryDropBag(s.userName, s.roomName, s.m, s.nightmare, s.hell, s.buff);
			if (bag2 != null && !bag2.isEmpty())
				s.bagDropMsg = (s.bagDropMsg == null || s.bagDropMsg.isEmpty()) ? bag2 : s.bagDropMsg + NL + bag2;
		}
	}

	// ─ 15~16) 메시지 구성 + 포인트 ────────────────────────────────
	private String ma_buildMessage(AttackSession s) {
		StringBuilder mid    = new StringBuilder();
		StringBuilder hunter = new StringBuilder();
		StringBuilder bot    = new StringBuilder();

		if (s.dmg.dmgCalcMsg != null && !s.dmg.dmgCalcMsg.isEmpty()) mid.append(s.dmg.dmgCalcMsg);
		if (s.dmg.hunterMsg  != null && !s.dmg.hunterMsg.isEmpty())  hunter.append(s.dmg.hunterMsg);
		if (s.dosabuffMsg    != null && !s.dosabuffMsg.isEmpty())     mid.append(NL).append(s.dosabuffMsg);
		if (s.dosaCastMsg    != null && !s.dosaCastMsg.isEmpty())     bot.append(NL).append(s.dosaCastMsg);
		if (s.thiefDoubleAtk && s.calc2 != null) {
			bot.append(NL).append("⚔️2타 데미지: ").append(formatWan(s.calc2.atkDmg));
			if (s.dmg2 != null && s.dmg2.flags != null && s.dmg2.flags.atkCrit) bot.append(" ✨크리!");
		}
		if (s.stealMsg != null && !s.stealMsg.isEmpty()) bot.append(NL).append(s.stealMsg);

		StringBuilder detailOut = new StringBuilder();
		String msg = buildAttackMessage(s.userName, s.u, s.m, s.flags, s.calc, s.res, s.up,
				s.monHpRemainBefore, s.monMaxHp, s.effAtkMin, s.effAtkMax, s.hpMax,
				mid.toString(), hunter.toString(), bot.toString(), s.nightmare, s.ctx, detailOut);

		if (!s.bonusMsg.isEmpty()) msg += s.bonusMsg;

		String curSpStr = "";
		try {
			HashMap<String,Object> pointRow = botNewService.selectCurrentPoint(s.userName, s.roomName);
			double cv = Double.parseDouble(Objects.toString(pointRow.get("SCORE"), "0"));
			String  ce = Objects.toString(pointRow.get("SCORE_EXT"), "");
			curSpStr = new SP(cv, ce).toString();
		} catch (Exception ignore) {}

		if (!s.stealPoint.isEmpty()) { msg += "✨추가획득" + s.stealPoint; if (!s.stealBonus.isEmpty()) msg += s.stealBonus; msg += NL; }
		if (!s.newPoint.isEmpty())   { msg += "✨전투획득" + s.newPoint;   if (!s.newBonus.isEmpty())   msg += s.newBonus;   msg += NL; }
		msg += "✨포인트: " + curSpStr;

		if (s.bagDropMsg != null && !s.bagDropMsg.isEmpty()) msg += NL + s.bagDropMsg;
		if (s.buff.started)                msg += NL + s.buff.startMsg;
		else if (s.buff.runningMsg != null) msg += NL + s.buff.runningMsg;

		try {
			botNewService.execSPMsgTest(s.map);
			msg += NL + Objects.toString(s.map.get("outMsg"), "");
		} catch (Exception e) { e.printStackTrace(); }

		// 헬모드 유저에게 헬보스 출현 알림
		if (s.hell) {
			String hellNotify = bossAttackS3Controller.getHellBossStatusMsg();
			if (hellNotify != null && !hellNotify.isEmpty()) msg += NL + hellNotify;
		}

		// 더보기(===) — 데미지 계산식 / 헌터랭크 / 연사계산 / 레벨업 상세
		if (detailOut.length() > 0) {
			msg += NL + ALL_SEE_STR + NL + detailOut.toString();
		}

		return msg;
	}

	// ─ [COMPACT] 사망 축약 메시지 래퍼 ───────────────────────────────
	private String ma_deathMsgShort(AttackSession s, String fullDeathMsg) {
		// 공지 조회 (ma_buildMessage 를 거치지 않으므로 직접 호출)
		String noticeStr = "";
		try {
			botNewService.execSPMsgTest(s.map);
			noticeStr = Objects.toString(s.map.get("outMsg"), "");
		} catch (Exception ignore) {}

		StringBuilder sb = new StringBuilder();
		// Line 1
		sb.append(s.userName).append("님! 전투 패배!").append(NL);
		// Line 2
		sb.append("5분 뒤 공격 가능!").append(NL);
		// Line 3: 공지
		if (!noticeStr.isEmpty()) sb.append(noticeStr).append(NL);

		sb.append(ALL_SEE_STR).append(NL);
		sb.append(fullDeathMsg);
		return sb.toString();
	}

	// ─ [COMPACT] 처치/진행중 축약 메시지 ────────────────────────────
	private String ma_buildMessageShort(AttackSession s) {
		// 원문 먼저 생성 (execSPMsgTest 포함)
		String fullMsg = ma_buildMessage(s);

		StringBuilder sb = new StringBuilder();
		int hpPct = s.hpMax > 0 ? (int) Math.round((double) s.u.hpCur / s.hpMax * 100) : 0;

		// Line 1: 유저님! ❤️체력: X%
		sb.append(s.userName).append("님! ❤️체력: ").append(hpPct).append("%").append(NL);

		// Line 2: ⚔ 데미지 X으로 처치! / 전투 진행중! (비처치 시 몬HP 인라인)
		sb.append("⚔ 데미지 ").append(String.format("%,d", s.calc.atkDmg));
		if (s.res.killed) {
			sb.append("으로 처치!");
		} else {
			sb.append("으로 전투 진행중!");
			int monHpAfter = Math.max(0, s.monHpRemainBefore - s.calc.atkDmg);
			sb.append(" [몬HP:").append(String.format("%,d", monHpAfter))
			  .append("/").append(String.format("%,d", s.monMaxHp)).append("]");
		}
		sb.append(NL);

		// Kill 전용: EXP / SP
		if (s.res.killed) {
			// Line 3: EXP (레벨업 포함)
			double gainPct = s.u.expNext > 0 ? (double) s.res.gainExp / s.u.expNext * 100 : 0;
			double curPct  = s.u.expNext > 0 ? (double) s.u.expCur    / s.u.expNext * 100 : 0;
			sb.append("EXP +").append(String.format("%.1f", gainPct)).append("%")
			  .append(" [").append(String.format("%.1f", curPct)).append("%/100%]");
			if (s.up != null && s.up.levelUpCount > 0) {
				sb.append(" ✨Lv").append(s.up.beforeLv).append("→").append(s.up.afterLv);
			}
			sb.append(NL);

			// Line 4: SP 획득량 + 현재 잔액
			try {
				HashMap<String,Object> pointRow = botNewService.selectCurrentPoint(s.userName, s.roomName);
				double cv = Double.parseDouble(Objects.toString(pointRow.get("SCORE"), "0"));
				String  ce = Objects.toString(pointRow.get("SCORE_EXT"), "");
				String curSpStr = new SP(cv, ce).toString();
				String gainStr  = s.newPoint.trim(); // " +0.13b" → "+0.13b"
				if (!gainStr.isEmpty()) {
					sb.append("✨SP: ").append(gainStr).append(" [ ").append(curSpStr).append("]");
				} else {
					sb.append("✨SP: ").append(curSpStr);
				}
			} catch (Exception ignore) {}
			sb.append(NL);
		}

		// Line 5: 업적달성! + 가방획득! (스페셜타임은 별도 줄)
		StringBuilder line5 = new StringBuilder();
		if (s.bonusMsg  != null && !s.bonusMsg.isEmpty())  line5.append("업적달성!");
		if (s.bagDropMsg != null && !s.bagDropMsg.isEmpty()) line5.append("가방획득!");
		if (line5.length() > 0) sb.append(line5).append(NL);

		// Line 6: 스페셜타임![효과 설명] (단독 줄)
		if (s.activeBuff != null && !s.activeBuff.isEmpty()) {
			String flagCode   = Objects.toString(s.activeBuff.get("FLAG_CODE"),   "");
			String effectType = Objects.toString(s.activeBuff.get("EFFECT_TYPE"), "");
			double effectVal  = 0;
			try { effectVal = Double.parseDouble(Objects.toString(s.activeBuff.get("EFFECT_VALUE"), "0")); } catch (Exception ignore) {}
			String desc = buildBuffDescription(flagCode, effectType, effectVal);
			sb.append("스페셜타임![").append(desc).append("]").append(NL);
		}

		// Line 7: 공지
		String noticeStr = Objects.toString(s.map.get("outMsg"), "");
		if (!noticeStr.isEmpty()) sb.append(noticeStr).append(NL);

		// === + 원문
		sb.append(ALL_SEE_STR).append(NL);
		sb.append(fullMsg);
		return sb.toString();
	}

	/** isAnyNonEmpty 헬퍼 */
	private static boolean isAnyNonEmpty(String... strs) {
		for (String s : strs) if (s != null && !s.isEmpty()) return true;
		return false;
	}
	/** 기존 호환용 오버로드 — 보너스 설명 불필요한 호출에서 사용 */
	public String baroSellItem(String dropName,Integer itemId,Resolve res,String userName,String roomName,UserBattleContext ctx,User u,String gainType,int qty,boolean nightmare) {
	    return baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,gainType,qty,nightmare,null,null);
	}

	/** 11-param 호환 오버로드 */
	public String baroSellItem(String dropName,Integer itemId,Resolve res,String userName,String roomName,UserBattleContext ctx,User u,String gainType,int qty,boolean nightmare,String[] outBonus) {
	    return baroSellItem(dropName,itemId,res,userName,roomName,ctx,u,gainType,qty,nightmare,outBonus,null);
	}

	/**
	 * SP 계산 + 보너스 설명 반환. INSERT는 수행하지 않음. 호출부에서 outSp를 누적 후 단건 INSERT 처리.
	 * @param outBonus null 허용. null이 아니면 outBonus[0]에 보너스 설명 문자열을 설정.
	 * @param outSp    null 허용. null이 아니면 outSp[0]에 계산된 SP 객체를 설정.
	 */
	public String baroSellItem(String dropName,Integer itemId,Resolve res,String userName,String roomName,UserBattleContext ctx,User u,String gainType,int qty,boolean nightmare,String[] outBonus,SP[] outSp) {
	    String newPoint="";
	    try {
	        if(0 == itemId) {
	            itemId = getItemIdCached(dropName);
	        }
	        HashMap<String,Object> priceRow = getItemPriceCached(itemId);
	        double basePrice = safeDouble(priceRow == null ? null : priceRow.get("ITEM_SELL_PRICE"));
	        if (basePrice > 0) {
	            double gainSp = basePrice;
	            StringBuilder bonusDesc = new StringBuilder();

	            if("STEAL".equals(gainType)) {
	                gainSp /= 2;
	                gainSp *= qty;
	            }

	            if(!"STEAL".equals(gainType)) {

	                if ("9".equals(res.dropCode)) {
	                    gainSp *= 9;
	                }

	                if ("3".equals(res.dropCode) || "5".equals(res.dropCode)) {
	                    gainSp *= 5;
	                }

	                if ("2".equals(res.dropCode)) {
	                    gainSp *= 2;
	                }
	            }

	            if(nightmare) {
	                if(u.nightmareYn == 2) {
	                    gainSp *= 50*HEL_SP_MULT; // 헬: 토끼(10sp) * 5000000 = 5000a
	                } else {
	                    gainSp *= 50; // 나메
	                }
	            }

	            if(SP.parse(ctx.lifetimeSpStr).lessThan(new SP(100,"b"))){
	            	gainSp *= 3;
	            	bonusDesc.append("(100b 이하 3배)");
	            }

	            if ("용사".equals(ctx.job)) {
	                gainSp *= 3;
	                bonusDesc.append("(용사 2배)");
	                if (ThreadLocalRandom.current().nextDouble() < 0.10) {
	                    gainSp *= 3;
	                    bonusDesc.append("(크리! ×3)");
	                }
	            }

	            // outBonus / outSp 설정
	            if (outBonus != null && outBonus.length > 0) {
	                outBonus[0] = bonusDesc.toString();
	            }

	            // --------------------------
	            // SP 스페셜버프 적용
	            HashMap<String,Object> _spBuff = SPECIAL_BUFF_CACHE;
	            if (_spBuff != null && "SP".equals(_spBuff.get("FLAG_CODE"))) {
	                double buffPct = Double.parseDouble(_spBuff.get("EFFECT_VALUE").toString());
	                gainSp *= (1 + buffPct / 100.0);
	                bonusDesc.append("(SP버프 +").append((int)buffPct).append("%)");
	            }

	            // SP 변환
	            // --------------------------

	            SP gain = SP.fromSp(gainSp);

	            // INSERT는 호출부에서 처리 — 여기서는 outSp에만 세팅
	            if (outSp != null && outSp.length > 0) {
	                outSp[0] = gain;
	            }

	            newPoint = gain.toString();
	        }

	    } catch (Exception ignore) {}

	    return newPoint;
	}
	
	
	public String patchNote(HashMap<String,Object> map) {
		String msg ="";
		try {
			botNewService.execSPPatchNoteTest(map);
			msg += Objects.toString(map.get("outMsg"), "");
	    } catch (Exception e) {
	    	msg ="";
	        e.printStackTrace();
	    }
		return msg;
	}

	private SpecialBuffResult handleSpecialBuff(String userName) {

	    SpecialBuffResult result = new SpecialBuffResult();

	    // 🔹 1. 현재 활성 버프 조회 — [FIX4] 15초 단기 캐시로 DB 조회 절감
	    HashMap<String,Object> activeBuff;
	    long nowMs = System.currentTimeMillis();
	    if (nowMs - SPECIAL_BUFF_CACHE_TS < SPECIAL_BUFF_CACHE_TTL_MS) {
	        activeBuff = SPECIAL_BUFF_CACHE;
	    } else {
	        activeBuff = botNewService.selectActiveSpecialBuff();
	        SPECIAL_BUFF_CACHE = activeBuff;
	        SPECIAL_BUFF_CACHE_TS = nowMs;
	    }

	    // 🔹 2. 활성 버프 없으면 → 확률 발동 시도
	    if (activeBuff == null) {

	        double chance = 0.03;

	        if (ThreadLocalRandom.current().nextDouble() < chance) {

	        	SpecialBuffOption selected = MiniGameUtil.pickWeightedBuff(MiniGameUtil.SPECIAL_BUFF_OPTIONS);

	        	String flagCode = selected.flagCode;
	        	String effectType = selected.effectType;
	        	double effectValue;
	        	int durationMin;

	        	switch (flagCode) {
	        	    case "가방":
	        	        effectValue = ThreadLocalRandom.current().nextInt(3, 8); //3~7
	        	        durationMin = randomDuration(effectValue);
	        	        break;

	        	    case "공격력":
	        	        effectValue = Math.round((1.1 + ThreadLocalRandom.current().nextDouble() * 0.4) * 100.0) / 100.0;
	        	        durationMin = randomDuration(effectValue);
	        	        break;

	        	    case "치피":
	        	        effectValue = ThreadLocalRandom.current().nextInt(100, 501);
	        	        durationMin = randomDuration(effectValue);
	        	        break;

	        	    case "치확":
	        	        effectValue = ThreadLocalRandom.current().nextInt(50, 301); // 예: 5~20
	        	        durationMin = randomDuration(effectValue);
	        	        break;
	        	        
	        	    case "회복":
	        	        effectValue = ThreadLocalRandom.current().nextInt(30, 101); // 예: 5~20
	        	        durationMin = 4;
	        	        break;
	        	        
	        	    case "쿨타임":
	        	        effectValue = 1;
	        	        durationMin = 10;
	        	        break;

	        	    case "쿨타임감소":
	        	        effectValue = 2; // 배율 2 = 50% 감소
	        	        durationMin = 10;
	        	        break;

	        	    case "SP":
	        	        effectValue = ThreadLocalRandom.current().nextInt(30, 101); // 30~100%
	        	        durationMin = randomDuration(effectValue / 30.0);
	        	        break;

	        	    case "경험치":
	        	        effectValue = ThreadLocalRandom.current().nextInt(50, 301); // 50~300%
	        	        durationMin = randomDuration(effectValue / 30.0);
	        	        break;

	        	    case "나메가방":
	        	        effectValue = 1;
	        	        durationMin = 3;
	        	        break;

	        	    default:
	        	        return result;
	        	}
	            
	            

	            HashMap<String,Object> param = new HashMap<>();
	            param.put("flagCode", flagCode);
	            param.put("effectType", effectType);
	            param.put("effectValue", effectValue);
	            param.put("durationMin", durationMin);
	            param.put("insertId", userName);

	            botNewService.insertSpecialBuff(param);

	            // 🔥 새로 발동했으니 activeBuff 재구성 (메모리상) + [FIX4] 캐시 무효화
	            activeBuff = new HashMap<>();
	            activeBuff.put("FLAG_CODE", flagCode);
	            activeBuff.put("EFFECT_TYPE", effectType);
	            activeBuff.put("EFFECT_VALUE", effectValue);

	            LocalDateTime now = LocalDateTime.now()
	                    .withSecond(0).withNano(0);
	            LocalDateTime end = now.plusMinutes(durationMin);

	            activeBuff.put("END_TIME",
	                    Date.from(end.atZone(ZoneId.systemDefault()).toInstant()));

	            String desc = buildBuffDescription(flagCode, effectType, effectValue);

	            result.started = true;
	            result.startMsg =
	                    "✨스페셜타임 발동! [" + desc + ", " + durationMin + "분]";
	            result.activeBuff = activeBuff;
	            SPECIAL_BUFF_CACHE = activeBuff;
	            SPECIAL_BUFF_CACHE_TS = System.currentTimeMillis();
	        }
	    }

	    // 🔹 3. 활성 버프가 있으면 진행 메시지 생성
	    if (activeBuff != null) {

	        String flagCode = (String) activeBuff.get("FLAG_CODE");
	        String effectType = (String) activeBuff.get("EFFECT_TYPE");
	        double effectValue =
	                Double.parseDouble(activeBuff.get("EFFECT_VALUE").toString());

	        Date endTime = (Date) activeBuff.get("END_TIME");

	        LocalDateTime end = endTime.toInstant()
	                .atZone(ZoneId.systemDefault())
	                .toLocalDateTime();

	        String endStr = end.format(DateTimeFormatter.ofPattern("HH:mm"));

	        String desc = buildBuffDescription(flagCode, effectType, effectValue);

	        result.runningMsg =
	                "✨스페셜타임 진행중! [" + desc + ", " + endStr + "까지]";
	        result.activeBuff = activeBuff;
	    }

	    return result;
	}

	/** 현재 스페셜타임 진행중 메시지 반환 (없으면 빈 문자열). S3Controller 등 외부 호출용. */
	public String getActiveSpecialTimeMsg() {
	    try {
	        long nowMs = System.currentTimeMillis();
	        HashMap<String,Object> activeBuff = (nowMs - SPECIAL_BUFF_CACHE_TS < SPECIAL_BUFF_CACHE_TTL_MS)
	                ? SPECIAL_BUFF_CACHE : botNewService.selectActiveSpecialBuff();
	        if (activeBuff == null) return "";
	        String fc  = Objects.toString(activeBuff.get("FLAG_CODE"), "");
	        String et  = Objects.toString(activeBuff.get("EFFECT_TYPE"), "");
	        double ev  = activeBuff.get("EFFECT_VALUE") != null
	                ? Double.parseDouble(activeBuff.get("EFFECT_VALUE").toString()) : 0;
	        java.util.Date endT = (java.util.Date) activeBuff.get("END_TIME");
	        String endStr = endT != null
	                ? endT.toInstant().atZone(ZoneId.systemDefault())
	                    .toLocalDateTime()
	                    .format(DateTimeFormatter.ofPattern("HH:mm"))
	                : "?";
	        return "✨스페셜타임 진행중! [" + buildBuffDescription(fc, et, ev) + ", " + endStr + "까지]";
	    } catch (Exception ignore) {
	        return "";
	    }
	}
	
	private int randomDuration(double effectValue) {

	    int min = 4;
	    int max = 20;

	    double normalized = Math.min(effectValue / 10.0, 1.0);

	    int adjustedMax = (int)(max - (normalized * 8));

	    return ThreadLocalRandom.current()
	            .nextInt(min, Math.max(min + 1, adjustedMax + 1));
	}

	private String buildBuffDescription(String flagCode, String effectType, double effectValue) {

		if ("가방".equals(flagCode)) {
			return "가방확률 " + (int) effectValue + "배";
		}
		
		if ("나메가방".equals(flagCode)) {
			return "나메가방확정 타임";
		}

		if ("공격력".equals(flagCode)) {
			return "공격력 " + Math.round((effectValue - 1) * 100) + "% 증가";
		}

		if ("SP".equals(flagCode)) {
			return "SP획득 +" + (int) effectValue + "%";
		}

		if ("치피".equals(flagCode)) {
			return "치명타피해 +" + (int) effectValue + "%";
		}
		if ("회복".equals(flagCode)) {
			return "HP회복 +" + (int) effectValue + "%";
		}
		if ("치확".equals(flagCode)) {
			return "치명타확률 +" + (int) effectValue + "%";
		}
		
		if ("쿨타임".equals(flagCode)) {
			return "공격쿨타임 +" + (int) effectValue + "분 감소";
		}

		if ("쿨타임감소".equals(flagCode)) {
			return "공격쿨타임 50% 감소";
		}

		if ("경험치".equals(flagCode)) {
			return "경험치획득 +" + (int) effectValue + "%";
		}

		return flagCode;
	}
	
	
	private double computeBagPityMultiplier(String userName, String roomName,double buffRate) {
		double rtn_value = 1;
		int bagCountToday = 0;

	    try {
	        bagCountToday = botNewService.selectTodayBagCount(userName);
	    } catch (Exception ignore) {}

	    // 🔹 일일 획득량 기반 확률 보정
	    if (bagCountToday < 3) {
	    	rtn_value *= 3.0;     // drop *3
	    }
	    else if (bagCountToday < 5) {
	    	rtn_value *= 2.0;     // drop *2
	    }
	    else if (bagCountToday < 10) {
	    	rtn_value *= 1.5;     // drop *1.5
	    }
	    else {
	    	rtn_value *= 0;
	    }
		
	    if(buffRate >0) {
	    	rtn_value *= buffRate; 
	    }
	    
	    // 기본값: 보정 없음
	    return rtn_value;
	}
	
	
	private String tryDropBag(String userName, String roomName, Monster m, boolean nightmare, boolean hell, SpecialBuffResult buff) {
	    double buffRate = 0.0;
	    boolean forceNmBagDrop = false;

	    // 버프 발동 시: 어떤 버프든 발동자는 나메가방 확정 (의도된 기능)
	    if (buff != null && buff.started) {
	        forceNmBagDrop = true;
	    }

	    try {
	        HashMap<String,Object> activeBuff = (buff != null) ? buff.activeBuff : null;
	        if (activeBuff != null) {
	            String flagCode = String.valueOf(activeBuff.get("FLAG_CODE"));
	            Object effectValueObj = activeBuff.get("EFFECT_VALUE");

	            if ("가방".equals(flagCode)) {
	                if (effectValueObj != null) {
	                    buffRate = Double.parseDouble(String.valueOf(effectValueObj));
	                }
	            } else if ("나메가방".equals(flagCode)) {
	                forceNmBagDrop = true;
	            }
	        }
	    } catch (Exception ignore) {
	    }

	    // 헬모드 + 헬상자 10개 이상 → pity 무시, 1% 고정으로 나메가방
	    if (hell && !forceNmBagDrop) {
	        int hellBagCount = 0;
	        try { hellBagCount = botNewService.selectBagCountByItemId(userName, roomName, BAG_HELL_ITEM_ID); } catch (Exception ignore) {}
	        if (hellBagCount >= 10) {
	            if (ThreadLocalRandom.current().nextDouble() >= 0.01) return ""; // 1% 고정
	            forceNmBagDrop = true; // 나메가방 확정
	            hell = false;          // 아래 hell 분기 스킵
	        }
	    }

	    // 강제 드랍이 아닐 때만 확률 계산
	    if (!forceNmBagDrop) {
	        double pityMul = computeBagPityMultiplier(userName, roomName, buffRate);
	        double finalRate = BAG_DROP_RATE * pityMul;

	        if (ThreadLocalRandom.current().nextDouble() >= finalRate) {
	            return "";
	        }
	    }

	    int bagItemId = BAG_ITEM_ID;
	    if (forceNmBagDrop) {
	        // 스페셜타임(나메가방 확정) → 헬모드여도 나메가방 우선
	        bagItemId = BAG_NM_ITEM_ID;
	    } else if (hell) {
	        // 헬모드: 보유 10개 미만 → 지옥의유물상자 (10개 이상은 위에서 처리)
	        bagItemId = BAG_HELL_ITEM_ID;
	    } else if (nightmare && ThreadLocalRandom.current().nextDouble() < 0.20) {
	        bagItemId = BAG_NM_ITEM_ID;
	    }
	    
	    try {
	        HashMap<String, Object> inv = new HashMap<>();
	        inv.put("userName", userName);
	        inv.put("roomName", roomName);
	        inv.put("itemId", bagItemId);
	        inv.put("qty", 1);
	        inv.put("delYn", "0");
	        inv.put("gainType", "BAG_DROP");

	        botNewService.insertInventoryLogTx(inv);
	        invalidateInvBuff(userName);

	        String bagName = (bagItemId == BAG_HELL_ITEM_ID) ? "지옥의유물상자" : (bagItemId == BAG_NM_ITEM_ID) ? "복주머니가방" : "세티노의비밀가방";

	        // ── 가방 보유 한도 초과 시 자동 오픈 ──────────────────────────────
	        try {
	            int totalBags = botNewService.selectBagCountByItemId(userName, roomName, BAG_ITEM_ID)
	                          + botNewService.selectBagCountByItemId(userName, roomName, BAG_NM_ITEM_ID)
	                          + botNewService.selectBagCountByItemId(userName, roomName, BAG_HELL_ITEM_ID);
	            if (totalBags >= BAG_MAX_HOLD) {
	                botNewService.consumeBagBulkByItemIdTx(userName, roomName, bagItemId, 1);
	                SP autoSP = new SP(0, "");
	                List<String> autoDetail = new ArrayList<>();
	                List<String> autoItems  = new ArrayList<>();
	                int autoSpMode = (bagItemId == BAG_NM_ITEM_ID) ? 1 : 0;
	                if (bagItemId == BAG_HELL_ITEM_ID) {
	                    openHellBag(userName, roomName, 1, autoSP, autoDetail, autoItems);
	                } else {
	                    processBagOpen(bagItemId, 1, autoSpMode, userName, roomName, autoSP, autoDetail, autoItems);
	                }
	                HashMap<String,Object> pr = new HashMap<>();
	                pr.put("userName",  userName);
	                pr.put("roomName",  roomName);
	                pr.put("score",     autoSP.getValue());
	                pr.put("scoreExt",  autoSP.getUnit());
	                String autoCmd = (bagItemId == BAG_HELL_ITEM_ID) ? "HELL_BOX_SP"
	                               : (bagItemId == BAG_NM_ITEM_ID)   ? "BAG_OPEN_NM_SP"
	                               :                                    "BAG_OPEN_SP";
	                pr.put("cmd",       autoCmd);
	                botNewService.insertPointRank(pr);
	                invalidateInvBuff(userName);
	                StringBuilder autoMsg = new StringBuilder();
	                autoMsg.append(m.monName).append("이(가) ").append(bagName).append("을 떨어뜨렸습니다!").append(NL);
	                autoMsg.append("가방 한도(").append(BAG_MAX_HOLD).append("개) 초과 → 자동 오픈!").append(NL);
	                autoMsg.append("✨ 획득: ").append(autoSP);
	                if (!autoItems.isEmpty()) autoMsg.append(" / 아이템: ").append(String.join(", ", autoItems));
	                return autoMsg.toString();
	            }
	        } catch (Exception ignore) {}

	        return m.monName + "이(가) " + bagName + "을 떨어뜨렸습니다! (/가방열기 로 열 수 있습니다.)";
	    } catch (Exception e) {
	        return "";
	    }
	}

	
	public String sellItem(HashMap<String, Object> map) throws Exception {

	    String userName = Objects.toString(map.get("userName"), "");
	    String roomName = Objects.toString(map.get("roomName"), "");
	    String itemNameRaw = Objects.toString(map.get("param1"), "").trim();
	    int reqQty = Math.max(1, MiniGameUtil.parseIntSafe(Objects.toString(map.get("param2"), "1")));

	    String roomCheck = checkRoomPermission(userName, roomName);
	    if (roomCheck != null) return roomCheck;

	    if (itemNameRaw.isEmpty()) {
	        return "판매할 아이템명을 입력해주세요." + NL + "예) /판매 도토리 5";
	    }

	    if (!botNewService.tryAcquireUserActionLock(userName)) {
	        return "⏳ 처리 중입니다. 잠시 후 다시 시도해 주세요.";
	    }
	    try {
	        // 콤마 기반 다중 판매
	        if (itemNameRaw.contains(",")) {
	            return sellMultiItems(userName, roomName, itemNameRaw);
	        }

	        String slotResult = checkSlotSell(userName, roomName, itemNameRaw);
	        if (slotResult != null) return slotResult;

	        Integer itemId = resolveItemId(itemNameRaw);

	        // 300/500/600/900번대 판매 불가
	        if (itemId != null &&
	            ((itemId >= 300 && itemId < 400) || (itemId >= 500 && itemId < 700) || (itemId >= 900 && itemId < 1000))) {
	            return "[" + itemNameRaw + "]은(는) 판매할 수 없는 아이템입니다.";
	        }

	        if (itemId == null) {
	            return "해당 아이템을 찾을 수 없습니다: " + itemNameRaw;
	        }

	        List<HashMap<String,Object>> rows =
	                botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	        itemNameRaw = resolveItemLabel(itemNameRaw);

	        if (rows == null || rows.isEmpty()) {
	            return "인벤토리에 보유 중인 [" + itemNameRaw + "]이(가) 없습니다.";
	        }

	        SellResult result = executeSell(
	                userName,
	                roomName,
	                rows,
	                null,
	                reqQty,
	                false
	        );

	        return buildSellMessage(userName, itemNameRaw, reqQty, result);
	    } finally {
	        botNewService.releaseUserActionLock(userName);
	    }
	}

	// 콤마 기반 다중 판매: PK 충돌 방지를 위해 insertPointRank 를 단 1회만 호출
	private String sellMultiItems(String userName, String roomName, String raw) throws Exception {
	    String[] tokens = raw.split(",");

	    StringBuilder sb = new StringBuilder("▶ 일괄 판매 결과").append(NL);

	    MULTI_SELL_TOTAL_TL.set(new SP[]{SP.of(0, "")});
	    int totalSold = 0;
	    int totalGp = 0;
	    try {
	        for (String t : tokens) {
	            String token = (t == null ? "" : t.trim());
	            if (token.isEmpty()) continue;

	            int qty = Integer.MAX_VALUE;
	            String itemToken = token;
	            java.util.regex.Matcher m =
	                java.util.regex.Pattern.compile("(.+?)[xX\\*](\\d+)$").matcher(token);
	            if (m.matches()) {
	                itemToken = m.group(1).trim();
	                qty = Math.max(1, MiniGameUtil.parseIntSafe(m.group(2)));
	            }

	            String label = resolveItemLabel(itemToken);
	            sb.append(NL).append("[").append(label).append("]").append(NL);

	            // 카테고리 키워드 체크
	            String slotKey = MiniGameUtil.SLOT_MAP.get(itemToken);
	            List<HashMap<String,Object>> rows;
	            boolean sellAll;
	            String slotFilter;
	            if (slotKey != null) {
	                rows = botNewService.selectAllInventoryRowsForSale(userName, roomName);
	                sellAll = true;
	                slotFilter = slotKey;
	            } else {
	                Integer itemId;
	                try { itemId = resolveItemId(itemToken); } catch (Exception e) { itemId = null; }
	                if (itemId == null) { sb.append("아이템을 찾을 수 없습니다.").append(NL); continue; }
	                if ((itemId >= 300 && itemId < 400) || (itemId >= 500 && itemId < 700) || (itemId >= 900 && itemId < 1000)) {
	                    sb.append("판매 불가 아이템입니다.").append(NL); continue;
	                }
	                rows = botNewService.selectInventoryRowsForSale(userName, roomName, itemId);
	                sellAll = (qty == Integer.MAX_VALUE);
	                slotFilter = null;
	            }

	            if (rows == null || rows.isEmpty()) { sb.append("보유 재고 없음").append(NL); continue; }

	            SellResult r = executeSell(userName, roomName, rows, slotFilter, qty, sellAll);
	            totalSold += r.sold;
	            totalGp += r.gpGranted;
	            if (r.sold > 0) sb.append("판매: ").append(r.sold).append("개 / ").append(r.total).append(NL);
	            else sb.append("판매 가능한 재고 없음").append(NL);
	        }

	        // 합산 금액 한 번에 차감
	        SP totalSp = MULTI_SELL_TOTAL_TL.get()[0];
	        if (totalSold > 0 && (totalSp.getValue() > 0 || totalSp.getUnit().length() > 0)) {
	            HashMap<String, Object> pr = new HashMap<>();
	            pr.put("userName", userName);
	            pr.put("roomName", roomName);
	            pr.put("score",    totalSp.getValue());
	            pr.put("scoreExt", totalSp.getUnit());
	            pr.put("cmd",      "SELL_EQUIP");
	            botNewService.insertPointRank(pr);
	        }
	    } finally {
	    	invalidateInvBuff(userName);
	        MULTI_SELL_TOTAL_TL.remove();
	    }

	    if (totalSold <= 0) return "판매 가능한 재고가 없습니다.";

	    SP curPoint = SP.of(0, "");
	    try {
	        HashMap<String,Object> p = botNewService.selectCurrentPoint(userName, null);
	        curPoint = new SP(Double.parseDouble(Objects.toString(p.get("SCORE"), "0")),
	                Objects.toString(p.get("SCORE_EXT"), ""));
	    } catch (Exception ignore) {}

	    sb.append(NL).append("- 총 판매 수량: ").append(totalSold).append("개");
	    if (totalGp > 0) sb.append(NL).append("- GP 획득: ").append(totalGp).append(" GP");
	    sb.append(NL).append("- 현재 포인트: ").append(curPoint);
	    return sb.toString();
	}

	public String gpGacha(HashMap<String, Object> map) {
		String userName = Objects.toString(map.get("userName"), "");
		String roomName = Objects.toString(map.get("roomName"), "");
		String roomCheck = checkRoomPermission(userName, roomName);
		if (roomCheck != null) return roomCheck;

		double gp;
		try { gp = botNewService.selectGpBalance(userName); }
		catch (Exception e) { return "GP 조회 중 오류가 발생했습니다."; }

		if (gp < 6) {
			return userName + "님," + NL + "보스뽑기에는 6 GP가 필요합니다." + NL
				+ "현재 GP: " + gp + " GP" + NL
				+ "(7000번대 보스 아이템 판매 시 1개당 1 GP 획득)";
		}

		// 7000번대 아이템 목록 조회
		List<Integer> bossItems;
		try { bossItems = botNewService.selectBossItemIds(); }
		catch (Exception e) { return "뽑기 목록 조회 중 오류가 발생했습니다."; }
		if (bossItems == null || bossItems.isEmpty())
			return "현재 뽑기 가능한 보스 아이템이 없습니다.";

		// 이미 보유한 아이템(어떤 경로든)은 가챠 풀에서 제외 (드랍템/가챠템 중복 방지)
		try {
			List<Integer> owned = botNewService.selectInventoryItemsByIds(userName, "", bossItems);
			if (owned != null && !owned.isEmpty()) {
				bossItems = new ArrayList<>(bossItems);
				bossItems.removeAll(new HashSet<>(owned));
			}
		} catch (Exception ignore) {}

		if (bossItems.isEmpty())
			return userName + "님," + NL + "현재 모든 보스 아이템을 보유 중입니다." + NL
				+ "잔여 GP: " + String.format("%.2f", gp) + " GP";

		// GP 6 차감
		try {
			HashMap<String, Object> gpDeduct = new HashMap<>();
			gpDeduct.put("userName", userName);
			gpDeduct.put("roomName", roomName);
			gpDeduct.put("score",   -6.0);
			gpDeduct.put("cmd",     "BOSS_GACHA");
			botNewService.insertGpRecord(gpDeduct);
		} catch (Exception e) { return "GP 차감 중 오류가 발생했습니다."; }

		// 랜덤 아이템 지급
		int giveItemId = bossItems.get(ThreadLocalRandom.current().nextInt(bossItems.size()));
		try {
			HashMap<String, Object> inv = new HashMap<>();
			inv.put("userName", userName);
			inv.put("roomName", roomName);
			inv.put("itemId",   giveItemId);
			inv.put("qty",      1);
			inv.put("gainType", "BOSS_GACHA");
			botNewService.insertInventoryLogTx(inv);
			invalidateInvBuff(userName); // 보스 가챠 아이템 획득
		} catch (Exception e) {
			// 지급 실패 시 GP 복구
			try {
				HashMap<String, Object> gpRestore = new HashMap<>();
				gpRestore.put("userName", userName);
				gpRestore.put("roomName", roomName);
				gpRestore.put("score",   6.0);
				gpRestore.put("cmd",     "BOSS_GACHA_RESTORE");
				botNewService.insertGpRecord(gpRestore);
			} catch (Exception ignore) {}
			return "아이템 지급 중 오류가 발생했습니다.";
		}

		// 아이템 이름/설명/옵션 조회
		String itemLine = "#" + giveItemId;
		try {
			HashMap<String, Object> detail = botNewService.selectItemDetailById(giveItemId);
			if (detail != null) {
				String iName = Objects.toString(detail.get("ITEM_NAME"), "#" + giveItemId);
				String iDesc = Objects.toString(detail.get("ITEM_DESC"), "");
				StringBuilder opts = new StringBuilder();
				int atkMin = detail.get("ATK_MIN") != null ? ((Number) detail.get("ATK_MIN")).intValue() : 0;
				int atkMax = detail.get("ATK_MAX") != null ? ((Number) detail.get("ATK_MAX")).intValue() : 0;
				int cri    = detail.get("ATK_CRI") != null ? ((Number) detail.get("ATK_CRI")).intValue() : 0;
				int criDmg = detail.get("CRI_DMG") != null ? ((Number) detail.get("CRI_DMG")).intValue() : 0;
				int hp     = detail.get("HP_MAX")  != null ? ((Number) detail.get("HP_MAX")).intValue()  : 0;
				int regen  = detail.get("HP_REGEN")!= null ? ((Number) detail.get("HP_REGEN")).intValue(): 0;
				int hpRate = detail.get("HP_MAX_RATE") != null ? ((Number) detail.get("HP_MAX_RATE")).intValue() : 0;
				int atkRate= detail.get("ATK_MAX_RATE")!= null ? ((Number) detail.get("ATK_MAX_RATE")).intValue(): 0;
				if (atkMin > 0 || atkMax > 0) opts.append(" ATK+").append(atkMin).append("~").append(atkMax);
				if (cri    > 0) opts.append(" 크리+").append(cri);
				if (criDmg > 0) opts.append(" 크리뎀+").append(criDmg);
				if (hp     > 0) opts.append(" HP+").append(hp);
				if (regen  > 0) opts.append(" 리젠+").append(regen);
				if (hpRate > 0) opts.append(" HP+").append(hpRate).append("%");
				if (atkRate> 0) opts.append(" ATK+").append(atkRate).append("%");
				itemLine = iName
					+ (!iDesc.isEmpty() ? " (" + iDesc + ")" : "")
					+ (opts.length() > 0 ? " [" + opts.toString().trim() + "]" : "");
			}
		} catch (Exception ignore) {}

		return userName + "님," + NL
				+ "보스뽑기! (-6 GP)" + NL
				+ "▶ 획득 아이템: " + itemLine + NL
				+ "- 잔여 GP: " + String.format("%.2f", gp - 6) + " GP";
	}

		private String sellCategoryItem(String userName, String roomName, String slotKey) throws Exception {

		List<HashMap<String, Object>> rows = botNewService.selectAllInventoryRowsForSale(userName, roomName);

		if (rows == null || rows.isEmpty()) {
		    return "인벤토리에 보유 중인 [" + slotKey + "]이(가) 없습니다.";
		}
		SellResult result = executeSell(userName, roomName, rows, slotKey, Integer.MAX_VALUE, true);

		return buildSellMessage(userName, slotKey, Integer.MAX_VALUE, result);
	}
	
	private String buildSellMessage(String userName, String name, int reqQty, SellResult r) {

		if (r.sold <= 0) {
			return "판매 가능한 재고가 없습니다.";
		}

		SP curPoint = new SP(0, "");

		try {

			HashMap<String, Object> p = botNewService.selectCurrentPoint(userName, null);

			curPoint = new SP(Double.parseDouble(Objects.toString(p.get("SCORE"), "0")),
					Objects.toString(p.get("SCORE_EXT"), ""));

		} catch (Exception ignore) {
		}

		StringBuilder sb = new StringBuilder();
		sb.append("⚔ ").append(userName).append("님,").append(NL)
		  .append("▶ 판매 완료!").append(NL)
		  .append("- 대상: ").append(name).append(NL)
		  .append("- 판매 수량: ").append(r.sold).append("개").append(NL);
		if (r.total.getValue() > 0)
			sb.append("- 합계 적립: ").append(r.total).append(NL);
		if (r.gpGranted > 0)
			sb.append("- GP 획득: ").append(r.gpGranted).append(" GP").append(NL);
		sb.append("- 현재 포인트: ").append(curPoint);
		return sb.toString();
	}
	
	private String checkRoomPermission(String userName, String roomName) {

	    if ("람쥐봇 문의방".equals(roomName) &&
	        !"일어난다람쥐/카단".equals(userName)) {

	        return "문의방에서는 불가능합니다.";
	    }

	    return null;
	}
	
	private String checkSlotSell(String userName, String roomName, String itemName) throws Exception {
		if (MiniGameUtil.SLOT_MAP.containsKey(itemName)) {
		    return sellCategoryItem(userName, roomName, MiniGameUtil.SLOT_MAP.get(itemName));
		}

	    return null;
	}
	
	public String refreshCache() {
	    MiniGameUtil.MONSTER_CACHE.clear();
	    MiniGameUtil.ACHV_GLOBAL_CACHE = null;
	    MiniGameUtil.ITEM_ID_CACHE.clear();
	    MiniGameUtil.ITEM_DETAIL_CACHE.clear();
	    MiniGameUtil.ITEM_PRICE_CACHE.clear();
	    MiniGameUtil.MARKET_OWNED_CACHE.clear();
	    MiniGameUtil.POTION_USE_CACHE.clear();
	    MiniGameUtil.INV_BUFF_CACHE.clear();
	    initCache();
	    return "✅ 캐시 갱신 완료" + NL
	         + "몬스터: " + MiniGameUtil.MONSTER_CACHE.size() + "건" + NL
	         + "아이템ID: " + MiniGameUtil.ITEM_ID_CACHE.size() + "건" + NL
	         + "아이템상세: " + MiniGameUtil.ITEM_DETAIL_CACHE.size() + "건" + NL
	         + "아이템가격: " + MiniGameUtil.ITEM_PRICE_CACHE.size() + "건" + NL
	         + "업적: " + (MiniGameUtil.ACHV_GLOBAL_CACHE != null ? MiniGameUtil.ACHV_GLOBAL_CACHE.size() : 0) + "건";
	}
	@PostConstruct
	public void initCache() {
	    try {
	        // 몬스터 전체 로드
	        List<Monster> monsters = botNewService.selectAllMonsters();
	        if (monsters != null) {
	            for (Monster m : monsters) {
	                MiniGameUtil.MONSTER_CACHE.put(m.monNo, m);
	            }
	        }
	        // 업적 글로벌 카운트 로드
	        MiniGameUtil.ACHV_GLOBAL_CACHE = botNewService.selectAchvCountsGlobalAll();
	        // 아이템ID 전체 로드
	        List<HashMap<String, Object>> items = botNewService.selectAllItemIdMappings();
	        if (items != null) {
	            for (HashMap<String, Object> row : items) {
	                String name = String.valueOf(row.get("ITEM_NAME"));
	                Object id   = row.get("ITEM_ID");
	                if (name != null && id != null) {
	                    MiniGameUtil.ITEM_ID_CACHE.put(name, ((Number) id).intValue());
	                }
	            }
	        }
	        // 아이템 상세 + 가격 전체 프리로드 (MARKET/POTION 포함 전체)
	        List<HashMap<String,Object>> marketItems = botNewService.selectMarketItems();
	        if (marketItems != null) {
	            for (HashMap<String,Object> row : marketItems) {
	                Object idObj = row.get("ITEM_ID");
	                if (idObj == null) continue;
	                int id2 = ((Number) idObj).intValue();
	                MiniGameUtil.ITEM_DETAIL_CACHE.put(id2, row);
	                HashMap<String,Object> priceRow = new HashMap<>();
	                priceRow.put("ITEM_SELL_PRICE",     row.get("ITEM_SELL_PRICE"));
	                priceRow.put("ITEM_SELL_PRICE_EXT", row.get("ITEM_SELL_PRICE_EXT"));
	                MiniGameUtil.ITEM_PRICE_CACHE.put(id2, priceRow);
	            }
	        }
	    } catch (Exception e) {
	        System.out.println("[initCache] 캐시 초기화 실패: " + e.getMessage());
	    }
	}
	// ─── 캐시 헬퍼 ─────────────────────────────────────────────────────────
	private Monster getMonsterCached(int monNo) {
	    Monster m = MiniGameUtil.MONSTER_CACHE.get(monNo);
	    if (m != null) return m;
	    m = botNewService.selectMonsterByNo(monNo);
	    if (m != null) MiniGameUtil.MONSTER_CACHE.put(monNo, m);
	    return m;
	}

	private List<AchievementCount> getAchvGlobalCached() {
	    if (MiniGameUtil.ACHV_GLOBAL_CACHE != null) {
	        return MiniGameUtil.ACHV_GLOBAL_CACHE;
	    }
	    List<AchievementCount> list = botNewService.selectAchvCountsGlobalAll();
	    MiniGameUtil.ACHV_GLOBAL_CACHE = list;
	    return list;
	}

	private Integer getItemIdCached(String itemName) {
	    if (itemName == null || itemName.isEmpty()) return null;
	    Integer cached = MiniGameUtil.ITEM_ID_CACHE.get(itemName);
	    if (cached != null) return cached;
	    try {
	        Integer id = botNewService.selectItemIdByName(itemName);
	        if (id != null) MiniGameUtil.ITEM_ID_CACHE.put(itemName, id);
	        return id;
	    } catch (Exception e) { return null; }
	}

	private HashMap<String,Object> getItemDetailCached(int itemId) {
	    HashMap<String,Object> cached = MiniGameUtil.ITEM_DETAIL_CACHE.get(itemId);
	    if (cached != null) return cached;
	    try {
	        HashMap<String,Object> item = botNewService.selectItemDetailById(itemId);
	        if (item != null) MiniGameUtil.ITEM_DETAIL_CACHE.put(itemId, item);
	        return item;
	    } catch (Exception e) { return null; }
	}

	private HashMap<String,Object> getItemPriceCached(int itemId) {
	    HashMap<String,Object> cached = MiniGameUtil.ITEM_PRICE_CACHE.get(itemId);
	    if (cached != null) return cached;
	    try {
	        HashMap<String,Object> price = botNewService.selectItemSellPriceById(itemId);
	        if (price != null) MiniGameUtil.ITEM_PRICE_CACHE.put(itemId, price);
	        return price;
	    } catch (Exception e) { return null; }
	}

	@SuppressWarnings("unchecked")
	private List<HashMap<String,Object>> getMarketItemsWithOwnedCached(String userName, String roomName) {
	    String key = userName + "|" + roomName;
	    Object[] entry = MiniGameUtil.MARKET_OWNED_CACHE.get(key);
	    long now = System.currentTimeMillis();
	    if (entry != null && (now - (long) entry[0]) < MiniGameUtil.MARKET_OWNED_TTL_MS) {
	        return (List<HashMap<String,Object>>) entry[1];
	    }
	    List<HashMap<String,Object>> list = botNewService.selectMarketItemsWithOwned(userName, roomName);
	    MiniGameUtil.MARKET_OWNED_CACHE.put(key, new Object[]{now, list});
	    return list;
	}

	// ─────────────────────────────────────────────────────────────────────────
	private Integer resolveItemId(String itemName) throws Exception {

	    if (itemName.matches("\\d+")) {
	        return Integer.valueOf(itemName);
	    }

	    return botNewService.selectItemIdByName(itemName);
	}

	private SellResult executeSell(String userName, String roomName, List<HashMap<String, Object>> rows, String slotKey,
			int reqQty, boolean sellAll) throws Exception {

		if (rows == null || rows.isEmpty()) {
			return SellResult.empty();
		}

		Set<Integer> itemIds = new HashSet<>(rows.size());

		for (HashMap<String, Object> r : rows) {
			int id = MiniGameUtil.parseIntSafe(Objects.toString(r.get("ITEM_ID"), "0"));
			if (id > 0)
				itemIds.add(id);
		}

		Map<String, Object> param = new HashMap<>();
		param.put("itemIds", itemIds);

		List<HashMap<String, Object>> priceRows = botNewService.selectItemSellPriceList(param);

		Map<Integer, Double> priceMap = new HashMap<>(itemIds.size());
		Map<Integer, String> extMap = new HashMap<>(itemIds.size());

		for (HashMap<String, Object> p : priceRows) {

			int id = MiniGameUtil.parseIntSafe(Objects.toString(p.get("ITEM_ID"), "0"));

			priceMap.put(id, safeDouble(p.get("ITEM_SELL_PRICE")));
			extMap.put(id, Objects.toString(p.get("ITEM_SELL_PRICE_EXT"), ""));
		}

		List<String> ridList = new ArrayList<>(rows.size());
		Map<Integer, String> catCache = new HashMap<>(itemIds.size());

		int sold = 0;
		SP total = SP.of(0, "");
		int gpCount = 0; // 7000번대 보스 아이템 판매 GP

		int need = reqQty;

		for (HashMap<String, Object> r : rows) {

			String rid = Objects.toString(r.get("RID"), null);
			if (rid == null)
				continue;

			int qty = MiniGameUtil.parseIntSafe(Objects.toString(r.get("QTY"), "0"));
			int itemId = MiniGameUtil.parseIntSafe(Objects.toString(r.get("ITEM_ID"), "0"));

			if (qty <= 0 || itemId <= 0)
				continue;

			if (slotKey != null) {

				String cat = catCache.computeIfAbsent(itemId, k -> resolveItemCategory(k));

				if (!slotKey.equals(cat))
					continue;
			}

			// [보스 아이템] 7000번대: BOSS_HELL 타입만 GP 판매 가능, BOSS_GACHA는 판매 불가
			if (itemId >= 7000 && itemId < 8000) {
				String gainType = Objects.toString(r.get("GAIN_TYPE"), "");
				if ("BOSS_GACHA".equalsIgnoreCase(gainType)) continue;
				int take2 = sellAll ? qty : Math.min(qty, need);
				if (take2 == qty) ridList.add(rid);
				else botNewService.updateInventoryQtyByRowId(rid, qty - take2);
				gpCount += take2;
				sold += take2;
				if (!sellAll) { need -= take2; if (need <= 0) break; }
				continue;
			}

			double price = priceMap.getOrDefault(itemId, 0d);
			if (price <= 0)
				continue;

			int take = sellAll ? qty : Math.min(qty, need);

			SP gain = SP.of(price, extMap.getOrDefault(itemId, "")).multiply(take);

			if (take == qty) {
				ridList.add(rid);
			} else {
				botNewService.updateInventoryQtyByRowId(rid, qty - take);
			}

			total = total.add(gain);
			sold += take;

			if (!sellAll) {
				need -= take;
				if (need <= 0)
					break;
			}
		}

		if (sold <= 0) {
			return SellResult.empty();
		}

		Map<String, Object> delParam = new HashMap<>();
		delParam.put("ridList", ridList);

		botNewService.updateInventoryDelBatch(delParam);

		// 다중판매 모드이면 ThreadLocal에 누적, 단건이면 즉시 처리
		SP[] sellTl = MULTI_SELL_TOTAL_TL.get();
		if (sellTl != null) {
		    sellTl[0] = sellTl[0].add(total);
		} else if (total.getValue() > 0 || total.getUnit().length() > 0) {
		    HashMap<String, Object> pr = new HashMap<>();
		    pr.put("userName", userName);
		    pr.put("roomName", roomName);
		    pr.put("score", total.getValue());
		    pr.put("scoreExt", total.getUnit());
		    pr.put("cmd", "SELL_EQUIP");
		    botNewService.insertPointRank(pr);
		}

		// GP 지급 (7000번대 보스 아이템 판매)
		if (gpCount > 0) {
			HashMap<String, Object> gp = new HashMap<>();
			gp.put("userName", userName);
			gp.put("roomName", roomName);
			gp.put("score",   (double) gpCount);
			gp.put("cmd",     "BOSS_SELL");
			try { botNewService.insertGpRecord(gp); } catch (Exception ignore) {}
		}

		return new SellResult(sold, total, gpCount);
	}

	public String showAttackRanking(HashMap<String,Object> map) {
	    final String NL = "♬";
	    final String allSeeStr = "===";

	    StringBuilder sb = new StringBuilder();

	    /* === 떠오르는샛별 (최근 6시간 공격횟수 TOP5) === */
	    List<HashMap<String,Object>> rising = botNewService.selectRisingStarsTop5Last6h();
	    sb.append("✨ 떠오르는샛별").append(NL);
	    if (rising == null || rising.isEmpty()) {
	        sb.append("- 데이터 없음").append(NL);
	    } else {
	        int rank = 1;
	        for (HashMap<String,Object> row : rising) {
	            String name = String.valueOf(row.get("USER_NAME"));
	            String job = String.valueOf(row.get("JOB"));
	            // 필요시 방 이름, 공격 횟수도 붙일 수 있음 (ex. " (12회)")
	            sb.append(rank).append("위 ").append(name);
	            
	            if(!"".equals(job)) {
	            	sb.append("(").append(job).append(")");
	            }
	            
	            sb.append(NL);
	            if (rank++ >= 9) break;
	        }
	    }
	    sb.append(NL);
	    
	    List<HashMap<String,Object>> maxs = botNewService.selectMaxDamageTop5();
	    
	    sb.append("✨ MAX 데미지 랭킹 (TOP5)").append(NL);
	    
	    if (maxs == null || maxs.isEmpty()) {
	    	sb.append("- 데이터 없음").append(NL);
	    } else {
	    	int rank = 1;
	    	for (HashMap<String,Object> row : maxs) {
	    		String max  = String.valueOf(row.get("MAX_DAMAGE"));
	    		String name = String.valueOf(row.get("USER_NAME"));
	    		
	    		sb.append("• ")
	    		.append(max)
	    		.append(" : ")
	    		.append(name)
	    		.append(NL);
	    		
	    		if (rank++ >= 5) break;
	    	}
	    }
	    
	    sb.append(allSeeStr);
	    
		 // =========================
		 // SP / 공격횟수 랭킹
		 // =========================
		 try {
		     List<HashMap<String, Object>> spAtkList = botNewService.selectSpAndAtkRanking();
		     sb.append(NL).append("◆ SP 누적 랭킹 (TOP5)").append(NL);
	
		     if (spAtkList == null || spAtkList.isEmpty()) {
		         sb.append("- 데이터가 없습니다.").append(NL);
		     } else {
		         List<HashMap<String, Object>> bySp = new ArrayList<>(spAtkList);
	
		         int rank = 1;
		         for (HashMap<String, Object> row : bySp) {
		             String userName2 = Objects.toString(row.get("USER_NAME"), "-");
		             int lv          = safeInt(row.get("LV"));
		             long totSp       = safeLong(row.get("TOT_SP"));
	
		             sb.append(rank).append("위 ")
		               .append(userName2)
		               .append(" (Lv.").append(lv).append(")")
		               .append(" - ").append(SP.fromSp(totSp))
		               .append(NL);
	
		             if (++rank > 5) break;
		         }
		     }
	
		     /*
		     sb.append(NL).append("◆ 공격 횟수 랭킹 (TOP10)").append(NL);
	
		     if (spAtkList == null || spAtkList.isEmpty()) {
		         sb.append("- 데이터가 없습니다.").append(NL);
		     } else {
		         // 공격 횟수 순위 정렬
		         List<HashMap<String, Object>> byAtk = new ArrayList<>(spAtkList);
		         byAtk.sort((a, b) -> Integer.compare(
		                 safeInt(b.get("ATK_CNT")),
		                 safeInt(a.get("ATK_CNT"))
		         ));
	
		         int rank = 1;
		         for (HashMap<String, Object> row : byAtk) {
		             String userName2 = Objects.toString(row.get("USER_NAME"), "-");
		             int lv          = safeInt(row.get("LV"));
		             int atkCnt      = safeInt(row.get("ATK_CNT"));
	
		             sb.append(rank).append("위 ")
		               .append(userName2)
		               .append(" (Lv.").append(lv).append(")")
		               .append(" - 공격 ").append(String.format("%,d", atkCnt)).append("회")
		               .append(NL);
	
		             if (++rank > 10) break;
		         }
		     }
		     */
		     
		 } catch (Exception ignore) {}
	    // =========================
	    // 업적 갯수 랭킹
	    // =========================
		 /*
	    try {
	        List<HashMap<String, Object>> achvRank = botNewService.selectAchievementCountRanking();
	        sb.append(NL).append("◆ 업적 갯수 랭킹 (TOP5)").append(NL);
	        if (achvRank == null || achvRank.isEmpty()) {
	            sb.append("- 데이터가 없습니다.").append(NL);
	        } else {
	            int rank = 1;
	            for (HashMap<String, Object> row : achvRank) {
	                String userName = Objects.toString(row.get("USER_NAME"), "-");
	                int cnt = MiniGameUtil.parseIntSafe(Objects.toString(row.get("ACHV_CNT"), "0"));
	                sb.append(rank).append("위 ").append(userName)
	                  .append(" - 업적 ").append(cnt).append("개").append(NL);
	                rank++;
	            }
	        }
	    } catch (Exception ignore) {}
	    */

    // =========================
    // GP 랭킹
    // =========================
		 
    try {
        List<HashMap<String, Object>> gpList = botNewService.selectGpRanking();
        sb.append(NL).append("◆ GP 랭킹 (보스뽑기: 6 GP)").append(NL);
        if (gpList == null || gpList.isEmpty()) {
            sb.append("- 데이터가 없습니다.").append(NL);
        } else {
            int rank = 1;
            for (HashMap<String, Object> row : gpList) {
                String uName    = Objects.toString(row.get("USER_NAME"), "-");
                double curGp    = row.get("CURRENT_GP")     != null ? ((Number)row.get("CURRENT_GP")).doubleValue()     : 0;
                double totalGp  = row.get("TOTAL_EARNED_GP") != null ? ((Number)row.get("TOTAL_EARNED_GP")).doubleValue() : 0;
                sb.append(rank).append("위 ").append(uName)
                  .append(" - 보유 ").append(String.format("%.2f", curGp)).append(" GP")
                  .append(" / 누적 ").append(String.format("%.2f", totalGp)).append(" GP")
                  .append(NL);
                if (++rank > 10) break;
            }
        }
    } catch (Exception ignore) {}

		  
	    sb.append(NL);
	    /* === ⚔ 몬스터 학살자 (전체) === */
	    /*
	    sb.append("⚔ 몬스터 학살자 (전체)").append(NL);
	    List<HashMap<String,Object>> killers = botNewService.selectKillLeadersByMonster();
	    if (killers == null || killers.isEmpty()) {
	        sb.append("데이터 없음").append(NL);
	    } else {
	        Integer lastMonNo = null;
	        for (HashMap<String,Object> k : killers) {
	            int monNo       = safeInt(k.get("MON_NO"));
	            String monName  = String.valueOf(k.get("MON_NAME"));
	            String uName    = String.valueOf(k.get("USER_NAME"));
	            int kills       = safeInt(k.get("KILL_COUNT"));

	            if (!java.util.Objects.equals(lastMonNo, monNo)) {
	            	sb.append(monNo).append(".No ").append(monName).append(" 학살자");
	                lastMonNo = monNo;
	            }
	            sb.append(" ▶ ").append(uName)
	              .append(" (").append(kills).append("마리)").append(NL);
	        }
	    }
	    */

	    /* === ⚔ 시즌 학살자 (이전 시즌 / 현재 시즌) === */
	    {
	        LocalDate today = LocalDate.now();
	        java.time.format.DateTimeFormatter yyyyMMdd = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
	        java.time.format.DateTimeFormatter mmdd     = java.time.format.DateTimeFormatter.ofPattern("M/d");

	        // 이전 시즌
	        LocalDate prevStart, prevEnd;
	        // 현재 시즌
	        LocalDate curStart, curEnd;

	        if (today.getDayOfMonth() >= 16) {
	            prevStart = today.withDayOfMonth(1);
	            prevEnd   = today.withDayOfMonth(15);
	            curStart  = today.withDayOfMonth(16);
	            curEnd    = today.withDayOfMonth(today.lengthOfMonth());
	        } else {
	            LocalDate prev = today.minusMonths(1);
	            prevStart = prev.withDayOfMonth(16);
	            prevEnd   = prev.withDayOfMonth(prev.lengthOfMonth());
	            curStart  = today.withDayOfMonth(1);
	            curEnd    = today.withDayOfMonth(15);
	        }

	        // 이전 시즌 출력
	        sb.append(NL).append("⚔ 학살자 [").append(prevStart.format(mmdd)).append("~").append(prevEnd.format(mmdd)).append(" 시즌]").append(NL);
	        try {
	            HashMap<String,Object> p = new HashMap<>();
	            p.put("seasonStart", prevStart.format(yyyyMMdd));
	            p.put("seasonEnd",   prevEnd.format(yyyyMMdd));
	            p.put("minKill", 100);
	            List<HashMap<String,Object>> prevKillers = botNewService.selectKillLeadersByMonsterSeason(p);
	            if (prevKillers == null || prevKillers.isEmpty()) {
	                sb.append("기록 없음").append(NL);
	            } else {
	                Integer last = null;
	                for (HashMap<String,Object> k : prevKillers) {
	                    int monNo      = safeInt(k.get("MON_NO"));
	                    String monName = String.valueOf(k.get("MON_NAME"));
	                    String uName   = String.valueOf(k.get("USER_NAME"));
	                    int kills      = safeInt(k.get("KILL_COUNT"));
	                    if (!java.util.Objects.equals(last, monNo)) {
	                        sb.append(monNo).append(".").append(monName).append(" 학살자");
	                        last = monNo;
	                    }
	                    sb.append(" ▶ ").append(uName).append(" (").append(kills).append("마리)").append(NL);
	                }
	            }
	        } catch (Exception ignore) { sb.append("조회 오류").append(NL); }

	        // 현재 시즌 출력
	        sb.append(NL).append("⚔ 학살자 [").append(curStart.format(mmdd)).append("~").append(curEnd.format(mmdd)).append(" 시즌 현황]").append(NL);
	        try {
	            HashMap<String,Object> p = new HashMap<>();
	            p.put("seasonStart", curStart.format(yyyyMMdd));
	            p.put("seasonEnd",   today.format(yyyyMMdd)); // 오늘까지만
	            p.put("minKill", 1);
	            List<HashMap<String,Object>> curKillers = botNewService.selectKillLeadersByMonsterSeason(p);
	            if (curKillers == null || curKillers.isEmpty()) {
	                sb.append("기록 없음").append(NL);
	            } else {
	                Integer last = null;
	                for (HashMap<String,Object> k : curKillers) {
	                    int monNo      = safeInt(k.get("MON_NO"));
	                    String monName = String.valueOf(k.get("MON_NAME"));
	                    String uName   = String.valueOf(k.get("USER_NAME"));
	                    int kills      = safeInt(k.get("KILL_COUNT"));
	                    if (!java.util.Objects.equals(last, monNo)) {
	                        sb.append(monNo).append(".").append(monName).append(" 학살자");
	                        last = monNo;
	                    }
	                    sb.append(" ▶ ").append(uName).append(" (").append(kills).append("마리)").append(NL);
	                }
	            }
	        } catch (Exception ignore) { sb.append("조회 오류").append(NL); }
	    }

	    sb.append(NL);
	    return sb.toString();
	}

	private String grantAchievementBasedReward(
	        String userName,
	        String roomName,
	        List<AchievementCount> achievements
	) {

	    if (achievements == null || achievements.isEmpty()) {
	        return "";
	    }

	    int achvCnt = 0;
	    for (AchievementCount ac : achievements) {
	        achvCnt += ac.getCnt();
	    }

	    StringBuilder msg = new StringBuilder();

	    try {

	        // 한번만 조회
	        List<Integer> ownedItems =
	                botNewService.selectInventoryItemsByIds(userName, roomName, MiniGameUtil.ACHV_REWARD_MAP.values());

	        Set<Integer> ownedSet = new HashSet<>(ownedItems);

	        for (Map.Entry<Integer, Integer> e : MiniGameUtil.ACHV_REWARD_MAP.entrySet()) {

	            int needCnt = e.getKey();
	            int itemId  = e.getValue();

	            if (achvCnt < needCnt) {
	                break; // LinkedHashMap이므로 이후도 필요 없음
	            }

	            if (ownedSet.contains(itemId)) {
	                continue;
	            }

	            HashMap<String,Object> inv = new HashMap<>();
	            inv.put("userName", userName);
	            inv.put("roomName", roomName);
	            inv.put("itemId", itemId);
	            inv.put("qty", 1);
	            inv.put("delYn", "0");
	            inv.put("gainType", "ACHV");

	            botNewService.insertInventoryLogTx(inv);
	            invalidateInvBuff(userName); // 업적 보상 아이템

	            msg.append("업적 ")
	               .append(needCnt)
	               .append("개 달성 보상 획득! (아이템_업적_")
	               .append(needCnt)
	               .append(")")
	               .append(NL);
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return msg.toString();
	}

	
	private String grantAttackCountAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        AttackDeathStat ads   // [PERF] 호출부에서 프리로드
	) {
	    if (ads == null) return "";

	    int totalAttacks = ads.totalAttacks;
	    if (totalAttacks <= 0) return "";

	    int[] thresholds = buildKillThresholds(AchievementConfig.KILL_TOTAL_MAX);

	    StringBuilder sb = new StringBuilder();

	    for (int th : thresholds) {
	        if (totalAttacks < th) break;

	        String cmd = "ACHV_ATTACK_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int rewardSp = calcTotalKillReward(th,false);

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, rewardSp, achievedCmdSet
	            )
	        );
	    }

	    return sb.toString();
	}



	private String grantJobSkillUseAchievementsAllJobs(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        List<HashMap<String,Object>> rows   // [PERF] 호출부에서 프리로드
	) {
	    if (rows == null || rows.isEmpty()) return "";

	    // 2️⃣ 공통 임계치 (AchievementConfig 중앙 관리)
	    final int[] thresholds = AchievementConfig.JOB_SKILL_THRESHOLDS;

	    StringBuilder sb = new StringBuilder();

	    // 3️⃣ 직업별 처리
	    for (HashMap<String,Object> row : rows) {
	        if (row == null) continue;

	        String jobName = Objects.toString(row.get("JOB"), "").trim();
	        if (jobName.isEmpty()) continue;

	        int totalSkillUse;
	        Object v = row.get("TOTAL_SKILL_USE");
	        if (v instanceof Number) {
	            totalSkillUse = ((Number) v).intValue();
	        } else {
	            totalSkillUse = MiniGameUtil.parseIntSafe(Objects.toString(v, "0"));
	        }

	        if (totalSkillUse <= 0) continue;

	        // 4️⃣ 임계치 달성 여부만 체크 (DB 조회 ❌)
	        for (int th : thresholds) {
	            if (totalSkillUse < th) break; // 정렬 가정 → 효율

	            String cmd = "ACHV_JOB_SKILL_" + jobName + "_" + th;

	            // 이미 달성한 업적이면 스킵 (메모리)
	            if (achievedCmdSet.contains(cmd)) continue;

	            int rewardSp = AchievementConfig.jobSkillReward(th);

	            sb.append(
	                grantOnceIfEligibleFast(
	                    userName,
	                    roomName,
	                    cmd,
	                    rewardSp,
	                    achievedCmdSet
	                )
	            );
	        }
	    }

	    return sb.toString();
	}

	private String grantSpecialBuffAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        List<HashMap<String,Object>> rows
	) {
	    if (rows == null || rows.isEmpty()) return "";

	    StringBuilder sb = new StringBuilder();

	    for (HashMap<String,Object> row : rows) {
	        if (row == null) continue;

	        String flagCode = Objects.toString(row.get("FLAG_CODE"), "").trim();
	        if (flagCode.isEmpty()) continue;

	        int triggerCnt = row.get("TRIGGER_CNT") instanceof Number ? ((Number) row.get("TRIGGER_CNT")).intValue() : 0;
	        int ingCnt     = row.get("ING_CNT")     instanceof Number ? ((Number) row.get("ING_CNT")    ).intValue() : 0;
	        int killCnt    = row.get("KILL_CNT")    instanceof Number ? ((Number) row.get("KILL_CNT")   ).intValue() : 0;

	        // 발동 횟수
	        for (int th : AchievementConfig.SBUFF_TRIGGER_THRESHOLDS) {
	            if (triggerCnt < th) break;
	            String cmd = "ACHV_SBUFF_TRIGGER_" + flagCode + "_" + th;
	            if (achievedCmdSet.contains(cmd)) continue;
	            sb.append(grantOnceIfEligibleFast(userName, roomName, cmd, AchievementConfig.sbuffTriggerReward(th), achievedCmdSet));
	        }

	        // 진행중 공격
	        for (int th : AchievementConfig.SBUFF_ING_THRESHOLDS) {
	            if (ingCnt < th) break;
	            String cmd = "ACHV_SBUFF_ING_" + flagCode + "_" + th;
	            if (achievedCmdSet.contains(cmd)) continue;
	            sb.append(grantOnceIfEligibleFast(userName, roomName, cmd, AchievementConfig.sbuffIngReward(th), achievedCmdSet));
	        }

	        // 진행중 킬
	        for (int th : AchievementConfig.SBUFF_KILL_THRESHOLDS) {
	            if (killCnt < th) break;
	            String cmd = "ACHV_SBUFF_KILL_" + flagCode + "_" + th;
	            if (achievedCmdSet.contains(cmd)) continue;
	            sb.append(grantOnceIfEligibleFast(userName, roomName, cmd, AchievementConfig.sbuffKillReward(th), achievedCmdSet));
	        }
	    }

	    return sb.toString();
	}


	private String grantShopSellAchievementsFast(
	        String userName,
	        String roomName,
	        Set<String> achvCmdSet,
	        int soldCount   // [PERF] 호출부에서 프리로드
	) {

	    if (soldCount <= 0) return "";

	    StringBuilder sb = new StringBuilder();

	    for (int[] r : AchievementConfig.SHOP_SELL) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (soldCount < threshold) continue;

	        String cmd = "ACHV_SHOP_SELL_" + threshold;

	        // 🔹 Fast 체크 (쿼리 안 탐)
	        if (achvCmdSet.contains(cmd)) continue;

	        // 🔹 지급
	        HashMap<String,Object> p = new HashMap<>();
	        p.put("userName", userName);
	        p.put("roomName", roomName);
	        p.put("score", rewardSp);
	        p.put("scoreExt", "");
	        p.put("cmd", cmd);

	        botNewService.insertPointRank(p);
	        achvCmdSet.add(cmd); // 중요 ⭐

	        sb.append("✨ 상점 판매 ")
	          .append(threshold)
	          .append("회 달성 보상 +")
	          .append(formatSpShort(rewardSp))
	          .append(" 지급!♬")
	          .append(NL);
	    }

	    return sb.toString();
	}

	private String grantPotionUseAchievements(
	        String userName,
	        String roomName,
	        Set<String> achvCmdSet,
	        int potionUseCnt
	) {
	    if (potionUseCnt <= 0) return "";

	    StringBuilder sb = new StringBuilder();

	    for (int[] r : AchievementConfig.POTION_USE) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (potionUseCnt < threshold) continue;

	        String cmd = "ACHV_POTION_USE_" + threshold;
	        if (achvCmdSet.contains(cmd)) continue;

	        HashMap<String,Object> p = new HashMap<>();
	        p.put("userName", userName);
	        p.put("roomName", roomName);
	        p.put("score", rewardSp);
	        p.put("scoreExt", "");
	        p.put("cmd", cmd);

	        botNewService.insertPointRank(p);
	        achvCmdSet.add(cmd);

	        sb.append("✨ 업적 달성! 물약 사용 ")
	          .append(threshold)
	          .append("회 달성 보상 +")
	          .append(formatSpShort(rewardSp))
	          .append(" 지급!")
	          .append(NL);
	    }

	    return sb.toString();
	}

	private String renderMarketListForBuy(List<HashMap<String,Object>> items, String userName, boolean hiddenYn) {

	    if (items == null || items.isEmpty()) {
	        return "▶ " + userName + "님, 구매 가능 아이템이 없습니다.";
	    }

	    StringBuilder sb = new StringBuilder();

	    sb.append("■").append(userName).append("님 상점 목록").append(NL)
	      .append("http://rgb-tns.dev-apc.com/loa/item-view?userName="+userName).append(NL)
	      .append("────────────────").append(NL);

	    // 유저 레벨 (TARGET_LV 체크용, 1회 조회)
	    int userLv = 0;
	    try {
	        User uForLv = botNewService.selectUser(userName, "");
	        if (uForLv != null) userLv = uForLv.lv;
	    } catch (Exception ignore) {}

	    // 🔹 포션 가격 계산용 컨텍스트
	    boolean hasPotion = items.stream()
	            .anyMatch(it -> "POTION".equalsIgnoreCase(String.valueOf(it.get("ITEM_TYPE"))));

	    SP userPoint = new SP(0, "");

	    if (hasPotion) {
	        HashMap<String,Object> map = new HashMap<>();
	        map.put("userName", userName);
	        UserBattleContext ctx = calcUserBattleContext(map);
	        userPoint = ctx.lifetimeSp;
	    }

	    if(!hasPotion) {
	    	sb.append(NL).append("더보기..").append(ALL_SEE_STR).append(NL);
	    }
	    for (int i = 0; i < items.size(); i++) {

	        HashMap<String,Object> it = items.get(i);

	        int itemId = safeInt(it.get("ITEM_ID"));
	        String name = String.valueOf(it.get("ITEM_NAME"));
	        String itemType = String.valueOf(it.get("ITEM_TYPE"));

	        String ownedYn = String.valueOf(it.get("OWNED_YN"));
	        int ownQty = safeInt(it.get("OWN_QTY"));
	        String maxedYn = String.valueOf(it.get("MAXED_YN"));

	        boolean isEquip = "MARKET".equalsIgnoreCase(itemType);
	        boolean isPotion = "POTION".equalsIgnoreCase(itemType);

	        // 300/500/600/900번대(행운/반지/토템/선물): 이미 보유 시 목록에서 숨김
	        boolean isSummaryGroup = (itemId >= 300 && itemId < 400)
	                || (itemId >= 500 && itemId < 700)
	                || (itemId >= 900 && itemId < 1000);
	        if (isSummaryGroup && "Y".equalsIgnoreCase(ownedYn)) {
	            continue;
	        }

	        // 레벨 제한
	        int reqLv = MiniGameUtil.parseIntSafe(Objects.toString(it.get("TARGET_LV"), "0"));
	        boolean lvLocked = (reqLv > 0 && userLv < reqLv);

	        String displayPrice = buildDisplayPrice(it, isPotion, itemId, userPoint);

	        // 포션: 한 줄 표기
	        if (isPotion) {
	            sb.append(itemId).append(" :: [").append(name).append("] -").append(displayPrice).append("sp").append(NL)
	              .append(MiniGameUtil.getPotionOptionText(itemId)).append(NL);
	            continue;
	        }

	        sb.append("[")
	          .append(itemId)
	          .append("] ")
	          .append(name);

	        if (lvLocked) {
	            sb.append(" (Lv.").append(reqLv).append(" 필요)");
	        } else if ("Y".equalsIgnoreCase(ownedYn)) {
	            if (isEquip && !"Y".equalsIgnoreCase(maxedYn)) {
	                sb.append(" (보유중)");
	            } else {
	                sb.append(" (구매완료)");
	            }
	        }

	        sb.append(NL);

	        sb.append("↘가격: ").append(displayPrice).append("sp").append(NL);

	        sb.append("↘옵션: ")
	          .append(buildOptionText(it, isEquip, isPotion, ownQty, itemId))
	          .append(NL)
	          .append(NL);
	    }

	    return sb.toString();
	}

	private String buildDisplayPrice(HashMap<String,Object> it, boolean isPotion, int itemId, SP userPoint){

	    if(isPotion){
	        SP potionPrice = MiniGameUtil.getPotionPrice(itemId, userPoint);
	        return potionPrice.toString();
	    }

	    double price = safeDouble(it.get("ITEM_SELL_PRICE"));
	    String ext = String.valueOf(it.get("ITEM_SELL_PRICE_EXT"));

	    if(ext != null && !ext.equals("") && !ext.equals("null")){
	        return price + ext;
	    }

	    return price + "";
	}

	private String buildOptionText(HashMap<String, Object> it, boolean isEquip, boolean isPotion, int ownQty,
			int itemId) {

		if (isPotion) {
			return MiniGameUtil.getPotionOptionText(itemId);
		}

		if (isEquip) {
			int curQty = (ownQty <= 0 ? 1 : ownQty);
			return MiniGameUtil.buildEnhancedOptionLine(it, curQty);
		}

		return MiniGameUtil.buildEnhancedOptionLine(it, 1);
	}
	

	/**
	 * 쓰러진 유저 자동 부활 처리
	 * - 마지막 피격(또는 공격) 시점 기준 REVIVE_WAIT_MINUTES(10) 경과 시 최대체력 10%로 부활
	 * - 이후 경과 시간에 따라 5분마다 regen 만큼 추가 회복
	 */
	private String reviveAfter1hIfDead(String userName, String roomName, User u,
	                                   int hpMax, int regen) {
	    // 살아있으면 관여 안 함
	    if (u.hpCur > 0) return null;

	    Timestamp baseline = botNewService.selectLastDamagedTime(userName, roomName);

	    // 기준 이벤트가 전혀 없으면: 보수적으로 10%로 세팅 후 조용히 복구
	    if (baseline == null) {
	        int startHp = (int) Math.ceil(hpMax * 0.10); // 10%
	        botNewService.updateUserHpOnlyTx(userName, roomName, startHp);
	        u.hpCur = startHp;
	        return "";
	    }

	    Instant reviveAt = baseline.toInstant().plus(Duration.ofMinutes(REVIVE_WAIT_MINUTES));
	    Instant now = Instant.now();

	    // 아직 부활 시간 전이면 대기 안내
	    if (now.isBefore(reviveAt)) {
	        long remainMin = (long) Math.ceil(Duration.between(now, reviveAt).getSeconds() / 60.0);
	        return "쓰러진 상태입니다. 약 " + remainMin + "분 후 자동 부활합니다.";
	    }

	    // 부활 시간 경과: 10%에서 시작
	    int startHp = (int) Math.ceil(hpMax * 0.1);

	    // 부활 시점 이후 경과 시간만큼 5분마다 회복 적용
	    long afterMin = Duration.between(reviveAt, now).toMinutes();
	    long healedTicks = Math.max(0, afterMin) / 5;
	    long healed = healedTicks * Math.max(0, (long) regen);

	    int effective = (int) Math.min((long) hpMax, (long) startHp + healed);

	    botNewService.updateUserHpOnlyTx(userName, roomName, effective);
	    u.hpCur = effective;

	    // 빈 문자열 반환 시 이번 턴은 안내 없이 평소처럼 진행
	    return "";
	}

	private int computeEffectiveHpFromLastAttack(String userName, String roomName, User u, int hpMax, int regen) {

	    // 0) 이미 풀피이거나 리젠 수치가 0 이하면 그대로 반환
	    if (u.hpCur >= hpMax || regen <= 0) {
	        return Math.min(u.hpCur, hpMax);
	    }

	    // 1) 마지막으로 "맞은" 시각
	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) {
	        return Math.min(u.hpCur, hpMax);
	    }

	    Instant damagedAt = damaged.toInstant();
	    Instant now = Instant.now();

	    // 2) damaged 이후 현재까지 총 리젠 틱 수
	    long minutesFromDamaged = java.time.Duration.between(damagedAt, now).toMinutes();
	    if (minutesFromDamaged <= 0) {
	        return Math.min(u.hpCur, hpMax);
	    }

	    long totalTicksNow = minutesFromDamaged / 5L;  // 5분당 1틱
	    if (totalTicksNow <= 0) {
	        return Math.min(u.hpCur, hpMax);
	    }

	    // 3) 마지막 공격 시각으로 "이미 반영된 틱" 계산 (항상 실시간 DB 조회)
	    long prevTicks = 0L;
	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        long minutesUntilLastAtk = java.time.Duration.between(damagedAt, lastAtk.toInstant()).toMinutes();
	        if (minutesUntilLastAtk > 0) {
	            prevTicks = minutesUntilLastAtk / 5L;
	        }
	    }

	    // 4) 이번에 새로 발생한 틱만 회복
	    long newTicks = totalTicksNow - prevTicks;
	    if (newTicks <= 0) {
	        return Math.min(u.hpCur, hpMax);
	    }

	    long effective = Math.min((long) u.hpCur + newTicks * (long) regen, (long) hpMax);
	    return (int) effective;
	}

	
	public String guideSetTargetMessage() {
	    final String NL = "♬";
	    List<Monster> monsters = botNewService.selectAllMonsters();
	    StringBuilder sb = new StringBuilder();
	    sb.append("공격 타겟이 없습니다. 먼저 타겟을 설정해주세요.").append(NL)
	      .append("예) /공격타겟 1   또는   /공격타겟 토끼").append(NL).append(NL)
	      .append("▶ 선택 가능한 몬스터").append(ALL_SEE_STR);
	    for (Monster m : monsters) {
	        sb.append(renderMonsterSelectLine(m,0)).append(NL);
	    }
	    return sb.toString();
	}
	
	private CooldownCheck checkCooldown(String userName, String roomName, String param1, String job, int buffTime) {
	    return checkCooldown(userName, roomName, param1, job, buffTime, null, 0);
	}

	private CooldownCheck checkCooldown(String userName, String roomName, String param1, String job, int buffTime, Timestamp cachedLastAtk) {
	    return checkCooldown(userName, roomName, param1, job, buffTime, cachedLastAtk, 0);
	}

	// [FIX1] cachedLastAtk 가 null 이 아니면 DB 조회 생략
	// itemCdReduction: 보스 아이템(7004 등)에 의한 추가 쿨타임 감소(초)
	private CooldownCheck checkCooldown(String userName, String roomName, String param1, String job, int buffTime, Timestamp cachedLastAtk, int itemCdReduction) {
	    if ("test".equals(param1)) return CooldownCheck.ok();

	    int baseCd = COOLDOWN_SECONDS; // 2분

	    if ("축복술사".equals(job)) {
	    	baseCd = 30 * 60; // 30분
	    }
	    if ("궁수".equals(job)) {
	    	baseCd = 10 * 60; // 10분
	    }
	    if ("사냥꾼".equals(job)) {
	    	baseCd = 6 * 60; // 6분
	    }

	    if (buffTime > 0) {
	    	baseCd -= 1 * 60;
	    } else if (buffTime < 0) {
	    	baseCd = baseCd / 2; // 쿨타임감소 버프: 50% 감소
	    }

	    if (itemCdReduction > 0) {
	        // itemCdReduction은 % 단위 (예: 20 = 20% 감소)
	        baseCd = Math.max(10, (int) Math.round(baseCd * (100 - itemCdReduction) / 100.0));
	    }

	    Timestamp last = (cachedLastAtk != null) ? cachedLastAtk : botNewService.selectLastAttackTime(userName, roomName);
	    if (last == null) return CooldownCheck.ok();

	    long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
	    if (sec >= baseCd) return CooldownCheck.ok();

	    long remainSec = baseCd - sec;
	    return CooldownCheck.blockSeconds(remainSec);
	}

	

	private Flags rollFlags(User u, Monster m) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Flags f = new Flags();
		int crit = Math.min(100, Math.max(0, u.critRate));
		f.atkCrit = r.nextInt(100) < crit;
		f.monPattern = rollPatternWeighted(m, r);
		return f;
	}
	
	private int rollPatternWeighted(Monster m, ThreadLocalRandom r) {
		int enabled = Math.max(1, m.monPatten);
		int[] custom = MiniGameUtil.MON_PATTERN_WEIGHTS.get(m.monNo);
		int[] weights;

		if (custom != null && custom.length >= enabled) {
			// 몬스터별로 설정된 가중치가 있고, 패턴 개수만큼 들어있으면 그대로 사용
			weights = Arrays.copyOf(custom, enabled);
		} else {
			// 2) 없으면 기존 공통 로직 사용
			weights = new int[enabled];
			for (int i = 0; i < enabled; i++)
				weights[i] = 1;

			if (enabled == 2) {
				weights[0] = 20;
				weights[1] = 80;
			} else if (enabled == 3) {
				weights[0] = 10;
				weights[1] = 60;
				weights[2] = 30;
			} else if (enabled == 4) {
				weights[0] = 0;
				weights[1] = 60;
				weights[2] = 25;
				weights[3] = 15;
			} else if (enabled == 5) {
				weights[0] = 0;
				weights[1] = 62;
				weights[2] = 7;
				weights[3] = 26;
				weights[4] = 5;
			}
		}

		// 3) 안전장치 (모든 weight가 0일 경우)
		int sum = 0;
		for (int w : weights)
			sum += Math.max(0, w);
		if (sum <= 0) {
			for (int i = 0; i < enabled; i++)
				weights[i] = 1;
			sum = enabled;
		}

		// 4) 가중치 랜덤 픽
		int pick = r.nextInt(sum) + 1;
		int acc = 0;
		for (int i = 0; i < enabled; i++) {
			acc += weights[i];
			if (pick <= acc)
				return i + 1; // 패턴 번호는 1부터
		}
		return 1;
	}


	private AttackCalc calcDamage(User u, Monster m, Flags f, int baseAtk, boolean crit, double critMultiplier,boolean nightmareYn) {
		AttackCalc c = new AttackCalc();
		c.baseAtk = baseAtk;
		c.critMultiplier = critMultiplier;
		c.atkDmg = crit ? (int) Math.round(baseAtk * critMultiplier) : baseAtk;

		int monAtk = m.monAtk;

		if(nightmareYn) {
			monAtk = monAtk * NM_MUL_HP_ATK;
		}
		
		String name = m.monName;
		switch (f.monPattern) {
		case 1: c.monDmg = 0; c.patternMsg = name + "이(가) 당신을 바라봅니다"; break;
		case 2:
			int minDmg = Math.max(1, (int) Math.floor(monAtk * 0.5));
			int maxDmg = monAtk;
			c.monDmg = ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1);
			c.patternMsg = name + "이(가) " + c.monDmg + " 의 데미지로 반격합니다!"; break;
		case 3:
		    // 기존 공격 데미지(크리티컬 반영 후)를 절반으로 줄인 뒤,
		    // 몬스터 방어력(defPower)을 적용하여 최종 피해를 계산한다.
		    int original = c.atkDmg; // 이전 단계(크리 포함) 데미지
		    int reduced = (int) Math.round(original * 0.5); // 방어 패턴으로 1차 감소

		    int minDef = Math.max(1, (int) Math.floor(monAtk * 0.5)); // 예: 22라면 11
		    int maxDef = monAtk;                                      // 예: 22
		    int defPower = ThreadLocalRandom.current().nextInt(minDef, maxDef + 1);

		    if (defPower >= reduced) {
		        // 완전 방어
		        c.atkDmg = 0;
		        c.monDmg = 0;
		        c.patternMsg = name + "이(가) 공격을 완전 방어했습니다!";
		    } else {
		        // 일부 방어: 최종 피해 = reduced - defPower
		        int finalDmg = reduced - defPower;
		        c.atkDmg = finalDmg;
		        c.monDmg = 0;
		        c.patternMsg = name + "이(가) 방어합니다!("
		                + original
		                + " → 50%↓ " + reduced
		                + " → 방어력 " + defPower
		                + " → 최종 " + finalDmg + ")";
		    }
		    break;
		case 4: c.monDmg = (int) Math.round(monAtk * 1.5); c.patternMsg = name + "의 필살기! (피해 " + c.monDmg + ")"; break;
		case 5:  

            double rnd = ThreadLocalRandom.current().nextDouble();
            if (rnd < 0.20) {
            	 // 🔥 빈사 패턴: 체력을 1 남기고 공격 연출
                int lethalDmg = Math.max(1, u.hpCur - 1); // 1HP 남기기
                c.atkDmg = 0;  
                c.monDmg = lethalDmg;
                c.patternMsg = name + "의 일격! 당신을 빈사 상태로 몰아넣었습니다!";
            } else {
            	// 🔥 보스 흡혈 패턴
                // 1) 플레이어에게 들어갈 피해 = 보스 ATK의 20%
                int lifeDmg = Math.max(1, (int)Math.round(monAtk * 0.2));

                // 2) 플레이어 공격은 0으로 취급 (보스에게 데미지 못 줌)
                //    내부적으로는 보스 회복량을 기록하기 위해 ATK_DMG를 음수로 넣는다.
                //    이렇게 하면 totalDealtDmg가 줄어들어서 "보스 HP 회복" 효과가 난다.
                int heal = lifeDmg * 10;  // 준 피해의 10배를 회복 (오버힐 느낌)
                c.atkDmg = -heal;         // 누적 데미지 감소 → 보스가 heal 만큼 회복

                // 3) 플레이어가 받는 피해
                c.monDmg = lifeDmg;

                // 4) 메시지
                c.patternMsg = name
                        + "의 흡혈 공격! 보스가 공격을 막고, 유저에게 "
                        + lifeDmg + " 피해를 주고, 체력을 "
                        + heal + " 만큼 회복했습니다!";
            }
		    break;
		case 6:
			c.atkDmg = 0;
		    c.monDmg = 0;
		    c.endBattle = true;
		    c.patternMsg = name + "이(가) 울부짖었습니다. 플레이어는 기절했습니다.(전투종료)";
		    break;
		case 7:
			break;
		case 8:
			break;
		default: c.monDmg = 0; c.patternMsg = name + "의 알 수 없는 행동… (피해 0)";
		}
		return c;
	}
	
	
	private String formatSigned(int v) {
	    return (v >= 0 ? "+" + v : String.valueOf(v));
	}

	private long safeLong(Object v) {
		// Double 경유로 파싱해 "32661315485.5" 같은 소수점 값도 정상 처리
		try { return v == null ? 0 : (long) Double.parseDouble(String.valueOf(v)); }
		catch (Exception e) { return 0; }
	}
	private int safeInt(Object v) {
	    try { return v == null ? 0 : Integer.parseInt(String.valueOf(v)); }
	    catch (Exception e) { return 0; }
	}
	private double safeDouble(Object v) {
		try { return v == null ? 0 : Double.parseDouble(String.valueOf(v)); }
		catch (Exception e) { return 0.0; }
	}

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky, boolean dark,
			boolean gray, int nightmareYnVal) {
		return resolveKillAndDrop(m, c, willKill, u, lucky, dark, gray, false, nightmareYnVal, null);
	}

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky, boolean dark,
			boolean gray, int nightmareYnVal, Set<Integer> ownedBossItems) {
		return resolveKillAndDrop(m, c, willKill, u, lucky, dark, gray, false, nightmareYnVal, ownedBossItems);
	}

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill, User u, boolean lucky, boolean dark,
			boolean gray, boolean shadow, int nightmareYnVal, Set<Integer> ownedBossItems) {
		Resolve r = new Resolve();
		r.killed = willKill;
		r.lucky = lucky;
		r.dark = dark;
		r.gray = gray;
		r.shadow = shadow;

		int monLv = m.monLv;
		long monExp = m.monExp;

		if (nightmareYnVal >= 1) {
			monExp *= NM_MUL_EXP;
			if (nightmareYnVal == 2)
				monExp *= HEL_MUL_EXP; // 헬 = 나메*HEL_MUL_EXP
			monLv += (nightmareYnVal == 2) ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
		}

		int levelGap = u.lv - monLv;
		double expMultiplier;

		if (levelGap >= 0) {
			// 플레이어가 몬스터보다 높을 때
			expMultiplier = Math.max(0.1, 1.0 - levelGap * 0.1);
		} else {
			// 몬스터가 더 강할 때 (보너스)
			expMultiplier = 1.0 + Math.min(-levelGap, 5) * 0.05; // 레벨 차이 1당 5% 보너스, 최대 25%
		}

		int baseKillExp = (int) Math.round(monExp * expMultiplier);

		if (willKill) {
			if (shadow) {
				baseKillExp *= 10; // 그림자 몬스터: 경험치 10배
			} else if (gray) {
				baseKillExp *= 9;
			} else if (dark) {
				baseKillExp *= 5;
			} else if (lucky) {
				baseKillExp *= 3;
			}

			r.gainExp = baseKillExp;
		} else if (c.atkDmg > 0) {
			r.gainExp = (int) Math.round(baseKillExp / 20) + 1; //
		}

		if (shadow && willKill) {
			r.dropCode = "0"; // 그림자 몬스터: 드랍 없음
			return r;
		}
		if (gray && willKill) {
			r.dropCode = "9";
			return r;
		}
		if (lucky && willKill) {
			r.dropCode = "3";
			return r;
		}
		if (dark && willKill) {
			r.dropCode = "5";
			return r;
		}

		// 기본드랍 100%
		r.dropCode = "1";

		double extraDropRate = MiniGameUtil.getDropRateByNo(m.monNo); // ← 새 메서드 사용

		boolean extraDrop = ThreadLocalRandom.current().nextDouble(0, 100) < extraDropRate;

		if (extraDrop) {
			r.dropCode = "2"; // 🔥 기본 + 추가 드랍
		}

		// [7008] 일반 드랍+1 (빛/어둠/음양 제외)
		if (ownedBossItems != null && ownedBossItems.contains(7008)
				&& ("1".equals(r.dropCode) || "2".equals(r.dropCode))) {
			r.bonusNormalDrop = true;
		}

		return r;
	}
	
	
	
	/** HP/EXP/LV + 로그 저장 (DB에는 '순수 레벨 기반 스탯'만 반영) */
	private LevelUpResult persist(String userName, String roomName,
	                              User u, Monster m,
	                              Flags f, AttackCalc c, Resolve res, int hpMax,
	                              boolean nightmare, int specialBuffStart, int specialBuffIng, String specialBuffCode) {

	    // 1) 최종 HP 계산 (전투 데미지 반영)
	    u.hpCur = Math.max(0, u.hpCur - c.monDmg);

	    // 2) EXP 적용 + 레벨업 (u.lv, u.expCur, u.expNext 변경)
	    LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);
	    
	 // 3) 레벨업이 발생했고, 죽은 게 아니라면 → 실전투 HPMax 기준으로 풀피 회복
	    if (up.levelUpCount > 0 && u.hpCur > 0 && hpMax > 0) {
	        u.hpCur = hpMax; // 여기서 109 같은 값으로 올려줌
	    }

	    // 3) 순수 레벨 기준 스탯 계산
	    int baseHpMax    = MiniGameUtil.calcBaseHpMax(u.lv);
	    int baseAtkMin   = MiniGameUtil.calcBaseAtkMin(u.lv);
	    int baseAtkMax   = MiniGameUtil.calcBaseAtkMax(u.lv);
	    int baseCrit = MiniGameUtil.calcBaseCritRate(u.lv);
	    int baseHpRegen  = MiniGameUtil.calcBaseHpRegen(u.lv);
	    
	    // 4) 유저 테이블 업데이트: **항상 '순수 레벨 스탯'만 저장**
	    botNewService.updateUserAfterBattleTx(
	        userName,
	        roomName,
	        u.lv,
	        u.expCur,
	        u.expNext,
	        u.hpCur,
	        baseHpMax,
	        baseAtkMin,
	        baseAtkMax,
	        baseCrit,
	        baseHpRegen
	    );

	    // 5) 사망 여부
	    int deathYn = (u.hpCur == 0 && c.monDmg > 0) ? 1 : 0;

	    // 6) 드랍 인벤토리 적재 (킬+드랍 있을 때)
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            try {
	            	
	            	String gainType="DROP";
	            	if("3".equals(res.dropCode)) {
	            		gainType = "DROP3";
	            	}else if ("5".equals(res.dropCode)) {
	            		gainType = "DROP5";
	            	}else if ("9".equals(res.dropCode)) {
	            		gainType = "DROP9";
	            	}
	            	
	            	
	            	int qty=1;
	            	if ("2".equals(res.dropCode)) {
	            	    qty = 2; // 기본 1 + 추가 1
	            	}
	            	if (res.bonusNormalDrop) qty++; // [7008] 일반 드랍+1


	                Integer itemId = getItemIdCached(dropName);
	                if (itemId != null) {
	                    HashMap<String, Object> inv = new HashMap<>();
	                    inv.put("userName",  userName);
	                    inv.put("roomName",  roomName);
	                    inv.put("itemId",    itemId);
                    	inv.put("qty",qty);
	                    inv.put("delYn",     "1");
	                    inv.put("gainType", gainType);
	                    botNewService.insertInventoryLogTx(inv);
	                }
	            } catch (Exception ignore) {
	                // 드랍 저장 실패해도 전투 진행은 계속
	            }
	        }
	    }

	    // 7) BattleLog 저장 (전투 당시 정보 기준)
	    
	    
	    int dropAsInt = 0; 
	    if( res.dropCode == "9") {
	    	dropAsInt = 9;
	    }else if( res.dropCode == "5") {
	    	dropAsInt = 5;
	    }if( res.dropCode == "3") {
	    	dropAsInt = 3;
	    }if( res.dropCode == "2") {
	    	dropAsInt = 2;
	    }if( res.dropCode == "1") {
	    	dropAsInt = 1;
	    }else {
	    	dropAsInt = 0;
	    }

	    
	    int buffYn = 0;
	    
	    if (u.job !=null && "도사".equals(u.job.trim()) || "음양사".equals(u.job.trim())) {   // job 은 u.job.trim()
	        buffYn = 1;
	    }

	    int luckyYn=0;
	    if(res.shadow) {
	        luckyYn =4;
	    }else if(res.gray) {
	    	luckyYn =3;
	    }else if(res.dark) {
	    	luckyYn =2;
	    }else if(res.lucky) {
	    	luckyYn =1;
	    }
	    
	    BattleLog log = new BattleLog()
	        .setUserName(userName)
	        .setRoomName(roomName)
	        .setLv(up.beforeLv)                 // 공격 시점 레벨
	        .setTargetMonLv(m.monNo)
	        .setGainExp(up.gainedExp)
	        .setAtkDmg(c.atkDmg)
	        .setMonDmg(c.monDmg)
	        .setAtkCritYn(f.atkCrit ? 1 : 0)
	        .setMonPatten(f.monPattern)
	        .setKillYn(res.killed ? 1 : 0)
	        .setNowYn(1)
	        .setDeathYn(deathYn)
	        .setLuckyYn(luckyYn)
	        .setDropYn(res.killed ? dropAsInt : 0)
	    	.setBuffYn(buffYn)
	    	.setJobSkillYn(c.jobSkillUsed ? 1 : 0)
	    	.setJob(u.job)
	    	.setNightmareYn(u.nightmareYn)
	    	.setSpecialBuffStart(specialBuffStart)
	    	.setSpecialBuffIng(specialBuffIng)
	    	.setSpecialBuffCode(specialBuffCode);

	    botNewService.insertBattleLogTx(log);

	    // 궁사 분할 화살 추가 로그 (공격횟수 증가용, 2번째 화살부터 개별 insert)
	    if (c.multiAttack > 1) {
	        List<BattleLog> arrowLogs = new ArrayList<>();
	        for (int i = 1; i < c.multiAttack; i++) {
	            arrowLogs.add(new BattleLog()
	                .setUserName(userName)
	                .setRoomName(roomName)
	                .setLv(up.beforeLv)
	                .setTargetMonLv(m.monNo)
	                .setGainExp(-1)
	                .setAtkDmg(0)
	                .setMonDmg(0)
	                .setAtkCritYn(0)
	                .setMonPatten(0)
	                .setKillYn(0)
	                .setNowYn(1)
	                .setDeathYn(0)
	                .setLuckyYn(0)
	                .setDropYn(0)
	                .setBuffYn(0)
	                .setJobSkillYn(0)
	                .setJob(u.job)
	                .setNightmareYn(u.nightmareYn)
	                .setSpecialBuffStart(0)
	                .setSpecialBuffIng(0)
	                .setSpecialBuffCode(null)
	                .setShotIndex(i));
	        }
	        botNewService.insertBattleLogsBatch(arrowLogs);
	    }

	    return up;
	}

	class DropSummary {
	    int normal;
	    int fragment;
	    int light;
	    int dark;
	    int gray;
	}

	private String buildAttackMessage(
	        String userName, User u, Monster m, Flags flags, AttackCalc calc,
	        Resolve res, LevelUpResult up,
	        int monHpRemainBefore, int monMaxHp,
	        int shownAtkMin, int shownAtkMax,
	        int displayHpMax,
	        String midExtraLines,
	        String hunterMsg,
	        String botExtraLines,
	        boolean nightmare,
	        UserBattleContext ctx
	) {
	    return buildAttackMessage(userName, u, m, flags, calc, res, up,
	            monHpRemainBefore, monMaxHp, shownAtkMin, shownAtkMax,
	            displayHpMax, midExtraLines, hunterMsg, botExtraLines, nightmare, ctx, null);
	}

	/**
	 * @param detailOut null이면 기존 동작(단일 메시지).
	 *                  non-null이면 데미지 계산식·헌터랭크·연사계산·레벨업 상세를 detailOut에 수집.
	 *                  호출부에서 포인트/공지 출력 후 NL + === + NL + detailOut 을 붙임.
	 */
	private String buildAttackMessage(
	        String userName, User u, Monster m, Flags flags, AttackCalc calc,
	        Resolve res, LevelUpResult up,
	        int monHpRemainBefore, int monMaxHp,
	        int shownAtkMin, int shownAtkMax,
	        int displayHpMax,
	        String midExtraLines,
	        String hunterMsg,
	        String botExtraLines,
	        boolean nightmare,
	        UserBattleContext ctx,
	        StringBuilder detailOut
	) {
	    final boolean split = (detailOut != null);
	    StringBuilder sb = new StringBuilder();

	    // 헤더
	    sb.append("⚔ ").append(userName).append("님, ").append(NL)
	      .append("▶ ").append(m.monName);
	    if(nightmare) {
	    	if(ctx.user.nightmareYn == 2) sb.append("[헬]");
	    	else sb.append("[나이트메어]");
	    }
	    sb.append("을(를) 공격!").append(NL).append(NL);

	    if (res.shadow) sb.append("✨ SHADOW MONSTER! (처치시 경험치×10, 드랍 없음)").append(NL);
	    if (res.gray) sb.append("✨ LIGHT&DARK MONSTER! (처치시 경험치×9, 음양 드랍)").append(NL);
	    if (res.dark) sb.append("✨ DARK MONSTER! (처치시 경험치×5, 어둠 드랍)").append(NL);
	    if (res.lucky) sb.append("✨ LUCKY MONSTER! (처치시 경험치×3, 빛 드랍)").append(NL);

	    if (u.job.equals("곰")) {
	        // 더보기 없이 그대로 표기 (곰은 계산식 없음 — dmgCalcMsg 는 항상 main에 출력)
	        sb.append("최대체력 ").append(formatWan(displayHpMax)).append(" 이하에게 괴력!").append(NL);
	        if (midExtraLines != null && !midExtraLines.isEmpty())
	            sb.append(midExtraLines).append(NL);
	    } else {
	        // 치명타/축복 (항상 main)
	        if (flags.atkCrit) sb.append("✨ 치명타!");
	        if (u.blessYn == 1) sb.append("✨축복(x1.5)!");
	        sb.append(NL);

	        if (split) {
	            // [main] 최종 데미지만 표기
	            sb.append("⚔ 데미지: ").append(formatWan(calc.atkDmg)).append(NL);

	            // [detail] 계산 상세
	            detailOut.append("⚔ 데미지: (").append(formatWan(shownAtkMin)).append("~").append(formatWan(shownAtkMax)).append(" ⇒ ");
	            if (flags.atkCrit && calc.baseAtk > 0 && calc.critMultiplier >= 1.0) {
	                detailOut.append(formatWan(calc.baseAtk)).append("*").append(trimDouble(calc.critMultiplier)).append("=>").append(formatWan(calc.atkDmg));
	            } else {
	                detailOut.append(formatWan(calc.atkDmg));
	            }
	            detailOut.append(")").append(NL);
	            if (hunterMsg != null && !hunterMsg.isEmpty())
	                detailOut.append(hunterMsg).append(NL);
	            if (midExtraLines != null && !midExtraLines.isEmpty())
	                detailOut.append(midExtraLines).append(NL);
	        } else {
	            // 기존 동작
	            sb.append("⚔ 데미지: (").append(formatWan(shownAtkMin)).append("~").append(formatWan(shownAtkMax)).append(" ⇒ ");
	            if (flags.atkCrit && calc.baseAtk > 0 && calc.critMultiplier >= 1.0) {
	                sb.append(formatWan(calc.baseAtk)).append("*").append(trimDouble(calc.critMultiplier)).append("=>").append(formatWan(calc.atkDmg));
	            } else {
	                sb.append(formatWan(calc.atkDmg));
	            }
	            sb.append(")").append(NL);
	            if (hunterMsg != null && !hunterMsg.isEmpty())
	                sb.append(hunterMsg).append(NL).append(NL);
	            if (midExtraLines != null && !midExtraLines.isEmpty())
	                sb.append(midExtraLines).append(NL).append(NL);
	        }
	    }

	    // 몬스터 HP (항상 main)
	    int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);
	    sb.append("❤️ 몬스터 HP: ").append(formatWan(monHpAfter)).append(" / ").append(formatWan(monMaxHp)).append(NL);

	    // 반격 알림 (항상 main — 게임 이벤트)
	    if (calc.patternMsg != null && !calc.patternMsg.isEmpty()) {
	        sb.append(NL).append("⚅ ").append(calc.patternMsg).append(NL);
	    }

	    // 현재 체력 (항상 main)
	    if (calc.monDmg > 0) {
	        sb.append("❤️ 받은 피해: ").append(formatWan(calc.monDmg))
	          .append(",  현재 체력: ").append(formatWan(u.hpCur)).append(" / ").append(formatWan(displayHpMax)).append(NL);
	    } else {
	        sb.append("❤️ 현재 체력: ").append(formatWan(u.hpCur)).append(" / ").append(formatWan(displayHpMax)).append(NL);
	    }

	    // 드랍 (항상 main)
	    if (res.killed && !"0".equals(res.dropCode)) {
	        String dropName = (m.monDrop == null ? "" : m.monDrop.trim());
	        if (!dropName.isEmpty()) {
	            if      ("9".equals(res.dropCode)) sb.append("✨ 드랍 획득: 음양").append(dropName).append(NL);
	            else if ("5".equals(res.dropCode)) sb.append("✨ 드랍 획득: 어둠").append(dropName).append(NL);
	            else if ("3".equals(res.dropCode)) sb.append("✨ 드랍 획득: 빛").append(dropName).append(NL);
	            else if ("2".equals(res.dropCode)) sb.append("✨ 드랍 획득: ").append(dropName).append(" x2");
	            else                               sb.append("✨ 드랍 획득: ").append(dropName).append(NL);
	            sb.append(NL);
	        }
	    }

	    // EXP (항상 main)
	    double gainPercent = (double) res.gainExp / u.expNext * 100;
	    double curPercent  = (double) u.expCur    / u.expNext * 100;
	    sb.append("✨ EXP +").append(formatWan(res.gainExp))
	      .append("(").append(String.format("%.1f", gainPercent)).append("%)")
	      .append("[").append(String.format("%.1f", curPercent)).append("%/100%]")
	      .append(NL);

	    // 레벨업
	    if (up != null && up.levelUpCount > 0) {
	        sb.append(NL).append("★★★ ✨레벨업!✨ ★★★").append(NL);
	        sb.append("Lv ").append(up.beforeLv).append(" → ").append(up.afterLv);
	        if (up.levelUpCount > 1) sb.append(" ( +").append(up.levelUpCount).append(" )");
	        sb.append(NL);

	        if (split) {
	            // [detail] 상세 수치
	            detailOut.append("└:❤️HP ").append(formatWan(up.beforeHpMax)).append("→").append(formatWan(up.afterHpMax))
	                     .append(" (+").append(formatWan(up.hpMaxDelta)).append(")").append(NL);
	            detailOut.append("└:⚔ATK ").append(up.beforeAtkMin).append("~").append(up.beforeAtkMax)
	                     .append("→").append(up.afterAtkMin).append("~").append(up.afterAtkMax)
	                     .append(" (+").append(up.atkMinDelta).append("~+").append(up.atkMaxDelta).append(")").append(NL);
	            detailOut.append("└: CRIT ").append(up.beforeCrit).append("%→").append(up.afterCrit).append("%")
	                     .append(" (+").append(up.critDelta).append("%)").append(NL);
	            detailOut.append("└: 5분당회복 ").append(up.beforeHpRegen).append("→").append(up.afterHpRegen)
	                     .append(" (+").append(up.hpRegenDelta).append(")").append(NL);
	        } else {
	            // 기존 동작
	            sb.append("└:❤️HP ").append(formatWan(up.beforeHpMax)).append("→").append(formatWan(up.afterHpMax))
	              .append(" (+").append(formatWan(up.hpMaxDelta)).append(")").append(NL);
	            sb.append("└:⚔ATK ").append(up.beforeAtkMin).append("~").append(up.beforeAtkMax)
	              .append("→").append(up.afterAtkMin).append("~").append(up.afterAtkMax)
	              .append(" (+").append(up.atkMinDelta).append("~+").append(up.atkMaxDelta).append(")").append(NL);
	            sb.append("└: CRIT ").append(up.beforeCrit).append("%→").append(up.afterCrit).append("%")
	              .append(" (+").append(up.critDelta).append("%)").append(NL);
	            sb.append("└: 5분당회복 ").append(up.beforeHpRegen).append("→").append(up.afterHpRegen)
	              .append(" (+").append(up.hpRegenDelta).append(")").append(NL);
	        }
	    }

	    if (botExtraLines != null && !botExtraLines.isEmpty()) {
	        sb.append(botExtraLines).append(NL);
	    }

	    return sb.toString();
	}

	/* ===== utils ===== */

	/** 10,000,000(천만) 이상이면 만 단위로 표시. 예: 12345678 → "1234만" */
	private String formatWan(int v) {
	    if (v >= 10_000_000) return (v / 10_000) + "만";
	    return String.valueOf(v);
	}

	private String trimDouble(double v) {
		return String.format("%.2f", v);
	    
	}

	// 이름은 기존 그대로 두고, 현재는 20% 기준으로 동작
	private int minutesUntilReach30(User u, String userName, String roomName) {
	    int threshold = (int)Math.ceil(u.hpMax * 0.05); // ✅ 5% 기준
	    
	    if(u.job.equals("곰")) {
	    	threshold = (int)Math.ceil(u.hpMax * 0.05);
	    }
	    if (u.hpCur >= threshold) return 0;
	    if (u.hpRegen <= 0) return Integer.MAX_VALUE;

	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    if (damaged == null) return 0; // 맞은 적 없으면 막지 않음

	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

	    Timestamp from = damaged;
	    if (lastAtk != null && lastAtk.after(damaged)) {
	        from = lastAtk;
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long offset = minutesPassed % 5;

	    int toNextTick = (int)((5 - offset) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    int hpNeeded = threshold - u.hpCur;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)u.hpRegen);
	    if (ticksNeeded <= 0) return 0;

	    return toNextTick + (ticksNeeded - 1) * 5;
	}
	private String buildBelowHalfMsg(String userName, String roomName, User u, String param1, int cooldownBuff, String cdJob, int itemCdReduction) {
	    if ("test".equals(param1)) return null; // 테스트 모드 패스

	    int regenWaitMin = minutesUntilReach30(u, userName, roomName);
	    CooldownCheck cd = checkCooldown(userName, roomName, param1, cdJob, cooldownBuff, null, itemCdReduction);

	    long remainMin = cd.remainSeconds / 60;
	    long remainSec = cd.remainSeconds % 60;

	    int waitMin = Math.max(regenWaitMin, cd.remainMinutes);
	    if (waitMin <= 0) return null;

	    StringBuilder sb = new StringBuilder();
	    sb.append(userName).append("님, 약 ").append(waitMin).append("분 후 공격 가능").append(NL)
	      .append("(최대체력의 5%까지 회복 필요 ").append(regenWaitMin).append("분, ")
	      .append("쿨타임 ").append(remainMin).append("분 ").append(remainSec).append("초)").append(NL)
	      .append("현재 체력: ").append(u.hpCur).append(" / ").append(u.hpMax)
	      .append(", 5분당 회복 +").append(u.hpRegen).append(NL);

	    String sched = buildRegenScheduleSnippetEnhanced(userName, roomName, u, waitMin);
	    if (sched != null) sb.append(sched).append(NL);

	    return sb.toString();
	}
	// ✅ 5분 단위 리젠 스케줄 + 풀HP까지 예상시간 표시
		private String buildRegenScheduleSnippetEnhanced(String userName, String roomName, User u, int horizonMinutes) {
		    if (horizonMinutes <= 0 || u.hpRegen <= 0 || u.hpCur >= u.hpMax) return null;

		    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
		    if (damaged == null) return null;

		    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);
		    Timestamp from = damaged;
		    if (lastAtk != null && lastAtk.after(damaged)) {
		        from = lastAtk;
		    }

		    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
		    long ticksSoFar = minutesPassed / 5;

		    int toNextTick = (int)((5 - (minutesPassed % 5)) % 5);
		    if (toNextTick == 0) toNextTick = 5;

		    StringBuilder sb = new StringBuilder();
		    final String NL = "♬";

		    int curHp = u.hpCur;
		    int maxHp = u.hpMax;
		    int regen = u.hpRegen;

		    // 5분 단위로 예측 표시
		    
		    int msg_cnt =0;
		    for (int t = toNextTick; t <= horizonMinutes; t += 5) {
		        int ticksAdded = (int)(((minutesPassed + t) / 5) - ticksSoFar);
		        if (ticksAdded <= 0) continue;

		        int proj = Math.min(maxHp, curHp + ticksAdded * regen);
		        sb.append("- ").append(t).append("분 뒤: HP ").append(proj)
		          .append(" / ").append(maxHp).append(NL);

		        msg_cnt++;
		        if(msg_cnt > 5) break;
		        
		        if (proj >= maxHp) break; // 풀피 도달 시 중단
		    }

		    // === 풀 HP까지 남은 시간 계산 ===
		    int hpNeeded = maxHp - curHp;
		    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)regen);
		    int minutesToFull = (toNextTick + (ticksNeeded - 1) * 5);
		    if (minutesToFull < 0) minutesToFull = 0;
		    
		    sb.append(" (풀HP까지 약 ").append(minutesToFull).append("분)").append(NL);
		    
		    String result = sb.toString().trim();

		    return result.isEmpty() ? null : result;
		}
	private static class Resolve {
		boolean killed; String dropCode; int gainExp; boolean lucky; boolean dark; boolean gray;
		boolean shadow; // 그림자 몬스터
		boolean bonusNormalDrop; // [7008] 일반 드랍 +1
	}
	private static class CooldownCheck {
	    final boolean ok; final int remainMinutes; final long remainSeconds;
	    private CooldownCheck(boolean ok, int remainMinutes, long remainSeconds) {
	        this.ok = ok; this.remainMinutes = remainMinutes; this.remainSeconds = remainSeconds;
	    }
	    static CooldownCheck ok() { return new CooldownCheck(true, 0, 0); }
	    static CooldownCheck blockSeconds(long remainSec) {
	        return new CooldownCheck(false, (int)Math.ceil(remainSec/60.0), remainSec);
	    }
	}

	public static class LevelUpResult {
		public int gainedExp, beforeLv, afterLv, beforeExpCur, afterExpCur, afterExpNext, levelUpCount;
		public int hpMaxDelta, atkMinDelta, atkMaxDelta;
		public int critDelta;
		public int hpRegenDelta;
		public int beforeHpMax,   afterHpMax;
		public int beforeAtkMin,  afterAtkMin;
		public int beforeAtkMax,  afterAtkMax;
		public int beforeCrit,    afterCrit;
		public int beforeHpRegen, afterHpRegen;
	}

	private LevelUpResult applyExpAndLevelUp(User u, int gainedExp) {
	    LevelUpResult r = new LevelUpResult();
	    r.gainedExp = Math.max(0, gainedExp);

	    r.beforeLv      = u.lv;
	    r.beforeExpCur  = u.expCur;

	    r.beforeHpMax   = u.hpMax;
	    r.beforeAtkMin  = u.atkMin;
	    r.beforeAtkMax  = u.atkMax;
	    r.beforeCrit    = u.critRate;
	    r.beforeHpRegen = u.hpRegen;

	    int lv      = u.lv;
	    int expCur  = u.expCur + r.gainedExp;
	    int expNext = u.expNext;

	    int hpMax   = u.hpMax;
	    int atkMin  = u.atkMin;
	    int atkMax  = u.atkMax;
	    int crit    = u.critRate;
	    int regen   = u.hpRegen;

	    int hpDelta = 0;
	    int atkMinDelta = 0;
	    int atkMaxDelta = 0;
	    int critDelta   = 0;
	    int regenDelta  = 0;
	    int upCount     = 0;

	    while (expCur >= expNext) {
	        expCur -= expNext;
	        lv++;
	        upCount++;

	        expNext = calcNextExp(lv, expNext);

	     // 🔥 핵심: 레벨 기준 재계산
	        int newHpMax   = MiniGameUtil.calcBaseHpMax(lv);
	        int newAtkMin = MiniGameUtil.calcBaseAtkMin(lv);
	        int newAtkMax = MiniGameUtil.calcBaseAtkMax(lv);
	        int newCrit   = MiniGameUtil.calcBaseCritRate(lv);
	        int newRegen  = MiniGameUtil.calcBaseHpRegen(lv);

	        hpDelta     += (newHpMax   - hpMax);
	        atkMinDelta += (newAtkMin - atkMin);
	        atkMaxDelta += (newAtkMax - atkMax);
	        critDelta   += (newCrit   - crit);
	        regenDelta  += (newRegen  - regen);
	        
	        hpMax   = newHpMax;
	        atkMin = newAtkMin;
	        atkMax = newAtkMax;
	        crit   = newCrit;
	        regen  = newRegen;
	    }

	    u.lv        = lv;
	    u.expCur    = expCur;
	    u.expNext   = expNext;
	    u.hpMax     = hpMax;
	    u.atkMin    = atkMin;
	    u.atkMax    = atkMax;
	    u.critRate  = crit;
	    u.hpRegen   = regen;

	    r.afterLv       = lv;
	    r.afterExpCur   = expCur;
	    r.afterExpNext  = expNext;
	    r.levelUpCount  = upCount;

	    r.afterHpMax    = hpMax;
	    r.afterAtkMin   = atkMin;
	    r.afterAtkMax   = atkMax;
	    r.afterCrit     = crit;
	    r.afterHpRegen  = regen;

	    r.hpMaxDelta    = hpDelta;
	    r.atkMinDelta   = atkMinDelta;
	    r.atkMaxDelta   = atkMaxDelta;
	    r.critDelta     = critDelta;
	    r.hpRegenDelta  = regenDelta;

	    return r;
	}

	private static final int DELTA_BASE = 150;
	private static final int DELTA_LIN  = 120;
	private static final int DELTA_QUAD = 8;
	private static final int NEXT_CAP   = Integer.MAX_VALUE;

	private int calcNextExp(int newLv, int prevExpNext) {
	    long lv = Math.max(1, newLv);
	    long delta = (long)DELTA_BASE + (long)DELTA_LIN * lv + (long)DELTA_QUAD * lv * lv;
	    long next  = (long)prevExpNext + delta;
	    if (next > NEXT_CAP) return NEXT_CAP;
	    return (int) next;
	}

	
	private String buildRegenScheduleSnippetEnhanced2(String userName, String roomName, User u, int horizonMinutes, int currentHp, int hpMax, int regen, int minutesSpan) {

		if (horizonMinutes <= 0 || regen <= 0 || currentHp >= hpMax) return null;
		
	    Timestamp damaged = botNewService.selectLastDamagedTime(userName, roomName);
	    Timestamp lastAtk = botNewService.selectLastAttackTime(userName, roomName);

	    Timestamp from;
	    if (damaged != null && lastAtk != null) {
	        from = lastAtk.after(damaged) ? lastAtk : damaged;
	    } else if (damaged != null) {
	        from = damaged;
	    } else if (lastAtk != null) {
	        from = lastAtk;
	    } else {
	        // ✅ 아무 로그도 없으면 "지금" 기준으로 시작
	        from = Timestamp.from(Instant.now());
	    }

	    long minutesPassed = Math.max(0, Duration.between(from.toInstant(), Instant.now()).toMinutes());
	    long ticksSoFar = minutesPassed / 5;

	    int toNextTick = (int)((5 - (minutesPassed % 5)) % 5);
	    if (toNextTick == 0) toNextTick = 5;

	    StringBuilder sb = new StringBuilder();
	    final String NL = "♬";

	    int msg_cnt =0;
	    for (int t = toNextTick; t <= horizonMinutes; t += 5) {
	        int ticksAdded = (int)(((minutesPassed + t) / 5) - ticksSoFar);
	        if (ticksAdded <= 0) continue;

	        int proj = Math.min(hpMax, currentHp + ticksAdded * regen);
	        sb.append("- ").append(t).append("분 뒤: HP ").append(proj)
	          .append(" / ").append(hpMax).append(NL);

	        msg_cnt++;
	        if(msg_cnt > 5) break;
	        
	        if (proj >= hpMax) break; // 풀피 도달 시 중단
	    }

	    // === 풀 HP까지 남은 시간 계산 ===
	    int hpNeeded = hpMax - currentHp;
	    int ticksNeeded = (int)Math.ceil(hpNeeded / (double)regen);
	    int minutesToFull = (toNextTick + (ticksNeeded - 1) * 5);
	    if (minutesToFull < 0) minutesToFull = 0;
	    
	    sb.append(" (풀HP까지 약 ").append(minutesToFull).append("분)").append(NL);
	    
	    String result = sb.toString().trim();

	    return result.isEmpty() ? null : result;
	
	}
	

	
	
	
	/** 몬스터 요약 한 줄 UI */
	/*
	private String renderMonsterCompactLine(Monster m, int userLv, int nightmareYnVal) {

		// 드랍 아이템명 및 판매가격
	    String dropName = (m.monDrop != null ? m.monDrop : "-");
	    long dropPrice = getDropPriceByName(dropName);

	    boolean nmActive = nightmareYnVal >= 1;
	    boolean hellActive = nightmareYnVal == 2;

	    int monAtk = m.monAtk;
	    int monHp = m.monHp;
	    int monLv = m.monLv;
	    long monExp = m.monExp;
	    if(nmActive) {
	    	monAtk *= NM_MUL_HP_ATK;
	    	monHp *= NM_MUL_HP_ATK;
	    	dropPrice = dropPrice * 50;
	    	if(hellActive) dropPrice *= HEL_SP_MULT; //토끼기준 100a
	    	monLv += hellActive ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
	    	monExp *= NM_MUL_EXP;
	    	if(hellActive) monExp *= HEL_MUL_EXP;
	    }

	    SP dropSp= SP.fromSp(dropPrice);

	    // ATK 범위 계산 (50% ~ 100%)
	    int atkMin = (int) Math.floor(monAtk * 0.5);
	    int atkMax = monAtk;

	 // EXP 보정 계산 (resolveKillAndDrop 과 동일)
	    long baseExp = Math.max(0, monExp);
	    int levelGap = userLv - monLv;
	    double expMultiplier;

	    if (levelGap >= 0) {
	        // 플레이어가 몬스터보다 높을 때 → 패널티
	        expMultiplier = Math.max(0.1, 1.0 - levelGap * 0.1);
	    } else {
	        // 몬스터가 더 강할 때 → 보너스
	        expMultiplier = 1.0 + Math.min(-levelGap, 5) * 0.05; // 레벨 차 1당 5%, 최대 25%
	    }

	    long effExp = Math.round(baseExp * expMultiplier);
	    if(nmActive) {
	    	effExp *= NM_MUL_EXP;
	    	if(hellActive) effExp *= HEL_MUL_EXP;
	    }
	    boolean hasPenalty = (levelGap >= 0 && expMultiplier < 1.0);
	    boolean hasBonus   = (levelGap < 0  && expMultiplier > 1.0);

	    
	    
	    
	    StringBuilder sb = new StringBuilder();

	    // 1행: 기본 정보
	    sb.append(m.monNo).append(". ").append(m.monName).append(" [").append(monLv).append("lv]")
	      .append(" ❤️HP ").append(monHp)
	      .append(" ⚔ATK ").append(atkMin).append("~").append(atkMax)
	      .append(NL);

	    
	 // 🔹 3행: 몬스터 패턴 정보 (mon_patten = 최대 패턴 번호)
	    int patMax = m.monPatten; // 예: 4라면 1~4까지 사용됨
	    if (patMax > 0) {
	        sb.append("▶ 패턴(").append(patMax).append("): ");

	        boolean first = true;
	        for (int pat = 1; pat <= patMax; pat++) {
	            String desc = null;
	            switch (pat) {
	                case 1: desc = "1: 주시"; break;
	                case 2: desc = "2: 공격"; break;
	                case 3: desc = "3: 방어"; break;
	                case 4: desc = "4: 필살기(최댐*1.5)"; break;
	                case 5: desc = "5: 흡혈/즉사급피해"; break; // 필요하면
	                default: break;
	            }

	            if (desc != null) {
	                if (!first) sb.append(", ");
	                sb.append(desc);
	                first = false;
	            }
	        }
	        sb.append(NL);
	    }

	    // 2행: 보상 정보
	    sb.append("▶ 보상: EXP ").append(effExp);
	    if (hasPenalty) sb.append("▼");
	    else if (hasBonus) sb.append("▲");
	    sb.append(" / ").append(dropName).append(" ").append(dropSp.toString()).append("sp")
	      .append(NL);


	    // 🔹 4행: 추가 설명 (mon_note)
	    String note = (m.monNote != null ? m.monNote.trim() : "");
	    if (!note.isEmpty()) {
	        sb.append("※ ").append(note).append(NL);
	    }
	    sb.append(NL);

	    return sb.toString();
	}
	*/
	
	private String renderMonsterSelectLine(Monster m, int nightmareYnVal) {

	    boolean nmActive = nightmareYnVal >= 1;
	    boolean hellActive = nightmareYnVal == 2;

	    int monAtk = m.monAtk;
	    int monHp = m.monHp;
	    int monLv = m.monLv;

	    if (nmActive) {
	        monAtk *= NM_MUL_HP_ATK;
	        monHp *= NM_MUL_HP_ATK;
	        monLv += hellActive ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
	    }

	    int atkMin = (int) Math.floor(monAtk * 0.5);
	    int atkMax = monAtk;

	    return new StringBuilder()
	    	.append("no.").append(m.monNo).append(" ")
	        .append(m.monName)
	        .append(" Lv").append(monLv)
	        .append("  ❤️").append(monHp)
	        .append(" ⚔").append(atkMin).append("~").append(atkMax).append(NL)
	        .toString();
	}
	
	/** 몬스터 최초 토벌 보상 (방별 1명만)
	 *  - 이미 해당 ROOM_NAME에 ACHV_FIRST_CLEAR_MON_{monNo}가 존재하면 스킵
	 *  - 없으면: 해당 유저에게 rewardSp 지급 + CMD 기록
	 */
	
	/*
	private String grantFirstClearIfEligible(
	        String userName,
	        String roomName,
	        Monster m,
	        Map<String, Integer> globalAchvMap  // 🔹 추가
	) {
	    if (m == null) return "";

	    String achvCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;

	    // 1) 글로벌 Map에서 이미 존재하는지 확인
	    int globalCnt = 0;
	    if (globalAchvMap != null) {
	        Integer v = globalAchvMap.get(achvCmd);
	        if (v != null) globalCnt = v.intValue();
	    }
	    if (globalCnt > 0) {
	        // 이미 이 CMD로 기록된 최초 토벌이 있음 → 보상 X
	        return "";
	    }

	    int rewardSp = calcFirstClearReward(m.monNo);
	    

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("scoreExt", "");
	    pr.put("cmd", achvCmd);
	    botNewService.insertPointRank(pr);

	    // (선택) 메모리상으로도 업데이트
	    if (globalAchvMap != null) {
	        globalAchvMap.put(achvCmd, globalCnt + 1);
	    }

	    return "✨ 업적 달성! [" + m.monName + "] 최초 토벌자 보상 +"
	            + formatSpShort(rewardSp) + " 지급되었습니다." + NL;
	}
*/
	
	private static final long LEVEL_ACHV_REWARD_SP = 50_000L; // 5a

	private String grantLevelAchievements(String userName, String roomName,
			Set<String> achievedCmdSet, int afterLv) {
		// 임계값: 10, 50, 100, 150, 200, 이후 100단위
		StringBuilder sb = new StringBuilder();
		try {
			int[] fixed = {10, 50, 100, 150, 200};
			for (int th : fixed) {
				if (afterLv < th) return sb.toString();
				String cmd = "ACHV_LEVEL_" + th;
				if (achievedCmdSet.contains(cmd)) continue;
				sb.append(grantOnceIfEligibleFast(userName, roomName, cmd, (int)LEVEL_ACHV_REWARD_SP, achievedCmdSet));
			}
			for (int th = 300; th <= afterLv; th += 100) {
				String cmd = "ACHV_LEVEL_" + th;
				if (achievedCmdSet.contains(cmd)) continue;
				sb.append(grantOnceIfEligibleFast(userName, roomName, cmd, (int)LEVEL_ACHV_REWARD_SP, achievedCmdSet));
			}
		} catch (Exception ignore) {}
		return sb.toString();
	}

	private String grantOnceIfEligibleFast(
	        String userName,
	        String roomName,
	        String achvCmd,
	        int rewardSp,
	        Set<String> achievedCmdSet
	) {
	    if (rewardSp <= 0) return "";

	    // ✅ 메모리에서만 중복 체크
	    if (achievedCmdSet.contains(achvCmd)) {
	        return "";
	    }

	    HashMap<String,Object> pr = new HashMap<>();
	    pr.put("userName", userName);
	    pr.put("roomName", roomName);
	    pr.put("score", rewardSp);
	    pr.put("scoreExt", "");
	    pr.put("cmd", achvCmd);

	    botNewService.insertPointRank(pr);

	    // ✅ 즉시 Set 갱신 (같은 공격 내 중복 방지)
	    achievedCmdSet.add(achvCmd);

	    return "✨ 업적 달성! [" + achvCmd + "] 보상 +" + formatSpShort(rewardSp) + " 지급되었습니다." + NL;
	}
	

	private boolean isSkeleton(Monster m) {
	    if (m == null) return false;
	    if (m.monNo == 10||m.monNo ==14||m.monNo ==15||m.monNo ==25||m.monNo ==28||m.monNo ==99) return true;
	    if (m.monName.equals("해골")||m.monName.equals("리치")||m.monName.equals("하급악마")
	    		||m.monName.equals("중급악마")||m.monName.equals("미이라")) {
	    	return true;
	    }
	    return false;
	}
	private boolean isAnimal(Monster m) {
		switch(m.monNo) {
			case 1: case 2: case 3: case 4: case 5:
			case 6: case 7: case 8: case 9: /*case 10:*/
			case 11: case 12: case 13: /*case 14: case 15:*/
			case 16: case 17: case 18: case 19: case 20:
			case 21: case 22: case 23: case 24: /*case 25:*/
			case 26: case 27: /*case 28:*/ case 29: case 30:
				return true;
			default:
				return false;
		}
	}
	
	/** 통산 킬수 업적 보상 */
	private int calcTotalKillReward(int threshold, boolean nightmareYn) {
	    return AchievementConfig.killTotalReward(threshold, nightmareYn);
	}

	private int[] buildKillThresholds(int maxThreshold) {
	    return AchievementConfig.buildKillThresholds(maxThreshold);
	}
	/**
	 * 몬스터별(50/100킬) + 통산 킬 업적 처리
	 * - room 단위로 동작
	 * - TBOT_POINT_RANK.CMD 기반 1회성 지급
	 */
	private String grantKillAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        List<KillStat> ksList   // [PERF] selectKillStats 제거 — monsterAttack의 cachedKillStats 재사용
	) {
	    if (ksList == null || ksList.isEmpty()) return "";

	    StringBuilder sb = new StringBuilder();
	    int totalKills = 0;
	    int totalNmKills = 0;
	    int totalHellKills = 0;

	    int[] perMonThresholds = buildKillThresholds(AchievementConfig.KILL_PER_MON_MAX);

	    for (KillStat ks : ksList) {
	        int monNo = ks.monNo;
	        int kills = ks.killCount;
	        totalKills += kills;
	        totalNmKills += ks.nmKillCount;
	        totalHellKills += ks.hellKillCount;

	        for (int th : perMonThresholds) {
	            if (kills < th) break;

	            String cmd = "ACHV_KILL" + th + "_MON_" + monNo;
	            if (achievedCmdSet.contains(cmd)) continue;

	            int reward = th * monNo / 2;

	            sb.append(
	                grantOnceIfEligibleFast(
	                    userName, roomName, cmd, reward, achievedCmdSet
	                )
	            );
	        }
	    }

	    int[] totalThresholds = buildKillThresholds(AchievementConfig.KILL_TOTAL_MAX);

	    for (int th : totalThresholds) {
	        if (totalKills < th) break;

	        String cmd = "ACHV_KILL_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int reward = calcTotalKillReward(th, false);

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, reward, achievedCmdSet
	            )
	        );
	    }

	    for (int th : totalThresholds) {
	        if (totalNmKills < th) break;

	        String cmd = "ACHV_KILL_NIGHTMARE_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int reward = calcTotalKillReward(th, true);

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, reward, achievedCmdSet
	            )
	        );
	    }

	    for (int th : totalThresholds) {
	        if (totalHellKills < th) break;

	        String cmd = "ACHV_KILL_HELL_TOTAL_" + th;
	        if (achievedCmdSet.contains(cmd)) continue;

	        int reward = calcTotalKillReward(th, true) * 2;

	        sb.append(
	            grantOnceIfEligibleFast(
	                userName, roomName, cmd, reward, achievedCmdSet
	            )
	        );
	    }

	    return sb.toString();
	}

	// [OPT3] selectAchievementInventoryCounts 결과(HashMap) → grantLightDarkItemAchievements 호환 List 변환
	private List<HashMap<String,Object>> buildGainRowsFromCounts(HashMap<String,Object> counts) {
	    List<HashMap<String,Object>> list = new java.util.ArrayList<>();
	    if (counts == null) return list;
	    for (String type : new String[]{"DROP3","DROP5","DROP9"}) {
	        int qty = ((Number) counts.getOrDefault(type + "_QTY", 0)).intValue();
	        if (qty > 0) {
	            HashMap<String,Object> row = new HashMap<>();
	            row.put("GAIN_TYPE", type);
	            row.put("TOTAL_QTY", qty);
	            list.add(row);
	        }
	    }
	    return list;
	}

	private String grantLightDarkItemAchievements(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        List<HashMap<String, Object>> gainRows   // [PERF] 호출부에서 프리로드
	) {
	    // 타입별 획득 수량 집계
	    int lightTotal = 0, darkTotal = 0, grayTotal = 0;
	    if (gainRows != null) {
	        for (HashMap<String, Object> row : gainRows) {
	            String type = Objects.toString(row.get("GAIN_TYPE"), "");
	            int qty = MiniGameUtil.parseIntSafe(Objects.toString(row.get("TOTAL_QTY"), "0"));
	            if (AchievementConfig.ITEM_TYPE_LIGHT.equals(type)) lightTotal = qty;
	            else if (AchievementConfig.ITEM_TYPE_DARK.equals(type)) darkTotal = qty;
	            else if (AchievementConfig.ITEM_TYPE_GRAY.equals(type)) grayTotal = qty;
	        }
	    }
	    if (lightTotal <= 0 && darkTotal <= 0 && grayTotal <= 0) return "";

	    // AchievementConfig.ITEM_ACHIEVEMENTS 기반으로 미달성 업적 부여
	    StringBuilder sb = new StringBuilder();
	    for (AchievementConfig.ItemEntry def : AchievementConfig.ITEM_ACHIEVEMENTS) {
	        if (achievedCmdSet.contains(def.cmd)) continue;

	        int total;
	        if      (AchievementConfig.ITEM_TYPE_LIGHT.equals(def.gainType)) total = lightTotal;
	        else if (AchievementConfig.ITEM_TYPE_DARK.equals(def.gainType))  total = darkTotal;
	        else if (AchievementConfig.ITEM_TYPE_GRAY.equals(def.gainType))  total = grayTotal;
	        else continue;

	        if (total >= def.threshold) {
	            sb.append(grantOnceIfEligibleFast(
	                userName, roomName, def.cmd, def.rewardSp, achievedCmdSet
	            ));
	        }
	    }
	    return sb.toString();
	}

	
	/*
	private String grantCelebrationClearBonus(
	        String userName,
	        String roomName,
	        Map<String, Integer> globalAchvMap,
	        Map<String, Integer> userAchvMap,
	        int myLv   // [PERF] selectUser 제거 — 호출부에서 ctx.user.lv 전달
	) {

	    StringBuilder sb = new StringBuilder();

	    List<Monster> mons = botNewService.selectAllMonsters();

	    for (Monster m : mons) {

	        String firstCmd = "ACHV_FIRST_CLEAR_MON_" + m.monNo;     // 최초토벌 기록
	        String userCmd  = "ACHV_CLEAR_BROADCAST_MON_" + m.monNo; // 유저 축하보상 기록

	        // 1) 해당 몬스터가 최초토벌된 적이 있는가? (글로벌)
	        int globalCnt = 0;
	        if (globalAchvMap != null) {
	            Integer v = globalAchvMap.get(firstCmd);
	            if (v != null) globalCnt = v.intValue();
	        }
	        if (globalCnt == 0) {
	            // 아직 아무도 이 몬스터를 최초토벌하지 않음 → 축하 보상 X
	            continue;
	        }
	        
	     // ⭐ NEW ①: 내가 이 몬스터의 최초토벌자인 경우 → 축하보상 스킵
	        int myFirstCnt = 0;
	        if (userAchvMap != null) {
	            Integer v = userAchvMap.get(firstCmd); // firstCmd = ACHV_FIRST_CLEAR_MON_X
	            if (v != null) myFirstCnt = v.intValue();
	        }
	        if (myFirstCnt > 0) {
	            // 나는 이미 이 몬스터의 '최초토벌' 업적을 가진 사람 → 축하보상 대상에서 제외
	            continue;
	        }

	        // ⭐ NEW ②: 내 레벨이 몬스터 레벨 미만이면 축하보상 스킵
	        
	        if (myLv + 30 < m.monLv) {
	            continue;
	        }
	        

	        // 2) 나는 축하보상을 이미 받았는가? (유저 기준)
	        int mine = 0;
	        if (userAchvMap != null) {
	            Integer mineCnt = userAchvMap.get(userCmd);
	            if (mineCnt != null) mine = mineCnt.intValue();
	        }
	        if (mine > 0) {
	            // 이미 이 몬스터에 대한 축하 보상을 받은 상태
	            continue;
	        }
	        
	        

	        // 3) 최초토벌 보상의 1/3 계산
	        int rewardFull   = calcFirstClearReward(m.monNo);
	        int rewardShared = Math.max(1, rewardFull / 3);

	        // 4) 축하 보상 지급
	        HashMap<String,Object> pr = new HashMap<>();
	        pr.put("userName", userName);
	        pr.put("roomName", roomName);
	        pr.put("score", rewardShared);
	        pr.put("scoreExt", "");
	        pr.put("cmd", userCmd);
	        botNewService.insertPointRank(pr);

	        // (선택) 메모리 캐시 업데이트
	        if (userAchvMap != null) {
	            userAchvMap.put(userCmd, mine + 1);
	        }

	        sb.append("✨ [")
	          .append(m.monName)
	          .append("] 최초토벌 축하 보상 +")
	          .append( formatSpShort(rewardShared) ).append(" 지급되었습니다!")
	          .append(NL);
	    }

	    return sb.toString();
	}


	private int calcFirstClearReward(int monNo) {
	    switch(monNo) {
	        case 1: case 2: case 3: case 4: case 5: return 100;
	        case 6: return 300;
	        case 7: return 500;
	        case 8: return 500;
	        case 9: return 1000;
	        case 10: return 1000;
	        case 11: return 1000;
	        case 12: return 1000;
	        case 13: return 1500;
	        case 14: return 1500;
	        case 15: return 5000;
	        case 16: return 5000;
	        case 17: return 5000;
	        case 18: return 15000;
	        case 19: return 25000;
	        case 20: return 35000;
	        case 21: return 45000;
	        case 22: return 60000;
	        case 23: return 75000;
	        case 24: return 100000;
	        case 25: return 200000;
	        case 26: return 200000;
	        case 27: return 200000;
	        case 28: return 200000;
	        case 29: return 250000;
	        case 30: return 300000;
	    }
	    return 0;
	}
	
	*/
	
	/**
	 * 업적 리스트를:
	 * - 축하보상 숨기고
	 * - 통산 처치 / 몬스터별 킬 / 죽음 극복 은 [..] 형태로 묶어서 출력
	 */
	// 업적 문자열 패턴
	
	private void renderAchievementLinesCompact(
	        StringBuilder sb,
	        List<HashMap<String, Object>> achv,
	        Map<Integer, Monster> monMap) {
		renderAchievementLinesCompact(sb, achv, monMap, 0);
	}

	private void renderAchievementLinesCompact(
	        StringBuilder sb,
	        List<HashMap<String, Object>> achv,
	        Map<Integer, Monster> monMap,
	        int nmBagCount) {

	    // ===== 패턴 =====
		Pattern P_BAG_GET =
				Pattern.compile("^가방 획득 (\\d+)회 달성$");
	    Pattern P_TOTAL_KILL =
	            Pattern.compile("^통산 처치 (\\d+)회 달성$");
	    Pattern P_TOTAL_NIGHTMARE_KILL =
	    		Pattern.compile("^나이트메어 통산 처치 (\\d+)회 달성$");
	    Pattern P_TOTAL_HELL_KILL =
	    		Pattern.compile("^헬 통산 처치 (\\d+)회 달성$");
	    Pattern P_DEATH_OVERCOME =
	            Pattern.compile("^죽음 극복 (\\d+)회 달성$");
	    Pattern P_MONSTER_KILL =
	            Pattern.compile("^(.+?) (\\d+)킬 달성$");
	    Pattern P_LIGHT_ITEM_GET =
	            Pattern.compile("^빛 아이템 획득 (\\d+)회 달성$");
	    Pattern P_DARK_ITEM_GET =
	            Pattern.compile("^어둠 아이템 획득 (\\d+)회 달성$");
	    Pattern P_GRAY_ITEM_GET =
	    		Pattern.compile("^음양 아이템 획득 (\\d+)회 달성$");
	    Pattern P_ATTACK_COUNT =
	            Pattern.compile("^통산 공격 (\\d+)회 달성$");
	    Pattern P_JOB_SKILL =
	            Pattern.compile("^(.+?) 스킬 사용 (\\d+)회 달성$");

	    // ===== 집계용 =====
	    SortedSet<Integer> bagGetSteps = new TreeSet<>();
	    SortedSet<Integer> totalKillSteps = new TreeSet<>();
	    SortedSet<Integer> totalNmKillSteps = new TreeSet<>();
	    SortedSet<Integer> hellKillSteps = new TreeSet<>();
	    SortedSet<Integer> deathSteps     = new TreeSet<>();
	    SortedSet<Integer> attackSteps   = new TreeSet<>();
	    SortedSet<Integer> lightSteps    = new TreeSet<>();
	    SortedSet<Integer> darkSteps     = new TreeSet<>();
	    SortedSet<Integer> graySteps     = new TreeSet<>();

	    Map<String, Integer> monsterKills = new LinkedHashMap<>();
	    Map<String, SortedSet<Integer>> jobSkillSteps = new LinkedHashMap<>();

	    List<String> firstClears = new ArrayList<>();

	    // ===== 수집 =====
	    for (HashMap<String, Object> row : achv) {
	        if (row == null) continue;

	        String cmd = Objects.toString(row.get("CMD"), "");
	        String label = formatAchievementLabelSimple(cmd, monMap);
	        if (label == null || label.isEmpty()) continue;

	        label = label.replace("✨", "").trim();

	        if (label.contains("축하보상")) continue;

	        Matcher m;

	        if ((m = P_BAG_GET.matcher(label)).matches()) {
	        	bagGetSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_TOTAL_KILL.matcher(label)).matches()) {
	            totalKillSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_TOTAL_NIGHTMARE_KILL.matcher(label)).matches()) {
	        	totalNmKillSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_TOTAL_HELL_KILL.matcher(label)).matches()) {
	        	hellKillSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_DEATH_OVERCOME.matcher(label)).matches()) {
	            deathSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_ATTACK_COUNT.matcher(label)).matches()) {
	            attackSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_LIGHT_ITEM_GET.matcher(label)).matches()) {
	            lightSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_DARK_ITEM_GET.matcher(label)).matches()) {
	            darkSteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	            continue;
	        }
	        if ((m = P_GRAY_ITEM_GET.matcher(label)).matches()) {
	        	graySteps.add(MiniGameUtil.parseIntSafe(m.group(1)));
	        	continue;
	        }
	        if ((m = P_JOB_SKILL.matcher(label)).matches()) {
	            String job = m.group(1).trim();
	            int v = MiniGameUtil.parseIntSafe(m.group(2));
	            jobSkillSteps
	                .computeIfAbsent(job, k -> new TreeSet<>())
	                .add(v);
	            continue;
	        }
	        if ((m = P_MONSTER_KILL.matcher(label)).matches()) {
	            String mon = m.group(1).trim();
	            int v = MiniGameUtil.parseIntSafe(m.group(2));
	            monsterKills.put(mon, Math.max(monsterKills.getOrDefault(mon, 0), v));
	            continue;
	        }

	        // 최초 토벌
	        if (label.startsWith("최초토벌")) {
	            firstClears.add(label.replace("최초토벌:", "").trim());
	        }
	    }

	    // ===== 출력 =====

	    // 1️⃣ 통산 기록 (최대값만)
	    sb.append("✨통산기록").append(NL);

	    
	    // 줄1: 공격/죽음
	    int _atk = attackSteps.isEmpty() ? 0 : attackSteps.last();
	    int _dth = deathSteps.isEmpty() ? 0 : deathSteps.last();
	    sb.append("공격 ").append(String.format("%,d", _atk)).append("회")
	      .append(" / 죽음 ").append(String.format("%,d", _dth)).append("회").append(NL);
	    // 줄2: 일반/나메/헬 처치
	    int _kill = totalKillSteps.isEmpty() ? 0 : totalKillSteps.last();
	    int _nmKill = totalNmKillSteps.isEmpty() ? 0 : totalNmKillSteps.last();
	    int _hellKill = hellKillSteps.isEmpty() ? 0 : hellKillSteps.last();
	    sb.append("처치 ").append(String.format("%,d", _kill)).append("마리")
	      .append(" / 나메 ").append(String.format("%,d", _nmKill)).append("마리")
	      .append(" / 헬 ").append(_hellKill).append("마리").append(NL);
	    // 줄3: 빛/어둠/음양
	    int _lt = lightSteps.isEmpty() ? 0 : lightSteps.last();
	    int _dk = darkSteps.isEmpty() ? 0 : darkSteps.last();
	    int _gy = graySteps.isEmpty() ? 0 : graySteps.last();
	    sb.append("빛 ").append(_lt).append(" / 어둠 ").append(_dk).append(" / 음양 ").append(_gy).append(NL);
	    // 줄4: 가방/나메가방
	    int _bag = bagGetSteps.isEmpty() ? 0 : bagGetSteps.last();
	    sb.append("가방 ").append(_bag).append(" / 나메가방 ").append(nmBagCount).append(NL);
	    sb.append(NL);

	    // 2️⃣ 스킬 숙련 (3개씩)
	    if (!jobSkillSteps.isEmpty()) {
	        sb.append("✨스킬 숙련").append(NL);

	        List<String> rows = new ArrayList<>();
	        for (Map.Entry<String, SortedSet<Integer>> e : jobSkillSteps.entrySet()) {
	            rows.add(e.getKey() + " " + String.format("%,d", e.getValue().last()) + "회");
	        }

	        for (int i = 0; i < rows.size(); i += 3) {
	            sb.append(String.join(" / ",
	                    rows.subList(i, Math.min(i + 3, rows.size()))))
	              .append(NL);
	        }
	        sb.append(NL);
	    }

	    // 4️⃣ 몬스터 처치 (3개씩)
	    if (!monsterKills.isEmpty()) {
	        sb.append("✨몬스터 처치").append(NL);

	        List<String> rows = new ArrayList<>();
	        for (Map.Entry<String, Integer> e : monsterKills.entrySet()) {
	            rows.add(e.getKey() + ": " + String.format("%,d", e.getValue()) + "킬");
	        }

	        for (int i = 0; i < rows.size(); i += 3) {
	            sb.append(String.join(" / ",
	                    rows.subList(i, Math.min(i + 3, rows.size()))))
	              .append(NL);
	        }
	    }
	}

	private String formatAchievementLabelSimple(String cmd, Map<Integer, Monster> monMap) {
	    if (cmd == null || cmd.isEmpty()) return "";

	    // 작은 헬퍼: monNo → 이름 (monMap에서만 조회)
	    java.util.function.Function<Integer, String> findMonName = (Integer monNo) -> {
	        if (monNo == null) return "몬스터#" + monNo;
	        Monster m = null;
	        if (monMap != null) {
	            m = monMap.get(monNo);
	        }
	        return (m == null ? ("몬스터#" + monNo) : m.monName);
	    };

	    // 🔹 최초토벌
	    if (cmd.startsWith("ACHV_FIRST_CLEAR_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_FIRST_CLEAR_MON_".length()));
	            String name = findMonName.apply(monNo);
	            return "✨최초토벌: " + name;
	        } catch (Exception e) {
	            return "최초토벌";
	        }
	    }

	    // 🔹 최초토벌 축하보상
	    if (cmd.startsWith("ACHV_CLEAR_BROADCAST_MON_")) {
	        try {
	            int monNo = Integer.parseInt(cmd.substring("ACHV_CLEAR_BROADCAST_MON_".length()));
	            String name = findMonName.apply(monNo);
	            return "✨축하보상: " + name;
	        } catch (Exception e) {
	            return "축하보상";
	        }
	    }

	    // 🔹 몬스터별 킬 업적: ACHV_KILL10_MON_3 이런 형태 가정
	    if (cmd.startsWith("ACHV_KILL") && cmd.contains("_MON_")) {
	        try {
	            String[] parts = cmd.substring("ACHV_KILL".length()).split("_MON_");
	            int threshold = Integer.parseInt(parts[0]);   // 10
	            int monNo = Integer.parseInt(parts[1]);       // 3
	            String name = findMonName.apply(monNo);
	            return name + " " + threshold + "킬 달성";
	        } catch (Exception e) {
	            return "킬 업적";
	        }
	    }

	    // 🔹 통산 킬 업적
	    if (cmd.startsWith("ACHV_KILL_TOTAL_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_KILL_TOTAL_".length()));
	            return "통산 처치 " + th + "회 달성";
	        } catch (Exception e) {
	            return "통산 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_KILL_NIGHTMARE_TOTAL_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_KILL_NIGHTMARE_TOTAL_".length()));
	    		return "나이트메어 통산 처치 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "나이트메어 통산 업적";
	    	}
	    }
	    if (cmd.startsWith("ACHV_KILL_HELL_TOTAL_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_KILL_HELL_TOTAL_".length()));
	    		return "헬 통산 처치 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "헬 통산 업적";
	    	}
	    }

	    // 🔹 데스 업적
	    if (cmd.startsWith("ACHV_DEATH_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_DEATH_".length()));
	            return "죽음 극복 " + th + "회 달성";
	        } catch (Exception e) {
	            return "죽음 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_SHOP_SELL_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_SHOP_SELL_".length()));
	    		return "상점 판매 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "상점 판매 ";
	    	}
	    }
	    if (cmd.startsWith("ACHV_LIGHT_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_LIGHT_ITEM_".length()));
	    		return "빛 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "빛 아이템 획득";
	    	}
	    }
	    if (cmd.startsWith("ACHV_DARK_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_DARK_ITEM_".length()));
	    		return "어둠 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "어둠 아이템 획득 ";
	    	}
	    }
	    if (cmd.startsWith("ACHV_GRAY_ITEM_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_GRAY_ITEM_".length()));
	    		return "음양 아이템 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "음양 아이템 획득 ";
	    	}
	    }
	    
	    if (cmd.startsWith("ACHV_ATTACK_TOTAL_")) {
	        try {
	            int th = Integer.parseInt(cmd.substring("ACHV_ATTACK_TOTAL_".length()));
	            return "통산 공격 " + th + "회 달성";
	        } catch (Exception e) {
	            return "통산 공격 업적";
	        }
	    }
	    if (cmd.startsWith("ACHV_BAG_")) {
	    	try {
	    		int th = Integer.parseInt(cmd.substring("ACHV_BAG_".length()));
	    		return "가방 획득 " + th + "회 달성";
	    	} catch (Exception e) {
	    		return "가방 획득 업적";
	    	}
	    }

	    if (cmd.startsWith("ACHV_JOB_SKILL_")) {
	        try {
	            String rest = cmd.substring("ACHV_JOB_SKILL_".length()); // "궁수_10"
	            String[] parts = rest.split("_");
	            if (parts.length >= 2) {
	                String jobName = parts[0];               // 궁수, 사신, 기사...
	                int th = Integer.parseInt(parts[1]);     // 10
	                return jobName + " 스킬 사용 " + th + "회 달성";
	            } else {
	                return "직업 스킬 사용 업적";
	            }
	        } catch (Exception e) {
	            return "직업 스킬 사용 업적";
	        }
	    }
	    

	    return cmd;
	}

	
	private String grantDeathAchievements(String userName, String roomName) {
	    StringBuilder sb = new StringBuilder();
	    int deaths = 0;

	    try {
	        AttackDeathStat stat = botNewService.selectAttackDeathStats(userName, roomName);
	        deaths = (stat == null ? 0 : stat.getTotalDeaths());
	    } catch (Exception ignore) { /* 안전무시 */ }

	    for (int[] r : AchievementConfig.DEATH) {
	        int threshold = r[0];
	        int rewardSp  = r[1];

	        if (deaths >= threshold) {
	            String cmd = "ACHV_DEATH_" + threshold;
	            int already = 0;
	            try {
	                already = botNewService.selectPointRankCountByCmdUserInRoom(roomName, userName, cmd);
	            } catch (Exception ignore) {}

	            if (already == 0) {
	                try {
	                    HashMap<String, Object> p = new HashMap<>();
	                    p.put("userName", userName);
	                    p.put("roomName", roomName);
	                    p.put("score", rewardSp);
	                    p.put("scoreExt", "");
	                    p.put("cmd", cmd);
	                    botNewService.insertPointRank(p);

	                    sb.append("✨ 죽음 ").append(threshold)
	                      .append("회 달성 보상 +").append( formatSpShort(rewardSp))
	                      .append(" 지급!♬");
	                } catch (Exception ignore) {}
	            }
	        }
	    }
	    return sb.toString();
	}
	
	private String grantBagAcquireAchievementsFast(
	        String userName,
	        String roomName,
	        Set<String> achievedCmdSet,
	        int bagTotal   // [PERF] 호출부에서 프리로드
	) {

	    if (bagTotal <= 0) return "";

	    // 기존 업적 스타일과 동일한 threshold
	    int[] thresholds = {
	            1, 5, 10, 30, 50, 100,
	            200, 300, 500, 700,
	            1000, 1500, 2000
	    };

	    StringBuilder sb = new StringBuilder();

	    for (int th : thresholds) {
	        if (bagTotal >= th) {
	            String cmd = "ACHV_BAG_" + th;

	            if (!achievedCmdSet.contains(cmd)) {
	                sb.append(
	                    grantOnceIfEligibleFast(
	                        userName,
	                        roomName,
	                        cmd,
	                        calcBagAchvReward(th),
	                        achievedCmdSet
	                    )
	                );
	            }
	        }
	    }

	    return sb.toString();
	}

	private int calcBagAchvReward(int th) {
	    if (th >= 2000) return 20000;
	    if (th >= 1500) return 15000;
	    if (th >= 1000) return 12000;
	    if (th >= 700)  return 8000;
	    if (th >= 500)  return 6000;
	    if (th >= 300)  return 4000;
	    if (th >= 100)  return 2500;
	    if (th >= 50)   return 1500;
	    if (th >= 10)   return 800;
	    if (th >= 5)    return 400;
	    return 200;
	}

	
	private int calcUserEffectiveAtkMax(User u, String roomName) {

	    // -------------------------------
	    // 1) 기본값
	    // -------------------------------
	    int atkMax = u.atkMax;
	    // -------------------------------
	    // 2) MARKET 아이템 버프 
	    //    (selectOwnedMarketBuffTotals 사용)
	    // -------------------------------
	    HashMap<String, Number> buffs = null;
	    try {
	        buffs = botNewService.selectOwnedMarketBuffTotals(u.userName, roomName);
	    } catch(Exception ignore){}

	    int bAtkMax = (buffs != null && buffs.get("ATK_MAX") != null)
	                    ? buffs.get("ATK_MAX").intValue()
	                    : 0;

	    atkMax += bAtkMax;

	    //int weaponBonus = getWeaponAtkBonus(0); // 25강부터 +1
	    // 네 구조: max ATK 는 무기레벨 만큼 +1 per level
	    //atkMax += weaponBonus;

	    // -------------------------------
	    // 6) 최소 하한선
	    // -------------------------------
	    if (atkMax < 1) atkMax = 1;

	    return atkMax;
	}
	// 전체방 기준 도사 버프 존재 여부 확인 (방 제한 없음)
	private DosaBuffEffect loadGlobalDosaBuffAndBuild() {
	    HashMap<String,Object> dosaBuff = botNewService.selectDosaBuffInfo();
	    if (dosaBuff == null) return null;
	    return new DosaBuffEffect();
	}

	public static class DosaBuffEffect {
	    // 효과는 고정값: 최종 데미지 +1000 flat, +5%, 최대체력 5% 회복
	    // (필드는 메시지 빌더용으로 유지)
	}

	private DamageOutcome calculateDamage(
	        User u,
	        Monster m,
	        Flags flags,
	        int effAtkMin,
	        int effAtkMax,
	        int critRate,
	        int critDmg,
	        double berserkMul,
	        int monHpRemainBefore,
	        int hpMax,
	        int beforeJobSkillYn,
	        boolean nightmareYn
	) {
	    return calculateDamage(u, m, flags, effAtkMin, effAtkMax, critRate, critDmg,
	            berserkMul, monHpRemainBefore, hpMax, beforeJobSkillYn, nightmareYn, Collections.emptySet());
	}

	private DamageOutcome calculateDamage(
	        User u,
	        Monster m,
	        Flags flags,
	        int effAtkMin,
	        int effAtkMax,
	        int critRate,
	        int critDmg,
	        double berserkMul,
	        int monHpRemainBefore,
	        int hpMax,
	        int beforeJobSkillYn,
	        boolean nightmareYn,
	        Set<Integer> ownedBossItems
	) {
	    DamageOutcome out = new DamageOutcome();
	    AttackCalc calc = new AttackCalc();
	    calc.jobSkillUsed = false;

	    StringBuilder extraMsg = new StringBuilder();
	    out.dmgCalcMsg="";
	    out.hunterMsg="";

	    int orgCritRateForGungsa = critRate;
	    if (critRate > 100) {
	    	int overflow = critRate - 100;
	    	double convertRate = MiniGameUtil.getHunterConvertRate(u.hunterGrade);
	    	if(!"헌터".equals(u.job)) {
	    		convertRate /= 10;
	    	}
	    	int converted = (int)Math.floor(overflow * convertRate);
	        critRate = 100;
	        critDmg  += converted;

	        // 디버그용
	        out.hunterMsg += "헌터랭크(" + u.hunterGrade + ")보너스 "
			               + "over치명률 " + overflow + "% → 치피"
			               + converted + "%로 변환(" + Math.round(convertRate*100) + "%)" + NL;
	    }

	    // -----------------------------
	    // 1) 공격력 굴림 + 크리티컬
	    // -----------------------------
	    int critRoll = ThreadLocalRandom.current().nextInt(0, 101);
	    int critThreshold = effAtkRateLimit(critRate); // 안전빵 방어
	    boolean crit = (critRoll <= critThreshold);

	    int baseAtk = (effAtkMax <= effAtkMin)
	            ? effAtkMin
	            : ThreadLocalRandom.current().nextInt(effAtkMin, effAtkMax + 1);

	    double critMultiplier = Math.max(1.0, critDmg / 100.0);
	    
	    // -----------------------------
	    // 2) 추가데미지 로직
	    // -----------------------------
	    
	    if ("궁사".equals(u.job)) {
	           // 1) 연사 횟수 계산 (최소데미지 비율 기반, 최소 2연사 ~ 최대 5연사)
	        // range/max 비율이 클수록 연사 증가 (ex. max=130000,min=65000 → 50% → 5연사)
	        int range     = Math.max(0, effAtkMax - effAtkMin);
	        double rangeRatio = (effAtkMax > 0) ? (double) range / effAtkMax : 0.0;
	        int hitCount;
	        if      (rangeRatio >= 0.50) hitCount = 5; // 50%이상 → 5연사
	        else if (rangeRatio >= 0.30) hitCount = 4; // 30~49% → 4연사
	        else if (rangeRatio >= 0.10) hitCount = 3; // 10~29% → 3연사
	        else                         hitCount = 2; //  0~9%  → 2연사
	        if (ownedBossItems.contains(7003)) hitCount = Math.min(hitCount + 1, 6); // [7003] 연사수 +1 (최대 6연사)

	        
	        calc.multiAttack =hitCount;
	        int totalDmg = 0;
	        StringBuilder multiMsg = new StringBuilder();

	        if (hitCount > 1) {
	            multiMsg.append("궁사의 연사 발동! ")
	                    .append(hitCount).append("연사").append(NL);
	        }

	        // 2) 크리티컬 분배
	        //  - 1타는 무조건 크리
	        //  - 나머지 (2~hitCount) 샷에 대해 남은 크리율을 균등 분배
	        int remainingCritBudget = Math.max(0, orgCritRateForGungsa); // 100은 1타 확정크리용
	        double perHitRateRaw = (hitCount > 1)
	                ? (double) remainingCritBudget / (hitCount - 1)
	                : 0.0;

	        // 2~마지막샷까지 개별 최대 80%
	        if (perHitRateRaw > 80.0) {
	            perHitRateRaw = 80.0;
	        }
	        double perHitRate = perHitRateRaw; // 0.0 ~ 80.0

	        boolean allCrit = true; // 전탄 크리 체크용

	        for (int i = 1; i <= hitCount; i++) {
	            int shotAtk;
	            shotAtk = (int)Math.round(ThreadLocalRandom.current().nextInt(effAtkMin, effAtkMax + 1) *0.7);
	            
	            // 3) 크리 판정
	            boolean shotCrit;
	            if (i == 1) {
	                // 1타는 확정 크리
	                shotCrit = true;
	            } else {
	                int roll = ThreadLocalRandom.current().nextInt(0, 101);
	                shotCrit = (roll <= perHitRate);
	            }

	            int shotDmg = shotCrit
	                    ? (int) Math.round(shotAtk * critMultiplier*0.70)
	                    : shotAtk;

	            totalDmg += shotDmg;
	            if (!shotCrit) {
	                allCrit = false;
	            }

	            if (hitCount > 1) {
	                multiMsg.append(i).append("타: ").append(shotDmg);
	                if (shotCrit) multiMsg.append(" (치명!)");
	                multiMsg.append(NL);
	            }
	        }

	        // 4) 전탄 크리 보너스 (1.1배)
	        if (hitCount > 1 && allCrit) {
	            int before = totalDmg;
	            totalDmg = (int) Math.round(totalDmg * 1.3);
	            multiMsg.append("ALL 치명! ")
	                    .append(before).append(" → ").append(totalDmg)
	                    .append(" (+30%)").append(NL);
	            calc.jobSkillUsed =true;
	        } else if (hitCount > 1) {
	            // 기존 총합 안내
	            multiMsg.append("총합 데미지: ").append(totalDmg).append("!").append(NL);
	        }

	        // 이후 공통 로직에서는 "한 번의 큰 타격"처럼 처리되지만
	        // 실제로는 위에서 연사 데미지로 합산한 값이 들어간다.
	        baseAtk = totalDmg;
	        crit = false;           // 샷별로 이미 크리 반영했으므로 여기서는 의미없음

	        // 궁사 전용 계산 메시지를 out에 남김
	        out.dmgCalcMsg += multiMsg.toString();
	    }
	    
	    if ("검성".equals(u.job)) {
	    	double skillRate = 0.065;
	    	if(ownedBossItems.contains(7005)) {
	    		skillRate += 0.15;
	    	}
	    	
	    	if (ThreadLocalRandom.current().nextDouble() < skillRate) {
        		out.dmgCalcMsg += "바람가르기! "+baseAtk+"→";
        		baseAtk = (int)Math.round(baseAtk * 4);
        		out.dmgCalcMsg += baseAtk+NL;
        		out.dmgCalcMsg += "몬스터가 바람에 갇혀 행동불가가 됨!";
        		calc.jobSkillUsed = true;
            	flags.monPattern = 1;
			}
	    	
	    }
	    
	    if ("도박사".equals(u.job)) {

            int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1~100
            int multiplier = 1;

            if (roll <= 1)       multiplier = 100/2;
            else if (roll <= 3)  multiplier = 50/2;
            else if (roll <= 6)  multiplier = 33/2;
            else if (roll <= 10) multiplier = 25/2;
            else if (roll <= 15) multiplier = 20/2;
            else if (roll <= 21) multiplier = 16/2;
            else if (roll <= 28) multiplier = 14/2;
            else if (roll <= 36) multiplier = 12/2;
            else if (roll <= 45) multiplier = 11/2;
            else if (roll <= 55) multiplier = 10/2;
            else {
                // ❌ 실패
                //baseAtk = 0;
                crit = false;
                calc.jobSkillUsed = false;
                out.dmgCalcMsg = "도박 실패!(크리티컬해제)";
                multiplier=1;
            }

            // 🎯 성공
            int before = baseAtk;
            baseAtk = baseAtk * multiplier;

            if(roll<=10) {
            	calc.jobSkillUsed = true;
            }
            if(roll <=55 ) {
	            out.dmgCalcMsg =
	                "도박 성공! (피해량 ×" + multiplier + ") "
	                + before + " ⇒ " + baseAtk + "!";
            }
        }
	    
	    boolean isSnipe = false;
	    if ("궁수".equals(u.job)) {
	        if (ThreadLocalRandom.current().nextDouble() < 0.07) {
	            isSnipe = true;
	            baseAtk = baseAtk * 7;
	            calc.jobSkillUsed = true;
	            crit = false;
	        }
	    }
	    if ("사냥꾼".equals(u.job) && isAnimal(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 2.00);
	    }

	    if ("어둠사냥꾼".equals(u.job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 2.00);
	    }
	    if ("용사".equals(u.job) && isSkeleton(m)) {
	    	baseAtk = (int) Math.round(baseAtk * 1.25);
	    }
	    
	    //모든직업 berserk 는 상위에서 계산하도록 
    	baseAtk = (int) Math.round(berserkMul * baseAtk);
	    int rawAtkDmg = crit ? (int) Math.round(baseAtk * critMultiplier) : baseAtk;

	    if ("곰".equals(u.job)) {
	    	// 🐻 공격 시 최대체력 10% 소모
	        int hpCost = (int)Math.round(hpMax * 0.10);
	        int beforeHp = u.hpCur;
	        u.hpCur = Math.max(1, u.hpCur - hpCost);

	        out.dmgCalcMsg += "곰의 괴력! 체력 -" + hpCost +
	                " (" + beforeHp + " → " + u.hpCur + ")" + NL;

	        int monHp = m.monHp;
	        if(nightmareYn) {
	        	monHp *= NM_MUL_HP_ATK;
	        }
	        
	        if (hpMax < monHp) {

	            baseAtk = 0;
	            rawAtkDmg = 0;

	            out.dmgCalcMsg += "곰의 힘이 부족하다... 공격 실패!" + NL;

	        }else {

	            double rnd = ThreadLocalRandom.current().nextDouble();

	            // 2️⃣ 거짓 이벤트 (10%)
	            if (rnd < 0.10) {

	                // 7014 달의부름: 50% 확률로 실패 → 성공 전환
	                if (ownedBossItems != null && ownedBossItems.contains(7014)
	                        && ThreadLocalRandom.current().nextDouble() < 0.50) {
	                    out.dmgCalcMsg += "달의 힘을 받아 반달가슴곰이 되었습니다... " + NL;
	                    baseAtk = monHpRemainBefore;
	                    rawAtkDmg = monHpRemainBefore;
	                    out.dmgCalcMsg += "곰은 몬스터를 찢었다!" + NL;
	                } else {
	                    baseAtk = 0;
	                    rawAtkDmg = 0;
	                    calc.jobSkillUsed = true;
	                    out.dmgCalcMsg += "달의 힘을 받아 문이 되었습니다... 공격 실패!" + NL;
	                }

	            } else {

	                // 3️⃣ 공격 성공 (즉사)
	                baseAtk = monHpRemainBefore;
	                rawAtkDmg = monHpRemainBefore;

	                out.dmgCalcMsg += "곰은 몬스터를 찢었다!" + NL;

	                // 4️⃣ 달의 힘 (10%)
	                if (rnd < 0.20) {

	                    int before = u.hpCur;
	                    u.hpCur = hpMax;
	                    calc.jobSkillUsed = true;
	                    out.dmgCalcMsg += "달의 힘을 받아 체력 회복! "
	                            + "(" + before + " → " + u.hpCur + "/" + hpMax + ")" + NL;
	                }
	            }
	        }

	        
	        
	    }
	    
	    // -----------------------------
	    // 3) 원턴킬 선판정
	    // -----------------------------
	    boolean lethal = rawAtkDmg >= monHpRemainBefore;

	    if (lethal) {
	        flags.atkCrit = crit;
	        flags.monPattern = 0;
	        flags.snipe = isSnipe;

	        calc.atkDmg = rawAtkDmg;
	        calc.monDmg = 0;
	        calc.patternMsg = null;

	        if (crit) {
	            calc.baseAtk = baseAtk;
	            calc.critMultiplier = critMultiplier;
	        }

	    } else {
	        // -----------------------------
	        // 4) 보스 패턴 포함 실제 데미지 계산
	        // -----------------------------
	    	boolean beforeCalc = calc.jobSkillUsed;
	    	int beforeMultiAttackCnt = calc.multiAttack;
	        calc = calcDamage(u, m, flags, baseAtk, crit, critMultiplier, nightmareYn);
	        calc.jobSkillUsed = beforeCalc;
	        calc.multiAttack = beforeMultiAttackCnt;
	        
	        flags.atkCrit = crit;
	        flags.snipe = isSnipe;
	        flags.finisher = (flags.monPattern >= 4); // 패턴4=필살기
	        
	        
	        
	        if ("처단자".equals(u.job) ) {
	        	if(flags.monPattern == 3) {
		        	// 패턴3 → 방어 대신 무행동 취급
		            flags.monPattern = 1;
	
		            // ✅ 방어 적용 전 기준( baseAtk * critMultiplier )으로 다시 계산
		            int originalDmg = (int) Math.round(calc.baseAtk * calc.critMultiplier);
	
		            int newDmg = (int) Math.round(originalDmg * 2.5);
		            calc.atkDmg = newDmg;
		            calc.monDmg = 0;  // 방어 패턴이었으니 몬스터 피해는 0 유지
	
		            // 디버그용 계수도 실제 데미지에 맞게 재계산
		            if (calc.baseAtk > 0) {
		                calc.critMultiplier = (double) newDmg / calc.baseAtk;
		            }
	
		            calc.patternMsg = "처단자의 방어파괴! (피해 2.5배)";
	        	}
	        }

	        // 🛡 전사: 보스 필살기 패링 (20% 확률)
	        if ("검성".equals(u.job)) {
	        	if (flags.finisher && calc.monDmg > 0) {
		            if (ThreadLocalRandom.current().nextDouble() < 0.15) {

		                int bossSkillDmg = calc.monDmg;             // 보스 필살기 데미지
		                int reflectTotal = calc.atkDmg + bossSkillDmg; // 되돌려줄 총 피해

		                calc.atkDmg += bossSkillDmg;  // 되받아친 만큼 공격에 누적
		                calc.monDmg = 0;              // 나는 피해 없음

		                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		                calc.patternMsg = baseMsg
		                        + "패링! 몬스터의 필살기를 되받아쳐 총 "
		                        + reflectTotal + " 피해를 입히고 피해를 받지 않았습니다.";

		                calc.jobSkillUsed = true;
		            }
		        }else if (!flags.finisher && calc.monDmg > 0) {
		        	if (ThreadLocalRandom.current().nextDouble() < 0.15) {
		        		int bossSkillDmg = calc.monDmg;             // 보스 필살기 데미지
		                int reflectTotal = calc.atkDmg + bossSkillDmg; // 되돌려줄 총 피해

		                calc.atkDmg += bossSkillDmg;  // 되받아친 만큼 공격에 누적
		                calc.monDmg = 0;              // 나는 피해 없음

		                String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
		                calc.patternMsg = baseMsg
		                        + "패링! 몬스터의 공격를 되받아쳐 총 "
		                        + reflectTotal + " 피해를 입히고 피해를 받지 않았습니다.";

		                calc.jobSkillUsed = true;
		        	}
		        }
	        }
	        
	        
	        if ("용사".equals(u.job) && calc.monDmg > 0 && !flags.finisher) {
	            int reduced = (int) Math.floor(calc.monDmg * 0.3);
	            if (reduced < 1) reduced = 1;
	            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = baseMsg + "(받는 피해 70% 감소 → " + reduced + ")";
	            calc.monDmg = reduced;
	        }
	        
	        if ("어둠사냥꾼".equals(u.job) && calc.monDmg > 0 && !flags.finisher) {
	        	int reduced = (int) Math.floor(calc.monDmg * 0.7);
	        	if (reduced < 1) reduced = 1;
	        	String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	        	calc.patternMsg = baseMsg + "(받는 피해 30% 감소 → " + reduced + ")";
	        	calc.monDmg = reduced;
	        }
	        
	        if ("사냥꾼".equals(u.job) && flags.finisher && flags.monPattern==6 ) {
	        	calc.atkDmg = rawAtkDmg*5;
	        	calc.monDmg = 0;
	        	calc.endBattle = false;
	        	calc.patternMsg = "도망가는 적을 붙잡아 강력한 일격!" + rawAtkDmg*5 + " 피해";
	        }
	        if ("어둠사냥꾼".equals(u.job) && flags.finisher && flags.monPattern==6 ) {
	        	calc.atkDmg = rawAtkDmg*5;
			    calc.monDmg = 0;
			    calc.endBattle = false;
			    calc.patternMsg = "도망가는 적을 붙잡아 강력한 일격!" + rawAtkDmg*5 + " 피해";
	        }
	        if ("곰".equals(u.job) && flags.monPattern == 6) {
	            calc.atkDmg = 0;
	            calc.monDmg = 0;
	            calc.endBattle = false;
	            calc.patternMsg = "기절했지만 곰의 야성으로 버텼습니다!";
	        }
	        
	        if ("복수자".equals(u.job)) {
		        if (calc.monDmg > 0 && flags.monPattern == 2 || flags.monPattern == 4) {
		        	//flags.monPattern =2 이면 2배 , 4이면 4배 
		            int revengeDmg = (int) Math.round(calc.monDmg * flags.monPattern);
		            //int orgMonDmg = calc.monDmg ;
		            int newMonDmg = (int) Math.round(calc.monDmg*0.25) ;
		            int revengeDmg2 =(int) Math.round((u.hpCur-newMonDmg) * calc.critMultiplier *0.2); 
		            
		            
		            calc.atkDmg += revengeDmg;
		            calc.atkDmg += revengeDmg2;
		            calc.monDmg = newMonDmg ;
		            
		            out.dmgCalcMsg += NL
		            	+ " → 받은 피해 "+newMonDmg +NL
		                + " → 반격 데미지 "+revengeDmg  +NL
		                + " → 현재체력&크리보너스 "+revengeDmg2 +NL;
		            

		            if (newMonDmg > 0 && calc.atkDmg >= monHpRemainBefore) {//willkill, 복수로죽였을때만 적용
		            	int heal = (int) Math.round(hpMax * 0.10);
		            	int before =u.hpCur-newMonDmg;
			            u.hpCur = Math.min(hpMax, before + heal);

			            calc.patternMsg = "복수에 성공했다..! " + heal +
			                    " 회복 (HP " + before + " → " + u.hpCur + "/" + hpMax + ")";
			            calc.jobSkillUsed = true;
			            calc.monDmg = 0;
		            }
		        }
		    }
	        // [7005] 가시갑옷: 받은 피해의 10% 반사
	        if (ownedBossItems.contains(7005) && calc.monDmg > 0) {
	            int reflect = Math.max(1, (int)Math.round(calc.monDmg * 0.10));
	            calc.atkDmg += reflect;
	            String baseMsg7005 = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	            calc.patternMsg = baseMsg7005 + "[가시갑옷] " + reflect + " 반사!";
	        }

	     // 몬스터 공격 변동 처리 (회피 / 증폭)
	        if ("도박사".equals(u.job)) {
		        if (calc.monDmg > 0 ) {
	
		            int roll = ThreadLocalRandom.current().nextInt(1, 101); // 1~100
		            String baseMsg = (calc.patternMsg == null ? "" : calc.patternMsg + " ");
	
		            if (roll <= 11) {
		                // 🌀 회피
		                calc.monDmg = 0;
		                calc.patternMsg = NL+baseMsg + "도박대성공! (회피판정 → "+0+")";
		            }
		            else if (roll <= 44) {
		            	int increased = calc.monDmg /2;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박성공! (받는 피해 50% → " + increased + ")";
		            }
		            else if (roll <= 88) {
		                // 💥 2배 피해
		                int increased = calc.monDmg * 2;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박실패! (받는 피해x2 → " + increased + ")";
		            }
		            else {
		                // ☠ 3배 피해
		                int increased = calc.monDmg * 3;
		                calc.monDmg = increased;
		                calc.patternMsg = NL+baseMsg + "도박대실패! (받는 피해x3 → " + increased + ")";
		            }
		        }
	        }
	    }
	    out.calc = calc;
	    out.flags = flags;
	    out.willKill = (calc.atkDmg >= monHpRemainBefore);
	    out.extraMsg = extraMsg.toString();

	    return out;
	}

	// 크리율 방어용 헬퍼 (0~100 clamp 용)
	private int effAtkRateLimit(int rate) {
	    if (rate < 0) return 0;
	    if (rate > 100) return 100;
	    return rate;
	}
	

	private String buildJobDescriptionList() {
		StringBuilder sb = new StringBuilder();
		sb.append("♬ /직업 [직업명] 으로 전직 가능합니다.").append(NL);
		sb.append("http://rgb-tns.dev-apc.com/loa/job-view").append(NL);
	    sb.append("♬♬ 전직 가능한 직업 목록").append(ALL_SEE_STR);
	    for (JobDef def : MiniGameUtil.JOB_DEFS.values()) {
	    	sb.append(def.name).append(":");
	        sb.append(def.listLine).append(NL);
	        sb.append(def.attackLine).append(NL).append(NL);
	        
	    }
	    
	    return sb.toString();
	}

	
	private String normalizeJob(String raw) {
		 if (raw == null) return null;
		    String s = raw.trim();

		    JobDef def = MiniGameUtil.JOB_DEFS.get(s);
		    return (def != null ? def.name : null);
	}


	
	private SP pickBiasedSp(long min, long max) {
	    double r = ThreadLocalRandom.current().nextDouble();
	    double biased = Math.pow(r, 6);

	    long span = max - min;
	    long value = min + Math.round(span * biased); // (int) 캐스팅 제거 — int 범위 초과 시 오버플로우 방지
	    SP sp = SP.fromSp(value);
	    return sp;
	}

	private String buildUnifiedDosaBuffMessage(DosaBuffEffect self, DosaBuffEffect room, int actualHeal) {
	    return buildUnifiedDosaBuffMessage(self, room, actualHeal, 1);
	}
	private String buildUnifiedDosaBuffMessage(DosaBuffEffect self, DosaBuffEffect room, int actualHeal, int coef) {
	    int buffCount = (self != null ? 1 : 0) + (room != null ? 1 : 0);
	    int flatBonus = buffCount * 1000 * coef;
	    int rateBonus = buffCount * 5 * coef;
	    StringBuilder sb = new StringBuilder("※도사 기원: 최종 데미지 +").append(flatBonus);
	    if (rateBonus > 0) sb.append(", +").append(rateBonus).append("%");
	    if (coef > 1) sb.append(" [7012 ×").append(coef).append("]");
	    if (actualHeal > 0) sb.append(", HP +").append(actualHeal).append(" 회복");
	    return sb.toString();
	}
	
	// ===== 장비 카테고리별 최대 소지 수량 =====
	private int getEquipCategoryMax(int itemId) {
		for (EquipCategory c : MiniGameUtil.EQUIP_CATEGORIES) {
	        if (c.contains(itemId)) {
	            return c.max;
	        }
	    }

	    return Integer.MAX_VALUE;
	}

	private int getMaxAllowedByCategoryLabel(String label) {
	    if (label.contains("무기"))  return 5;    // 100번대 , 1100번대 
	    if (label.contains("투구"))  return 1;    // 200번대
	    if (label.contains("갑옷"))  return 1;    // 400번대
	    if (label.contains("날개"))  return 1;    // 800번대
	    if (label.contains("전설"))  return 1;    // 700번대

	    // 나머지(행운/반지/토템/선물/유물 등)
	    return Integer.MAX_VALUE;
	}
	
	/**
	 * 같은 "장비 카테고리"인지 판별
	 *  - 여기서 말하는 카테고리는 위 제한이 걸리는 4개(무기/투구/갑옷/전설)
	 */
	private boolean isSameEquipCategory(int baseItemId, int otherItemId) {
		for (EquipCategory c : MiniGameUtil.EQUIP_CATEGORIES) {
	        if (c.contains(baseItemId) && c.contains(otherItemId)) {
	            return true;
	        }
	    }

	    return false;
	}

	private int getCurrentEquipCategoryHolding(String userName, String roomName, int baseItemId) {

		List<HashMap<String, Object>> inv = botNewService.selectInventorySummaryAll(userName, roomName);

		if (inv == null || inv.isEmpty()) {
			return 0;
		}

		int count = 0;
		for (HashMap<String, Object> row : inv) {
			if (row == null)
				continue;

			Object oItemId = row.get("ITEM_ID");
			if (!(oItemId instanceof Number))
				continue;

			int itemId = ((Number) oItemId).intValue();

// baseItemId 와 같은 장비 카테고리인지 체크
			if (!isSameEquipCategory(baseItemId, itemId))
				continue;

// 장비인지 한 번 더 필터 (ITEM_TYPE 이 MARKET 인 것만)
			String itemType = Objects.toString(row.get("ITEM_TYPE"), "");
			if (!("MARKET".equalsIgnoreCase(itemType)||"MARKET2".equalsIgnoreCase(itemType) ))
				continue;

// TOTAL_QTY 가 0 이면 사실상 미보유로 간주
			Object oQty = row.get("TOTAL_QTY");
			int qty = (oQty instanceof Number) ? ((Number) oQty).intValue() : 0;
			if (qty <= 0)
				continue;

// ✅ 장비 제한은 "행 개수" 기준으로 +1
			count++;
		}
		return count;
	}
	
	/**
	 * 장비아이템 카테고리 수량 제한 체크
	 *
	 * @return null 이면 OK, 문자열이면 에러 메시지
	 */
	private String checkEquipCategoryLimit(String userName,
	                                       String roomName,
	                                       int itemId,
	                                       int gainQty) {

	    if (gainQty <= 0) {
	        return null; // 실제로 얻는 수량이 없으면 체크 안 함
	    }

	    int maxAllowed = getEquipCategoryMax(itemId);
	    if (maxAllowed == Integer.MAX_VALUE) {
	        // 제한 없는 카테고리 (행운/반지/토템/선물/유물 등)
	        return null;
	    }

	    // 현재 인벤토리 기준 해당 카테고리 총합
	    int current = getCurrentEquipCategoryHolding(userName, roomName, itemId);

	    if (current + gainQty > maxAllowed) {
	        // 메시지는 네 스타일에 맞게
	        return "❌ 장비 카테고리 수량 제한으로 인해 행동이 불가능합니다."
	             + NL
	             + "현재 카테고리 보유 수량: " + current
	             + "개 / 최대 " + maxAllowed + "개, 판매 후 구매해주세요. ";
	    }

	    return null;
	}
	
	private String resolveItemCategory(int itemId) {
		if (itemId >= 3000 && itemId < 4000)  return "※지옥";
		if (itemId > 9000 && itemId < 10000) return "※유물";
		if (itemId > 8000 && itemId < 9000)  return "※업적";
		if (itemId >= 7000 && itemId < 8000) return "※보스";

		for (EquipCategory c : MiniGameUtil.EQUIP_CATEGORIES) {
	        if (c.contains(itemId)) {
	            return "※" + c.name;
	        }
	    }

	    return "※기타";
	}
	// 카테고리명 또는 숫자로 범위를 구하는 함수
	private int[] resolveCategoryRange(String raw) {
		if (raw == null) return null;
	    String s = raw.trim();

	    if (s.isEmpty()) return null;

	    // 문자 카테고리
	    for (EquipCategory c : MiniGameUtil.EQUIP_CATEGORIES) {
	        if (c.matchAlias(s)) {
	            return c.firstRange();
	        }
	    }

	    // 숫자 카테고리
	    if (s.matches("\\d+") && s.endsWith("00")) {
	        int num = Integer.parseInt(s);
	        return new int[]{num, num + 100};
	    }

	    return null;
	}
	
	
	


	private Date truncateToDate(Date d) {
	    Calendar c = Calendar.getInstance();
	    c.setTime(d);
	    c.set(Calendar.HOUR_OF_DAY, 0);
	    c.set(Calendar.MINUTE, 0);
	    c.set(Calendar.SECOND, 0);
	    c.set(Calendar.MILLISECOND, 0);
	    return c.getTime();
	}
	
	private java.sql.Timestamp toTimestamp(Object obj) {
	    if (obj == null) return null;

	    if (obj instanceof java.sql.Timestamp) {
	        return (java.sql.Timestamp) obj;
	    }
	    if (obj instanceof java.util.Date) {
	        return new java.sql.Timestamp(((java.util.Date) obj).getTime());
	    }
	    if (obj instanceof String) {
	        String s = ((String) obj).trim();
	        if (s.isEmpty()) return null;

	        // 1) yyyy-MM-dd HH:mm:ss 형태 시도
	        try {
	            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	            java.util.Date d = fmt.parse(s);
	            return new java.sql.Timestamp(d.getTime());
	        } catch (Exception ignore) {}

	        // 2) yyyyMMddHHmmss 형태 시도
	        try {
	            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
	            java.util.Date d = fmt.parse(s);
	            return new java.sql.Timestamp(d.getTime());
	        } catch (Exception ignore) {}

	        // 3) 위 포맷이 아니면, 그냥 null 취급
	        return null;
	    }

	    // 예상 밖 타입이면 null
	    return null;
	}
	
	
	
	

	private String buildRelicSummaryLine(List<HashMap<String, Object>> bag,int number) {
		int sumAtkMin = 0;
		int sumAtkMax = 0;
		int sumHp = 0;
		int sumRegen = 0;
		int sumCrit = 0;
		int sumCritDmg = 0;
		int sumAtkRate = 0;
		int sumHpRate = 0;
		int relicCount = 0;
		try {
			if (bag == null)
				return null;

			for (HashMap<String, Object> row : bag) {
				int itemId = safeInt(row.get("ITEM_ID"));
				if (itemId < number || itemId >= number+1000)
					continue;

				relicCount++;

				sumAtkMin += safeInt(row.get("ATK_MIN"));
				sumAtkMax += safeInt(row.get("ATK_MAX"));
				sumHp += safeInt(row.get("HP_MAX"));
				sumRegen += safeInt(row.get("HP_REGEN"));
				sumCrit += safeInt(row.get("ATK_CRI"));
				sumCritDmg += safeInt(row.get("CRI_DMG"));
				sumAtkRate += safeInt(row.get("ATK_MAX_RATE"));
				sumHpRate += safeInt(row.get("HP_MAX_RATE"));
			}

		} catch (Exception e) {
			return null;
		}

		if (relicCount == 0)
			return null;

		StringBuilder sb = new StringBuilder();
		if(number==8000) {
			sb.append("✨ 업적 효과 (").append(relicCount).append("개): ");
		}else if(number==9000) {
			sb.append("✨ 유물 효과 (").append(relicCount).append("개): ");
		}

		boolean first = true;

		if (sumAtkMin != 0 || sumAtkMax != 0) {
			sb.append("ATK ").append(sumAtkMin).append("~").append(sumAtkMax);
			first = false;
		}
		if (sumAtkRate > 0) {
			if (!first)
				sb.append(", ");
			sb.append("최종ATK +").append(sumAtkRate).append("%");
			first = false;
		}
		if (sumHp > 0 || sumHpRate > 0) {
			if (!first)
				sb.append(", ");
			sb.append("HP +").append(sumHp);
			if (sumHpRate > 0)
				sb.append(" (+").append(sumHpRate).append("%)");
			first = false;
		}
		if (sumRegen > 0) {
			if (!first)
				sb.append(", ");
			sb.append("체젠 +").append(sumRegen);
			first = false;
		}
		if (sumCrit > 0 || sumCritDmg > 0) {
			if (!first)
				sb.append(", ");
			sb.append("치확 +").append(sumCrit).append("% / 치뎀 +").append(sumCritDmg).append("%");
		}
		


		return sb.toString();
	}

	private String buildGroupSummaryLine(List<HashMap<String, Object>> bag, int minId, int maxId, String label) {
		int sumAtkMin = 0, sumAtkMax = 0, sumHp = 0, sumRegen = 0;
		int sumCrit = 0, sumCritDmg = 0, sumAtkRate = 0, sumHpRate = 0;
		int count = 0;
		if (bag == null) return null;
		for (HashMap<String, Object> row : bag) {
			int itemId = safeInt(row.get("ITEM_ID"));
			if (itemId < minId || itemId >= maxId) continue;
			count++;
			sumAtkMin  += safeInt(row.get("ATK_MIN"));
			sumAtkMax  += safeInt(row.get("ATK_MAX"));
			sumHp      += safeInt(row.get("HP_MAX"));
			sumRegen   += safeInt(row.get("HP_REGEN"));
			sumCrit    += safeInt(row.get("ATK_CRI"));
			sumCritDmg += safeInt(row.get("CRI_DMG"));
			sumAtkRate += safeInt(row.get("ATK_MAX_RATE"));
			sumHpRate  += safeInt(row.get("HP_MAX_RATE"));
		}
		if (count == 0) return null;
		StringBuilder sb = new StringBuilder();
		sb.append("※").append(label).append("합계(").append(count).append("개): ");
		boolean first = true;
		if (sumAtkMin != 0 || sumAtkMax != 0) {
			sb.append("ATK ").append(sumAtkMin).append("~").append(sumAtkMax); first = false;
		}
		if (sumAtkRate != 0) {
			if (!first) sb.append(", ");
			sb.append("최종ATK +").append(sumAtkRate).append("%"); first = false;
		}
		if (sumHp != 0) {
			if (!first) sb.append(", ");
			sb.append("HP +").append(sumHp); first = false;
		}
		if (sumHpRate != 0) {
			if (!first) sb.append(", ");
			sb.append("체력% +").append(sumHpRate); first = false;
		}
		if (sumRegen != 0) {
			if (!first) sb.append(", ");
			sb.append("체젠 +").append(sumRegen); first = false;
		}
		if (sumCrit != 0 || sumCritDmg != 0) {
			if (!first) sb.append(", ");
			sb.append("치확 +").append(sumCrit).append("% / 치뎀 +").append(sumCritDmg).append("%");
		}
		return sb.toString();
	}



	public static String formatSpShort(long sp) {
		if (sp < 10_000) {
	        return String.format("%,dsp", sp);
	    }

	    double unit = 10_000.0;
	    int index = 0;

	    while (sp >= unit * 10_000) {
	        unit *= 10_000;
	        index++;
	    }

	    double result = sp / unit;
	    char suffix = (char) ('a' + index);

	    return trimDecimal(result) + suffix + " sp";
	}

	private static String trimDecimal(double v) {
		if (v == (long) v) {
	        return String.format("%,d", (long) v);
	    } else {
	        return String.format("%,.2f", v);
	    }
	}
	
	private static String formatDateYMD(Date d) {
	    if (d == null) return "-";
	    return new java.text.SimpleDateFormat("yyyy-MM-dd").format(d);
	}

	private static String formatDateMD(Date d) {
	    if (d == null) return "-";
	    return new java.text.SimpleDateFormat("MM월dd일").format(d);
	}
	
	
	


	// ─── 데미지 시뮬레이터 API ──────────────────────────────────────────
	@GetMapping("/api/dmg-sim")
	@ResponseBody
	public ResponseEntity<?> getDmgSim(
	        @RequestParam(defaultValue = "") String userName) {

	    HashMap<String, Object> map = new HashMap<>();
	    map.put("userName", userName);
	    map.put("roomName", "");
	    map.put("param1",   "");

	    UserBattleContext ctx = calcUserBattleContext(map);
	    if (!ctx.success) {
	        HashMap<String, Object> err = new HashMap<>();
	        err.put("error", ctx.errorMessage);
	        return ResponseEntity.ok(err);
	    }

	    String job = (ctx.job == null ? "" : ctx.job.trim());
	    double jobDmgMul = getJobDmgMulForSim(job);

	    // ─ 공격력 단계별 계산 ───────────────────────────────────────────
	    List<Map<String, Object>> atkSteps = new ArrayList<>();

	    int curMin = ctx.baseAtkMin;
	    int curMax = ctx.baseAtkMax;
	    atkSteps.add(simStep("기본 공격력", ctx.baseAtkMin, ctx.baseAtkMax, curMin, curMax, "캐릭터 기본 스탯"));

	    if (ctx.mktAtkMin != 0 || ctx.mktAtkMax != 0) {
	        curMin += ctx.mktAtkMin;
	        curMax += ctx.mktAtkMax;
	        atkSteps.add(simStep("아이템/버프 합산", ctx.mktAtkMin, ctx.mktAtkMax, curMin, curMax, "장비 · 천벌 · 세트(가산) · 헌터보너스 포함"));
	    }

	    if (ctx.mktAtkMaxRate > 0) {
	        int rMin = (int)((long)curMin * ctx.mktAtkMaxRate / 100);
	        int rMax = (int)((long)curMax * ctx.mktAtkMaxRate / 100);
	        curMin += rMin; curMax += rMax;
	        atkSteps.add(simStep("ATK 비율 보너스", rMin, rMax, curMin, curMax, "+" + ctx.mktAtkMaxRate + "%"));
	    }

	    int bossMin = 0, bossMax = 0;
	    List<String> bossNotes = new ArrayList<>();
	    if (ctx.ownedBossItems.contains(7009)) {
	        int effLv = Math.min(ctx.user.lv, 300);
	        int b = effLv * 150;
	        bossMin += b; bossMax += b;
	        bossNotes.add("[7009] 진화무기 Lv" + effLv + "(max300) × 150 = +" + b);
	    }
	    if (ctx.ownedBossItems.contains(7013)) {
	        int yest = Math.min(getYesterdayAttackerCountCached(), 30);
	        int b = yest * 500;
	        bossMin += b; bossMax += b;
	        bossNotes.add("[7013] 어제의전사들 " + yest + "명(max30) × 500 = +" + b);
	    }
	    if (bossMin > 0) {
	        curMin += bossMin; curMax += bossMax;
	        atkSteps.add(simStep("보스 아이템", bossMin, bossMax, curMin, curMax, String.join(" / ", bossNotes)));
	    }

	    if (ctx.hellNerfAtkMin > 0 || ctx.hellNerfAtkMax > 0) {
	        double hellMult = MiniGameUtil.getHellNerfMult(ctx.hunterGrade);
	        if (ctx.ownedBossItems.contains(7007)) hellMult = Math.max(0.0, hellMult + 0.03);
	        curMin -= ctx.hellNerfAtkMin; curMax -= ctx.hellNerfAtkMax;
	        atkSteps.add(simStep("헬 너프",
	            -ctx.hellNerfAtkMin, -ctx.hellNerfAtkMax, curMin, curMax,
	            String.format("등급 %s → %.0f%% 너프", ctx.hunterGrade, hellMult * 100)));
	    }

	    if (ctx.setAtkFinalRate > 0) {
	        int sfMin = (int)Math.round((long)curMin * ctx.setAtkFinalRate / 100.0);
	        int sfMax = (int)Math.round((long)curMax * ctx.setAtkFinalRate / 100.0);
	        curMin += sfMin; curMax += sfMax;
	        atkSteps.add(simStep("세트 최종 비율", sfMin, sfMax, curMin, curMax, "+" + ctx.setAtkFinalRate + "%"));
	    }

	    if (ctx.dropAtkMin > 0 || ctx.dropAtkMax > 0) {
	        curMin += ctx.dropAtkMin; curMax += ctx.dropAtkMax;
	        atkSteps.add(simStep("드랍 보너스", ctx.dropAtkMin, ctx.dropAtkMax, curMin, curMax, "필드 드랍 아이템 보너스"));
	    }

	    // 직업 배율 후 실전 공격 범위
	    boolean isBear = "곰".equals(job);
	    int effMin, effMax;
	    if (isBear) {
	        // 곰: hpMax가 실전 공격력 → ATK 누적치와 차이(HP 기여분)를 마지막 스텝으로 표시
	        int hpContribMin = ctx.atkMin - curMin;
	        int hpContribMax = ctx.atkMax - curMax;
	        if (hpContribMin != 0 || hpContribMax != 0) {
	            curMin = ctx.atkMin; curMax = ctx.atkMax;
	            atkSteps.add(simStep("곰 HP→ATK 전환",
	                hpContribMin, hpContribMax, curMin, curMax,
	                "기본HP + 아이템HP + 공격력×크리데미지% 합산 = 최대HP"));
	        }
	        effMin = ctx.atkMin; effMax = ctx.atkMax;
	    } else {
	        effMin = (int)Math.round((long)ctx.atkMin * 100 * jobDmgMul / 100);
	        effMax = (int)Math.round((long)ctx.atkMax * 100 * jobDmgMul / 100);
	        atkSteps.add(simStep(String.format("직업 배율 ×%.1f (%s)", jobDmgMul, job),
	            effMin - ctx.atkMin, effMax - ctx.atkMax, effMin, effMax, ""));
	    }

	    double critMul = Math.max(1.0, ctx.critDmg / 100.0);
	    int critMin = (int)Math.round(effMin * critMul);
	    int critMax = (int)Math.round(effMax * critMul);

	    // ─ 응답 조립 ─────────────────────────────────────────────────────
	    HashMap<String, Object> result = new HashMap<>();
	    HashMap<String, Object> userInfo = new HashMap<>();
	    userInfo.put("userName",    ctx.userName);
	    userInfo.put("job",         job);
	    userInfo.put("lv",          ctx.user.lv);
	    userInfo.put("nightmareYn", ctx.user.nightmareYn);
	    userInfo.put("hunterGrade", ctx.hunterGrade);
	    userInfo.put("bossItems",   new ArrayList<>(ctx.ownedBossItems));
	    result.put("user", userInfo);

	    result.put("atkSteps", atkSteps);
	    result.put("isBear", isBear);

	    HashMap<String, Object> eff = new HashMap<>();
	    eff.put("min", effMin); eff.put("max", effMax);
	    result.put("eff", eff);

	    HashMap<String, Object> crit = new HashMap<>();
	    crit.put("rate", ctx.crit);
	    crit.put("dmg",  ctx.critDmg);
	    crit.put("mul",  String.format("%.2f", critMul));
	    crit.put("min",  critMin);
	    crit.put("max",  critMax);
	    result.put("crit", crit);

	    HashMap<String, Object> hp = new HashMap<>();
	    hp.put("base",    ctx.baseHpMax);
	    hp.put("mkt",     ctx.mktHpMax);
	    hp.put("rateBonus", ctx.mktHpMaxRate);
	    hp.put("jobBonus",  ctx.jobHp);
	    hp.put("hellNerf",  ctx.hellNerfHp);
	    hp.put("total",     ctx.hpMax);
	    hp.put("regen",     ctx.regen);
	    hp.put("hpCur",     ctx.hpCur);
	    result.put("hp", hp);

	    return ResponseEntity.ok(result);
	}

	@GetMapping("/dmg-sim-view")
	public String dmgSimView() {
	    return "nonsession/loa/dmg_sim_view";
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 장비 랭킹 API + View
	// ─────────────────────────────────────────────────────────────────────────
	@ResponseBody
	@GetMapping("/api/equip-rank")
	public Object getEquipRank() {
	    if (EQUIP_RANK_CACHE == null || EQUIP_RANK_CACHE.isEmpty()) {
	        return Collections.singletonMap("error", "데이터 준비 중입니다. 잠시 후 다시 시도해주세요.");
	    }
	    HashMap<String, Object> resp = new HashMap<>();
	    resp.put("list", EQUIP_RANK_CACHE);
	    resp.put("cachedAt", EQUIP_RANK_CACHE_TS);
	    return resp;
	}

	/** 크론 배치용: 장비랭킹 캐시 갱신 (1시간마다 호출) */
	public void refreshEquipRankCache() {
	    List<HashMap<String, Object>> users;
	    try {
	        users = botNewService.selectAllUsersForRank();
	    } catch (Exception e) {
	        System.out.println("[CRON-equip-rank] 유저 목록 조회 실패: " + e.getMessage());
	        return;
	    }

	    List<HashMap<String, Object>> result = new ArrayList<>();
	    for (HashMap<String, Object> row : users) {
	        String uName = Objects.toString(row.get("USER_NAME"), "");
	        if (uName.isEmpty()) continue;
	        HashMap<String, Object> ctxMap = new HashMap<>();
	        ctxMap.put("userName",    uName);
	        ctxMap.put("roomName",    "");
	        ctxMap.put("_rankMode",   Boolean.TRUE);  // GP/hpCur 쿼리 스킵
	        ctxMap.put("_noJobBonus", Boolean.TRUE);  // 직업 보너스 제외 (공평 비교)
	        ctxMap.put("_forceHell",  Boolean.TRUE);  // 헬모드 기준 너프 적용
	        UserBattleContext ctx;
	        try {
	            ctx = calcUserBattleContext(ctxMap);
	        } catch (Exception e) { continue; }
	        if (!ctx.success) continue;

	        int lv = ctx.user != null ? ctx.user.lv : 0;
	        boolean has7009 = ctx.ownedBossItems.contains(7009);
	        boolean has7013 = ctx.ownedBossItems.contains(7013);

	        // 표시용 보스템 보너스 (맥스치 기준, 너프 전 원본)
	        int maxBossBonus = 0;
	        if (has7009) maxBossBonus += Math.min(lv, 300) * 150;
	        if (has7013) maxBossBonus += 30 * 500;

	        // ctx.atkMax 에는 이미 7009(실제lv)+7013(실제cappedYest)가 헬너프 적용된 채로 포함됨.
	        // 추정MAX = ctx.atkMax + [7013의 (max30-실제) 차분] × 헬너프유지율 + dropAtkMax
	        int cappedYest = Math.min(getYesterdayAttackerCountCached(), 30);
	        int extra7013  = has7013 ? (30 - cappedYest) * 500 : 0;
	        // ctx.hellNerfRate = 유지비율(e.g. 0.1=10%유지). 헬너프 없으면 0 → 1.0 처리
	        double keepFraction = ctx.hellNerfRate > 0 ? ctx.hellNerfRate : 1.0;
	        int extra7013Nerfed = (int) Math.round(extra7013 * keepFraction);

	        List<String> setDesc = new ArrayList<>();
	        if (ctx.setAtkFinalRate  > 0) setDesc.add("ATK+" + ctx.setAtkFinalRate + "%");
	        if (ctx.setCritFinalRate > 0) setDesc.add("크리+" + ctx.setCritFinalRate + "%");
	        if (ctx.setCooldownReduce> 0) setDesc.add("쿨-"  + ctx.setCooldownReduce + "s");
	        if (ctx.setEvasionRate   > 0) setDesc.add("회피+" + ctx.setEvasionRate + "%");
	        if (ctx.activeSetSpecials != null) {
	            for (String sp : ctx.activeSetSpecials) setDesc.add(sp.replace("SPECIAL_", ""));
	        }

	        // 헬너프율: 백분율 정수 (예: 5 → "5%")
	        int hellNerfPct = (int) Math.round(ctx.hellNerfRate * 100);

	        HashMap<String, Object> entry = new HashMap<>();
	        entry.put("userName",    uName);
	        entry.put("lv",          lv);
	        entry.put("atkMin",      ctx.atkMin);
	        entry.put("atkMax",      ctx.atkMax);
	        entry.put("crit",        ctx.crit);
	        entry.put("critDmg",     ctx.critDmg);
	        entry.put("darkAtkMin",  ctx.dropAtkMin);
	        entry.put("darkAtkMax",  ctx.dropAtkMax);
	        entry.put("setInfo",     String.join(" / ", setDesc));
	        entry.put("bossBonus",   maxBossBonus);
	        entry.put("has7009",     has7009);
	        entry.put("has7013",     has7013);
	        // bossEstMax: ctx.atkMax(7009+7013실제분 헬너프포함) + 7013차분(헬너프적용) + dropAtkMax
	        entry.put("bossEstMin",  ctx.atkMin + ctx.dropAtkMin + extra7013Nerfed);
	        entry.put("bossEstMax",  ctx.atkMax + ctx.dropAtkMax + extra7013Nerfed);
	        entry.put("hellNerfPct", hellNerfPct);
	        result.add(entry);
	    }

	    result.sort((a, b) -> Integer.compare(
	        b.get("bossEstMax") != null ? ((Number) b.get("bossEstMax")).intValue() : 0,
	        a.get("bossEstMax") != null ? ((Number) a.get("bossEstMax")).intValue() : 0
	    ));
	    EQUIP_RANK_CACHE = result;
	    EQUIP_RANK_CACHE_TS = System.currentTimeMillis();
	    System.out.println("[CRON-equip-rank] 갱신 완료: " + result.size() + "명");
	}

	@SuppressWarnings("unused")
	private Object getEquipRankLegacy() {
	    List<HashMap<String, Object>> users;
	    try {
	        users = botNewService.selectAllUsersForRank();
	    } catch (Exception e) {
	        return Collections.singletonMap("error", "유저 목록 조회 실패: " + e.getMessage());
	    }

	    List<HashMap<String, Object>> result = new ArrayList<>();

	    for (HashMap<String, Object> row : users) {
	        String uName = Objects.toString(row.get("USER_NAME"), "");
	        if (uName.isEmpty()) continue;

	        // calcUserBattleContext 로 ctx 전체 계산 (동일한 공식 재사용)
	        HashMap<String, Object> ctxMap = new HashMap<>();
	        ctxMap.put("userName", uName);
	        ctxMap.put("roomName", "");
	        UserBattleContext ctx;
	        try {
	            ctx = calcUserBattleContext(ctxMap);
	        } catch (Exception e) { continue; }
	        if (!ctx.success) continue;

	        int lv = ctx.user != null ? ctx.user.lv : 0;

	        // ctx.atkMax = (base+market+7009(lv)+7013(cappedYest)) × hellMult (헬너프 적용)
	        // 추정MAX: ctx.atkMax + 7013차분(max30-실제) × hellMult + dropAtkMax
	        boolean has7009 = ctx.ownedBossItems.contains(7009);
	        boolean has7013 = ctx.ownedBossItems.contains(7013);

	        // 표시용 보스템 보너스 (맥스치 기준, 너프 전 원본)
	        int maxBossBonus = 0;
	        if (has7009) maxBossBonus += Math.min(lv, 300) * 150; // 최대 45,000
	        if (has7013) maxBossBonus += 30 * 500;                // 최대 15,000

	        int cappedYest = Math.min(getYesterdayAttackerCountCached(), 30);
	        int extra7013  = has7013 ? (30 - cappedYest) * 500 : 0;
	        double keepFraction = ctx.hellNerfRate > 0 ? ctx.hellNerfRate : 1.0;
	        int extra7013Nerfed = (int) Math.round(extra7013 * keepFraction);

	        // 세트 효과 텍스트 요약
	        List<String> setDesc = new ArrayList<>();
	        if (ctx.setAtkFinalRate  > 0) setDesc.add("ATK+" + ctx.setAtkFinalRate + "%");
	        if (ctx.setCritFinalRate > 0) setDesc.add("크리+" + ctx.setCritFinalRate + "%");
	        if (ctx.setCooldownReduce> 0) setDesc.add("쿨-"  + ctx.setCooldownReduce + "s");
	        if (ctx.setEvasionRate   > 0) setDesc.add("회피+" + ctx.setEvasionRate + "%");
	        if (ctx.activeSetSpecials != null) {
	            for (String sp : ctx.activeSetSpecials) setDesc.add(sp.replace("SPECIAL_", ""));
	        }

	        HashMap<String, Object> entry = new HashMap<>();
	        entry.put("userName",   uName);
	        entry.put("lv",         lv);
	        entry.put("atkMin",     ctx.atkMin);
	        entry.put("atkMax",     ctx.atkMax);
	        entry.put("crit",       ctx.crit);
	        entry.put("critDmg",    ctx.critDmg);
	        entry.put("darkAtkMin", ctx.dropAtkMin);
	        entry.put("darkAtkMax", ctx.dropAtkMax);
	        entry.put("setInfo",    String.join(" / ", setDesc));
	        entry.put("bossBonus",  maxBossBonus);
	        entry.put("has7009",    has7009);
	        entry.put("has7013",    has7013);
	        entry.put("bossEstMin", ctx.atkMin + ctx.dropAtkMin + extra7013Nerfed);
	        entry.put("bossEstMax", ctx.atkMax + ctx.dropAtkMax + extra7013Nerfed);
	        result.add(entry);
	    }

	    result.sort((a, b) -> Integer.compare(
	        b.get("bossEstMax") != null ? ((Number) b.get("bossEstMax")).intValue() : 0,
	        a.get("bossEstMax") != null ? ((Number) a.get("bossEstMax")).intValue() : 0
	    ));
	    return result;
	}

	@GetMapping("/equip-rank-view")
	public String equipRankView() {
	    return "nonsession/loa/equip_rank_view";
	}

	private double getJobDmgMulForSim(String job) {
	    switch (job) {
	        case "궁수":  case "사냥꾼": return 3.0;
	        case "궁사":                 return 1.0;
	        case "전사":  case "처단자": case "용사": return 1.4;
	        case "검성":                 return 2.2;
	        case "어쓰신":               return 1.3;
	        case "제너럴":               return 1.2;
	        case "저격수":               return 2.0;
	        case "복수자":               return 0.2;
	        case "음양사":               return 1.6;
	        default:                     return 1.0;
	    }
	}

	private Map<String, Object> simStep(String label, int bonusMin, int bonusMax,
	                                    int totalMin, int totalMax, String note) {
	    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
	    m.put("label",    label);
	    m.put("bonusMin", bonusMin);
	    m.put("bonusMax", bonusMax);
	    m.put("totalMin", totalMin);
	    m.put("totalMax", totalMax);
	    m.put("note",     note);
	    return m;
	}
}