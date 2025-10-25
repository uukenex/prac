package my.prac.api.loa.controller;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;


@Controller
public class BossAttackController {
	static Logger logger = LoggerFactory.getLogger(BossAttackController.class);
	@Autowired
	LoaPlayController play;
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	/* ===== Public API ===== */
    public String monsterAttack(HashMap<String, Object> map) {
        map.put("cmd", "monster_attack");

        // 0) 입력/기본 셋업
        /*
        BossInfo boss = loadBoss(map);
        if (boss == null) return "보스 정보를 가져오는데 실패했습니다.";
         
        */
        /*
        if (!isStartTimeReached(boss.startTime)) {
            return "보스가 아직 등장하지 않았습니다!" + enterStr + "/보스정보 로 정보확인!";
        }
        */
        //WeaponInfo weapon = loadWeapon(map);
        //if (weapon == null) return "무기 정보를 가져오는데 실패했습니다.";

        // 1) 숨김 룰 적용(아이템 우회 제거) 및 회피율 보정
        CalcContext ctx = new CalcContext();
        //applyHideRuleAndEvade(boss, weapon, ctx);

        // 2) 일일 공격 횟수 / 쿨타임 체크
        if (!checkDailyLimit(map, ctx)) return map.get("userName") + "님," + enterStr + "일일공격횟수 끝!";
        if (!checkCooldown(map)) return map.get("userName") + "님," + enterStr + map.get("extra_msg");

        // 3) 확률 플래그 롤
        //Flags flags = rollFlags(boss);

        // 4) 데미지 계산
        //computeDamage(boss, weapon, flags, ctx);

        // 5) 처치/점수 계산
        //handleKillAndScore(boss, flags, weapon, ctx, map);

        // 6) 보스 반격/흡혈/필살기 (미처치 시)
        /*
        if (!ctx.isKill) {
            bossCounterAttack(boss, weapon, flags, ctx);
        }
		*/
        // 7) 점수 상/하한 보정
        //applyScoreBounds(map, flags, ctx);

        // 8) DB 반영
        //int newScore = persist(map, boss, ctx);

        // 9) 메시지 생성
        //return buildMessage(map, boss, ctx, newScore);
        return "";
    }

	
    
    /* ===== Loaders ===== */

    private BossInfo loadBoss(HashMap<String, Object> map) {
        try {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> b = botService.selectBotPointBoss(map);
            if (b == null || b.get("HP") == null) return null;

            BossInfo bi = new BossInfo();
            bi.hp           = parseInt(b.get("HP"), 0);
            bi.reward       = parseInt(b.get("REWARD"), 0);
            bi.orgHp        = parseInt(b.get("ORG_HP"), 0);
            bi.seq          = parseInt(b.get("SEQ"), 0);
            bi.atkRate      = parseInt(b.get("ATK_RATE"), 20);
            bi.atkPower     = parseInt(b.get("ATK_POWER"), 100);
            bi.defRate      = parseInt(b.get("DEF_RATE"), 20);
            bi.defPower     = parseInt(b.get("DEF_POWER"), 100);
            bi.debuff       = parseInt(b.get("DEBUFF"), 0);
            bi.debuff1      = parseInt(b.get("DEBUFF1"), 0);
            bi.debuff2      = parseInt(b.get("DEBUFF2"), 0);
            bi.drainRemain  = parseInt(b.get("DRAIN_REMAIN"), 0);
            bi.critDefRate  = parseInt(b.get("CRIT_DEF_RATE"), 0);
            bi.evadeRate    = parseInt(b.get("EVADE_RATE"), 10);
            bi.hideRule     = b.get("HIDE_RULE") == null ? "Normal" : b.get("HIDE_RULE").toString();
            bi.startTime    = b.get("START_DATE").toString();
            return bi;
        } catch (Exception e) {
            return null;
        }
    }

    private WeaponInfo loadWeapon(HashMap<String, Object> map) {
        try {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> w = play.getWeaponStats(map);
            WeaponInfo wi = new WeaponInfo();
            wi.level          = parseInt(w.get("level"), 0);
            wi.criticalChance = parseDouble(w.get("criticalChance"), 0.0);
            wi.min            = parseInt(w.get("min"), 1);
            wi.max            = parseInt(w.get("max"), 1);
            wi.def            = parseInt(w.get("def"), 0);
            wi.hit            = parseInt(w.get("hit"), 0);
            wi.sumScore       = parseInt(w.get("sum_score"), 0);
            return wi;
        } catch (Exception e) {
            return null;
        }
    }
	
    /* ===== Phase 1: Hide/evade ===== */

    private void applyHideRuleAndEvade(BossInfo boss, WeaponInfo weapon, CalcContext ctx) {
        // 회피율 보정: 보스 회피 - 플레이어 명중 (아이템 제거)
        boss.evadeRate = Math.max(boss.evadeRate - weapon.hit, 0);

        if (boss.debuff == 0) {
            LocalTime now = LocalTime.now();
            LocalTime start, end;
            switch (boss.hideRule) {
                case "아침": start = LocalTime.of(6, 0);  end = LocalTime.of(10, 0); break;
                case "점심": start = LocalTime.of(10, 0); end = LocalTime.of(15, 0); break;
                case "저녁": start = LocalTime.of(15, 0); end = LocalTime.of(19, 0); break;
                default:     start = LocalTime.of(2, 0);  end = LocalTime.of(6, 0);  break;
            }
            if (!now.isBefore(start) && now.isBefore(end)) {
                ctx.nightStatus = "Y2"; // 피해 30% 감소
                ctx.nightMsg = "보스가 숨었습니다...피해 30% 감소.." + enterStr;
            }
        } else {
            // 천벌 중엔 숨김/회피/공격/방어 미적용
            boss.atkRate = 0;
            boss.defRate = 0;
            boss.evadeRate = 0;
        }
    }

    /* ===== Phase 2: Limits/Cooldown ===== */

    private boolean checkDailyLimit(HashMap<String, Object> map, CalcContext ctx) {
        String checkCount = botService.selectHourCheckCount(map);
        int used = parseInt(checkCount, 0);
        int limit = 40; // 아이템 가산 제거
        if (used >= limit) return false;
        used += 1;
        ctx.countingMsg = "일일공격횟수: " + used + " / " + limit;
        return true;
    }

    private boolean checkCooldown(HashMap<String, Object> map) {
        return play.checkBossCooldown(map); // 기존 서비스 호출
    }

    /* ===== Phase 3: Flags ===== */

    private Flags rollFlags(BossInfo boss) {
        Flags f = new Flags();
        f.bossAttack      = Math.random() < boss.atkRate / 100.0;
        f.bossEvade       = Math.random() < boss.evadeRate / 100.0;
        f.bossDefence     = Math.random() < boss.defRate / 100.0;
        f.bossDebuff      = boss.debuff  > 0;
        f.bossDebuff1     = boss.debuff1 > 0;
        f.bossDebuff2     = boss.debuff2 > 0;
        f.bossDrainRemain = boss.drainRemain > 0;
        f.playerDouble    = Math.random() < 0.03;
        f.bossDrain       = Math.random() < 0.01;
        f.bossSpecial     = Math.random() < 0.01;
        return f;
    }

    /* ===== Phase 4: Damage ===== */

    private void computeDamage(BossInfo boss, WeaponInfo weapon, Flags f, CalcContext ctx) {
        ctx.isEvade = f.bossEvade;
        ctx.isEvadeMsg = ctx.isEvade ? "보스가 회피합니다." + enterStr : "";

        if (ctx.isEvade) {
            ctx.damage = 0;
            return;
        }

        // 치명 확률 = 무기 치확 - 보스 치저 + (debuff1이면 +5)
        double critPercent = weapon.criticalChance * 100.0;
        critPercent -= boss.critDefRate;
        if (f.bossDebuff1) critPercent += 5.0;
        if (critPercent < 0) critPercent = 0;

        ctx.isCritical = Math.random() < (critPercent / 100.0);
        ctx.isSuperCritical = ctx.isCritical && Math.random() < 0.10;

        // 야간 감폭(Y2)이면 치명 무효
        if ("Y2".equals(ctx.nightStatus)) {
            ctx.isCritical = false;
            ctx.isSuperCritical = false;
        }

        int baseDamage = ThreadLocalRandom.current().nextInt(weapon.min, weapon.max + 1);
        String baseMsg = "(" + weapon.min + "~" + weapon.max + ") ";

        if (ctx.isSuperCritical) {
            ctx.damage = baseDamage * 5;
            ctx.dmgMsg = "[✨초강력 치명타!!] " + baseMsg + baseDamage + " → " + ctx.damage;
        } else if (ctx.isCritical) {
            ctx.damage = baseDamage * 3;
            ctx.dmgMsg = "[✨치명타!] " + baseMsg + baseDamage + " → " + ctx.damage;
        } else {
            ctx.damage = baseDamage;
            ctx.dmgMsg = baseMsg + baseDamage + " 로 공격!";
        }

        // 숨김 피해 30% 감소
        if ("Y2".equals(ctx.nightStatus)) {
            ctx.damage = ctx.damage * 7 / 10;
            ctx.dmgMsg += " → " + ctx.damage;
        }
        
    }

    /* ===== Phase 5: Kill/Score ===== */

    private void handleKillAndScore(BossInfo boss, Flags f, WeaponInfo weapon, CalcContext ctx, HashMap<String, Object> map) {
        int score = ctx.damage / 4;
        boolean newbie = weapon.sumScore < 6000;
        if (newbie) score += 10;

        ctx.newHp = boss.hp - ctx.damage;
        if (ctx.newHp <= 0) {
            // 아이템 제거 버전: 치명타일 때만 처치
            if (ctx.isCritical) {
                ctx.isKill = true;
                score = Math.min(ctx.damage, boss.hp) / 4 + 100 + boss.drainRemain;
                map.put("reward", boss.reward);
                map.put("org_hp", boss.orgHp);
                ctx.rewardMsg = calcBossReward2(map);
                respawnBoss(map);

                if (f.bossDrainRemain) {
                    ctx.extraMsg += "보스가 흡혈했던 포인트 추가획득 : " + boss.drainRemain + enterStr;
                }
            } else {
                ctx.newHp = 1;
                int allowed = boss.hp - 1;
                score = Math.min(ctx.damage, allowed) / 4;
                ctx.damage = allowed;
                ctx.dmgMsg = "";
                ctx.bossDefenseMsg = "";
            }
        }

        ctx.score = score;
    }

    /* ===== Phase 6: Boss Counter ===== */

    private void bossCounterAttack(BossInfo boss, WeaponInfo weapon, Flags f, CalcContext ctx) {
        if (f.bossDrain) {
            ctx.appliedAtkPower = ThreadLocalRandom.current().nextInt(10, 30);
            ctx.appliedAtkPowerAfterDef = ctx.appliedAtkPower;
            ctx.bossAttackMsg = "▶ {보스의 흡혈} 사용! " + ctx.appliedAtkPowerAfterDef + " 의 흡혈!!"
                    + enterStr + "누적흡혈량: " + (boss.drainRemain + ctx.appliedAtkPowerAfterDef) + "(처치시 처치자 획득)";
        } else if (f.bossSpecial) {
            ctx.appliedAtkPower = ThreadLocalRandom.current().nextInt(100, 200);
            ctx.appliedAtkPowerAfterDef = ctx.appliedAtkPower;
            ctx.bossAttackMsg = "▶ {보스의 필살기} 사용!! " + ctx.appliedAtkPowerAfterDef + " 의 피해..!!"
                    + enterStr + "너무큰피해에..상자를 받았습니다.";
            try {
                botService.insertPointNewBoxOpenTx(new HashMap<>(0));
            } catch (Exception ignored) {}
        } else if (f.bossAttack) {
            ctx.appliedAtkPower = ThreadLocalRandom.current().nextInt(1, boss.atkPower + 1);
            ctx.appliedAtkPowerAfterDef = Math.max(ctx.appliedAtkPower - weapon.def, 0);
            ctx.bossAttackMsg = "▶ 보스의 반격! " + ctx.appliedAtkPower + " 의 데미지!!"
                    + enterStr + "(플레이어 방어)-" + weapon.def + " → -" + ctx.appliedAtkPowerAfterDef;
        }

        if (!ctx.bossAttackMsg.isEmpty()) {
            // 초보자 보호
            if (weapon.sumScore < 6000 && ctx.appliedAtkPowerAfterDef > 0) {
                ctx.score += ctx.appliedAtkPowerAfterDef;
                ctx.bossAttackMsg += enterStr + "(초보자) " + ctx.appliedAtkPowerAfterDef + " 회복";
            }
            ctx.extraMsg += ctx.bossAttackMsg + enterStr;
        }
    }

    /* ===== Phase 7: Score Bounds ===== */

    private void applyScoreBounds(HashMap<String, Object> map, Flags f, CalcContext ctx) {
        if (ctx.isKill) return;

        int limitLv = 0;
        try {
            @SuppressWarnings("unchecked")
            List<HashMap<String, Object>> ls = botService.selectBotPointStatUserSum(map);
            for (HashMap<String, Object> hs : ls) {
                if ("LIMIT".equals(String.valueOf(hs.get("STAT_NAME")))) {
                    limitLv = parseInt(hs.get("LV"), 0);
                }
            }
        } catch (Exception ignored) {}

        if (ctx.score > 150) ctx.score = 150 + limitLv;

        if (!(f.bossAttack || f.bossDrain || f.bossSpecial)) {
            if (ctx.score < 10) ctx.score = 10;
        }
    }
	

    /* ===== Phase 8: Persistence ===== */

    private int persist(HashMap<String, Object> map, BossInfo boss, CalcContext ctx) {
        try {
            map.put("hp", boss.hp);
            map.put("newHp", ctx.newHp);
            map.put("seq", boss.seq);
            map.put("damage", ctx.damage);
            map.put("score", ctx.score);
            map.put("endYn", ctx.isKill ? "1" : "0");
            map.put("atkPower", ctx.appliedAtkPower);
            map.put("defPower", ctx.appliedDefPower);

            if (ctx.appliedAtkPower > 0 && ctx.bossAttackMsg.contains("흡혈")) {
                map.put("drainRemain", ctx.appliedAtkPower);
                map.put("useDrain", 1);
            }
            if (!ctx.extraMsg.isEmpty()) {
                map.put("extra_msg", ctx.extraMsg);
            }

            botService.updateBotPointBossTx(map);
            return botService.insertBotPointRankTx(map);
        } catch (Exception e) {
            throw new RuntimeException("DB 반영 오류", e);
        }
    }

    /* ===== Phase 9: Message ===== */

    private String buildMessage(HashMap<String, Object> map, BossInfo boss, CalcContext ctx, int newScore) {
        StringBuilder msg = new StringBuilder();
        msg.append(map.get("userName")).append("님이 보스를 공격했습니다!").append(enterStr);

        if (!ctx.isEvade) {
            msg.append("▶ 입힌 데미지: ").append(ctx.damage).append(enterStr);
            if (!ctx.dmgMsg.isEmpty()) msg.append(ctx.dmgMsg).append(enterStr);
            if (!ctx.bossDefenseMsg.isEmpty()) msg.append(ctx.bossDefenseMsg);
        } else {
            msg.append("보스가 공격을 회피! 데미지 0!").append(enterStr);
            msg.append(ctx.isEvadeMsg);
        }

        msg.append(ctx.nightMsg);
        if (!ctx.extraMsg.isEmpty()) msg.append(ctx.extraMsg);

        msg.append(enterStr);
        if (ctx.newHp == 1 && !ctx.isKill) {
            msg.append("✨보스는 체력 1! 치명타로 최후의 일격 필요!").append(enterStr);
        } else if (ctx.isKill) {
            msg.append("✨보스를 처치했습니다!").append(enterStr);
        } else {
            if ((ctx.newHp * 100.0) / boss.orgHp < 10) {
                msg.append("보스 체력: ").append(ctx.newHp).append("/").append(boss.orgHp).append(enterStr);
            } else {
                msg.append("보스 체력: ???/???").append(enterStr);
            }
        }

        msg.append("공격 쿨타임 : ").append(map.get("timeDelay")).append(" Min ").append(map.get("timeDelayMsg")).append(enterStr);
        msg.append("▶ 획득 포인트: ").append(ctx.score);
        if (ctx.score == 150) msg.append("(MAX)");
        if (parseInt(getMap(map, "sum_score"), 0) < 6000) msg.append(" (초보자 +10p)");
        if (ctx.score == 10) msg.append(" (최소p 보정)");
        msg.append(enterStr).append("갱신포인트 : ").append(newScore).append(enterStr);

        if (boss.drainRemain > 0) {
            msg.append("보스 흡혈 잔여 포인트 : ").append(boss.drainRemain).append(enterStr);
        }

        int boxCount = 0;
        try {
            boxCount = botService.selectPointNewBoxCount(map);
        } catch (Exception ignored) {}
        if (boxCount > 0) {
            msg.append(enterStr).append("보유 상자 갯수 : ").append(boxCount);
        }

        msg.append(enterStr).append(ctx.countingMsg);
        return msg.toString();
    }

    /* ===== Helpers ===== */

    private boolean isStartTimeReached(String startTime) {
        try {
            LocalDateTime startDate = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
            return !LocalDateTime.now().isBefore(startDate);
        } catch (Exception e) {
            return true; // 파싱 실패 시 공격 허용
        }
    }

    private static int parseInt(Object o, int def) {
        try { return o == null ? def : Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(Object o, double def) {
        try { return o == null ? def : Double.parseDouble(o.toString()); } catch (Exception e) { return def; }
    }

    private static Object getMap(Map<String, Object> map, String key) {
        return map.get(key);
    }
    
    public String calcBossReward2(HashMap<String, Object> map) {
		String roomName = (String) map.get("roomName");
		int totalReward = Integer.parseInt(map.get("reward").toString()) ; // 기본 총 보상 포인트
		int tot1=0;
		int tot2=0;
		int bossOrgMaxHp = Integer.parseInt(map.get("org_hp").toString());
		
		tot1 = totalReward/10*7;
		tot2 = totalReward/10*3;
		
		List<HashMap<String, Object>> top3List = botService.selectTop3Contributors(map);
		
		int totalTop3Damage = 0;
		for (HashMap<String, Object> row : top3List) {
			totalTop3Damage += Integer.parseInt(row.get("SCORE").toString());
		}
		
		StringBuilder msgBuilder = new StringBuilder();
		
		msgBuilder.append("보스는 2시간 뒤 재등장합니다!");
		
		msgBuilder.append(enterStr).append(enterStr).append("보스 기여도 보상 분배 결과").append(allSeeStr);
		
		msgBuilder.append(enterStr+"횟수 기여도"+enterStr);
		for (HashMap<String, Object> row : top3List) {
			String name = row.get("USER_NAME").toString();
			int cnt = Integer.parseInt(row.get("CNT").toString());
			int totCnt = Integer.parseInt(row.get("TOT_CNT").toString());
			
			double cntRatio = (double) cnt / totCnt * 100;
			
			// top3 데미지 합 대비 분배 비율
			double rewardRatio = (double) cnt / totCnt;
			int reward = (int) Math.floor(tot1 * rewardRatio); // 내림처리
			
			// 포인트 지급 처리
			HashMap<String, Object> rewardMap = new HashMap<>();
			rewardMap.put("roomName", roomName);
			rewardMap.put("userName", name);
			rewardMap.put("score", reward);
			rewardMap.put("cmd", "boss_kill_reward");
			
			try {
				botService.insertBotPointRankTx(rewardMap);
			} catch (Exception e) {
				// 오류 무시
			}
			
			// 메시지 작성
			String percentStr = String.format("%.0f", cntRatio); // 정수 퍼센트
			msgBuilder
			.append(name)
			.append(" - ")
			.append(cnt) //score가 3분의1로 나눠지기때문 
			.append(" 회(")
			.append(percentStr)
			.append("%) - ")
			.append(reward)
			.append("pt 지급")
			.append(enterStr)
			
			;
		}
		
		msgBuilder.append(enterStr+"데미지 기여도"+enterStr);
		for (HashMap<String, Object> row : top3List) {
			String name = row.get("USER_NAME").toString();
			int damage = Integer.parseInt(row.get("SCORE").toString());
			
			// 보스 전체 체력 대비 데미지 비율 (%)
			double bossRatio = (double) damage / bossOrgMaxHp * 100;
			
			// top3 데미지 합 대비 분배 비율
			double rewardRatio = (double) damage / totalTop3Damage;
			int reward = (int) Math.floor(tot2 * rewardRatio); // 내림처리
			
			// 포인트 지급 처리
			HashMap<String, Object> rewardMap = new HashMap<>();
			rewardMap.put("roomName", roomName);
			rewardMap.put("userName", name);
			rewardMap.put("score", reward);
			rewardMap.put("cmd", "boss_kill_reward");
			
			try {
				botService.insertBotPointRankTx(rewardMap);
			} catch (Exception e) {
				// 오류 무시
			}
			
			// 메시지 작성
			String percentStr = String.format("%.0f", bossRatio*4); // 정수 퍼센트
			msgBuilder
			.append(name)
			.append(" - ")
			.append(damage*4) //score가 4분의1로 나눠지기때문 
			.append(" dmg(")
			.append(percentStr)
			.append("%) - ")
			.append(reward)
			.append("pt 지급")
			.append(enterStr)
			
			;
		}
		
		
		
		
		
		return msgBuilder.toString();
	}
    
    void respawnBoss(HashMap<String, Object> map) {
		try {
			HashMap<String, Object> newBoss = new HashMap<>();
			newBoss.put("startDate",
					LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
			newBoss.put("roomName", map.get("roomName"));

			// 랜덤 스탯 생성기
			Random rand = new Random();
			
			 // --- 사용자 최고 레벨 조회 ---
	        HashMap<String, Object> values = botService.selectBotMaxLv(map);
	        int userMaxWeapon = values.get("VAL1") == null ? 0 : Integer.parseInt(values.get("VAL1").toString());
	        int userMaxItem   = values.get("VAL2") == null ? 0 : Integer.parseInt(values.get("VAL2").toString());
	        int userMaxAcc    = values.get("VAL3") == null ? 0 : Integer.parseInt(values.get("VAL3").toString());

	        // --- HP 계산: 유저 최고 레벨 총합 기반 ---
	        int userTotalLv = userMaxWeapon + userMaxItem + userMaxAcc;
	        int orgHp = (userTotalLv * 300) + (5000 + rand.nextInt(5001)); // 5000 ~ 10000 랜덤 보정
	        newBoss.put("org_hp", orgHp);
	        
	        int reward = 600 + (int)(Math.pow((orgHp - 5000) / 55000.0, 0.8) * 1200);
			newBoss.put("reward", reward);
			
			if (userTotalLv <= 20) {
			    // --- 유저 합계가 20레벨 이하라면 능력치는 전부 0 ---
			    newBoss.put("evadeRate", 0);
			    newBoss.put("atkRate", 0);
			    newBoss.put("atkPower", 0);
			    newBoss.put("defRate", 0);
			    newBoss.put("defPower", 0);
			    newBoss.put("critDefRate", 0);
			} else {
				// --- 능력치 총합 예산 (유저 레벨 기반 랜덤) ---
		        int minBudget = Math.max(6, userTotalLv / 2);       // 최소 유저 합계만큼
		        int maxBudget = Math.max(12, userTotalLv * 3 / 2);  // 1.5배까지
		        int totalBudget = minBudget + rand.nextInt(maxBudget - minBudget + 1);
		        
		        int maxStat = 25; // 능력치 최대치
		        // 최소 1씩 보장
		        int[] stats = new int[6];
		        Arrays.fill(stats, 1);
		        int remaining = totalBudget - 6;

		        // 남은 포인트를 랜덤 분배 (각 스탯 maxStat 초과 방지)
		        while (remaining > 0) {
		            int idx = rand.nextInt(6);
		            if (stats[idx] < maxStat) {
		                stats[idx]++;
		                remaining--;
		            }
		        }

		        newBoss.put("evadeRate", stats[0]);
		        newBoss.put("atkRate", stats[1]);
		        newBoss.put("atkPower", stats[2]);
		        newBoss.put("defRate", stats[3]);
		        newBoss.put("defPower", stats[4]);
		        newBoss.put("critDefRate", stats[5]);
			}
	        
			// hideRule : 아침 / 저녁 / 새벽 중 랜덤
			String[] hideRules = { "아침","점심", "저녁", "새벽" };
			newBoss.put("hideRule", hideRules[rand.nextInt(hideRules.length)]);
			// TODO: 다른 스탯(ATK_RATE, DEF_RATE 등)도 초기화 값 넣기
			botService.insertBotPointBossTx(newBoss); // 신규 보스 생성 쿼리

		} catch (Exception e) {
			System.err.println("보스 재생성 실패: " + e.getMessage());
		}
	}

	/* ===== DTOs ===== */
    static class BossInfo {
        int hp, reward, orgHp, seq;
        int atkRate, atkPower, defRate, defPower;
        int critDefRate, evadeRate;
        int debuff, debuff1, debuff2, drainRemain;
        String hideRule;
        String startTime; // "yyyyMMdd HHmmss"
    }

    static class WeaponInfo {
        int level, min, max, def, hit, sumScore;
        double criticalChance; // 0.0 ~ 1.0
    }

    static class Flags {
        boolean bossAttack;
        boolean bossEvade;
        boolean bossDefence;
        boolean bossDebuff;    // 천벌
        boolean bossDebuff1;   // 치명저항 감소
        boolean bossDebuff2;
        boolean bossDrainRemain;
        boolean playerDouble;  // 3%
        boolean bossDrain;     // 1%
        boolean bossSpecial;   // 1%
    }

    static class CalcContext {
        boolean isEvade;
        boolean isCritical;
        boolean isSuperCritical;
        int damage;
        int appliedAtkPower;
        int appliedAtkPowerAfterDef;
        int appliedDefPower;
        int newHp;
        int score;
        boolean isKill;
        String dmgMsg = "";
        String bossDefenseMsg = "";
        String bossAttackMsg = "";
        String nightMsg = "";
        String countingMsg = "";
        String extraMsg = "";
        String rewardMsg = "";
        String isEvadeMsg = "";
        String nightStatus = "N"; // "N" or "Y2"
    }
}

