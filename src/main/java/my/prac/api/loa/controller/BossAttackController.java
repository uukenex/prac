package my.prac.api.loa.controller;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.BattleLog;
import my.prac.core.game.dto.Monster;
import my.prac.core.game.dto.OngoingBattle;
import my.prac.core.game.dto.User;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;

@Controller
public class BossAttackController {

	/* ===== Config / Const ===== */
	private static final Logger log = LoggerFactory.getLogger(BossAttackController.class);
	private static final int COOLDOWN_SECONDS = 120; // 2분
	private static final String NL = "♬";

	/* ===== DI ===== */
	@Autowired
	LoaPlayController play;
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	@Resource(name = "core.prjbot.BotNewService")
	BotNewService botNewService;
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;

	/* ===== Public APIs ===== */

	/** 타겟 변경 (번호/이름 허용) */
	public String changeTarget(HashMap<String, Object> map) {
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");
		final String input = Objects.toString(map.get("monNo"), "").trim();
		if (roomName.isEmpty() || userName.isEmpty())
			return "방/유저 정보가 누락되었습니다.";
		if (input.isEmpty())
			return "변경할 몬스터 번호 또는 이름을 입력해주세요.";

		Monster m = input.matches("\\d+") ? botNewService.selectMonsterByNo(Integer.parseInt(input))
				: botNewService.selectMonsterByName(input);

		if (m == null) {
			List<Monster> monsters = botNewService.selectAllMonsters();
			StringBuilder sb = new StringBuilder();
			sb.append("해당 몬스터(").append(input).append(")를 찾을 수 없습니다.").append(NL).append("아래 목록 중에서 선택해주세요:").append(NL)
					.append(NL);
			for (Monster mm : monsters)
				sb.append(mm.monNo).append(" : ").append(mm.monName).append(NL);
			return sb.toString();
		}

		User u = botNewService.selectUser(userName, roomName);
		if (u == null) {
			// ❗ 유저 미등록이면 생성 + 타겟 세팅
			botNewService.insertUserWithTargetTx(userName, roomName, m.monNo);
			return userName + "님, 새 유저를 생성하고 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 설정했습니다.";
		}
		if (u.targetMon == m.monNo)
			return "현재 타겟이 이미 " + m.monName + "(MON_NO=" + m.monNo + ") 입니다.";

		botNewService.closeOngoingBattleTx(userName, roomName);
		botNewService.updateUserTargetMonTx(userName, roomName, m.monNo);
		return userName + "님, 공격 타겟을 " + m.monName + "(MON_NO=" + m.monNo + ") 으로 변경했습니다.";
	}

	/** 몬스터 공격 */
	public String monsterAttack(HashMap<String, Object> map) {
		map.put("cmd", "monster_attack");
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");
		if (roomName.isEmpty() || userName.isEmpty())
			return "방/유저 정보가 누락되었습니다.";

		// 1) 유저 조회
		User u = botNewService.selectUser(userName, roomName);
		if (u == null)
			return guideSetTargetMessage();

		// 🔽🔽🔽 여기부터 새 로직 삽입 🔽🔽🔽

		// ① 쓰러진 경우: 1시간 부활 체크
		String reviveMsg = reviveAfter1hIfDead(userName, roomName, u);
		boolean revivedThisTurn = false;
		if (reviveMsg != null) {
		    if (!reviveMsg.isEmpty()) return reviveMsg; // 대기 안내 등 즉시 리턴
		    revivedThisTurn = true; // "" → 이번 턴에 자동부활 반영됨
		}

		// ② 읽기 계산 회복(저장 없이 사용) — 자동부활이 이번 턴에 적용된 경우엔 스킵
		int effectiveHp = revivedThisTurn ? u.hpCur : computeEffectiveHpFromLastAttack(userName, roomName, u);
		u.hpCur = effectiveHp;
		
		
		// 2) 진행중 전투 or 신규 타겟 세팅
		OngoingBattle ob = botNewService.selectOngoingBattle(userName, roomName);
		Monster m;
		int monMaxHp, monHpRemainBefore;
		if (ob != null) {
			m = botNewService.selectMonsterByNo(ob.monNo);
			if (m == null)
				return "진행중 몬스터 정보를 찾을 수 없습니다.";
			monMaxHp = m.monHp;
			monHpRemainBefore = Math.max(0, m.monHp - ob.totalDealtDmg);
		} else {
			m = botNewService.selectMonsterByNo(u.targetMon);
			if (m == null)
				return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";
			monMaxHp = m.monHp;
			monHpRemainBefore = m.monHp;
		}

		// 3) 쿨타임 + 체력 50% 미만 안내
		boolean isTest = "test".equals(Objects.toString(map.get("param1"), ""));
		if (!isTest) {
			CooldownCheck cd = checkCooldown(userName, roomName);
			if (!cd.ok)
				return userName + "님, 공격 쿨타임 " + cd.remainMinutes + "분 남았습니다.";
			String hpMsg = buildBelowHalfMsg(userName, roomName, u);
			if (hpMsg != null)
				return hpMsg;
		} else {
			// test 모드에서는 쿨타임 무시하지만, HP 50% 미만 안내는 유지
			String hpMsg = buildBelowHalfMsgIgnoreCooldown(userName, roomName, u);
			if (hpMsg != null)
				return hpMsg;
		}

		// 4) 플래그/피해 계산
		Flags flags = rollFlags(u, m);
		AttackCalc calc = calcDamage(u, m, flags);

		// 5) 즉사 처리 (HP <= 0 미리보기)
		int newHpPreview = Math.max(0, u.hpCur - calc.monDmg);
		if (newHpPreview <= 0) {
			botNewService.closeOngoingBattleTx(userName, roomName);
			botNewService.updateUserHpOnlyTx(userName, roomName, 0);

			int minutes = minutesToHalf(u, 0);
			if (minutes == Integer.MAX_VALUE) {
				return userName + "님, 큰 피해로 쓰러졌습니다." + NL + "현재 체력: 0 / " + u.hpMax + NL
						+ "(자동 회복 불가: 분당 회복 0) 회복 수단을 사용해주세요.";
			} else {
				int threshold = (int) Math.ceil(u.hpMax * 0.5);
				int projected = Math.min(u.hpMax, minutes * u.hpRegen);
				return userName + "님, 큰 피해로 쓰러졌습니다." + NL + "현재 체력: 0 / " + u.hpMax + NL + "(분당 +" + u.hpRegen
						+ " HP) 약 " + minutes + "분 후 공격 가능" + NL + "예상 체력: " + projected + " / " + u.hpMax + " (목표: "
						+ threshold + ")";
			}
		}

		// 6) 처치/드랍 판단
		boolean willKill = calc.atkDmg >= monHpRemainBefore;
		Resolve res = resolveKillAndDrop(m, calc, willKill);

		// 7) DB 반영 (HP/EXP/LV + 로그)
		LevelUpResult up = persist(userName, roomName, u, m, flags, calc, res);

		// 처치 시 전투 종료
		if (res.killed)
			botNewService.closeOngoingBattleTx(userName, roomName);

		// 8) 메시지
		return buildAttackMessage(userName, u, m, flags, calc, res, up, monHpRemainBefore, monMaxHp);
	}

	private String buildBelowHalfMsgIgnoreCooldown(String userName, String roomName, User u) {
		int regenMin = minutesToHalf(u); // 50%까지 필요한 분
		if (regenMin == Integer.MAX_VALUE) {
	        return userName + "님, 체력이 50% 미만이며 자동 회복(분당 +" 
	            + u.hpRegen + ")이 불가합니다. 회복 수단을 사용해주세요.";
	    }

	    // regenMin > 0 인 경우만 남음
	    return "리젠 hp : +" + u.hpRegen + " , " + regenMin + "분뒤 50%까지 도달합니다";
	}

	/**
	 * HP<=0일 때만 동작. 마지막 공격시간 +60분 기준으로 자동 부활 로직.
	 * - 60분 미만: 남은 분 안내 후 종료(문구 리턴)
	 * - 60분 이상: 부활 기준선(HP=50%) + (기준선 이후 경과분 * HP_REGEN)을 적용해 현재 HP를 산출/저장하고,
	 *              메시지 없이(null 또는 빈문자열) 진행
	 * - 공격 이력 없음: 즉시 50%로 만들고 메시지 없이 진행
	 *
	 * 반환 값:
	 *  - null        : (살아있음) 계속 진행
	 *  - "" (빈 문자열): (이번에 자동부활 반영함) 계속 진행하되 이후 읽기-회복은 적용하지 말 것
	 *  - 그 외 문자열 : (대기 안내 등) 즉시 리턴
	 */
	private String reviveAfter1hIfDead(String userName, String roomName, User u) {
	    if (u.hpCur > 0) return null; // 살아있으면 아무것도 안 함

	    Timestamp last = botNewService.selectLastAttackTime(userName, roomName);

	    // 공격 이력 없음: 지금 시점에 50%로 세팅(리젠 더하지 않음)
	    if (last == null) {
	        int half = (int)Math.ceil(u.hpMax * 0.5);
	        botNewService.updateUserHpOnlyTx(userName, roomName, half);
	        u.hpCur = half;
	        return ""; // 메시지 없이 계속 진행 (이번 턴에 읽기-회복 금지)
	    }

	    Instant reviveAt = last.toInstant().plus(Duration.ofMinutes(60));
	    Instant now = Instant.now();

	    // 아직 60분이 안 지났으면 대기 안내
	    if (now.isBefore(reviveAt)) {
	        long remainMin = (long)Math.ceil(Duration.between(now, reviveAt).getSeconds() / 60.0);
	        return "쓰러진 상태입니다. 약 " + remainMin + "분 후 자동 부활합니다.";
	    }

	    // 60분 이상 지남 → 부활 기준선(50%) + (부활시점 이후 경과분 * HP_REGEN)
	    int half = (int)Math.ceil(u.hpMax * 0.5);
	    long afterMin = Duration.between(reviveAt, now).toMinutes();
	    long healed = Math.max(0, afterMin) * Math.max(0, (long)u.hpRegen);
	    int effective = (int)Math.min((long)u.hpMax, (long)half + healed);

	    botNewService.updateUserHpOnlyTx(userName, roomName, effective);
	    u.hpCur = effective;

	    return ""; // 메시지 없이 계속 진행 (이번 턴에 읽기-회복 금지)
	}


	/**
	 * 마지막 공격시각 기준 '읽기 계산' 체력. DB에는 즉시 저장하지 않고, 이후 공격 성공 시 persist에서 최종 HP를 저장. 이렇게
	 * 해야 중복 호출로 회복이 누적되는 문제를 피함.
	 */
	private int computeEffectiveHpFromLastAttack(String userName, String roomName, User u) {
		if (u.hpCur >= u.hpMax || u.hpRegen <= 0)
			return u.hpCur;

		Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
		if (last == null)
			return u.hpCur; // 최초 상태면 그대로

		long minutes = Math.max(0, Duration.between(last.toInstant(), Instant.now()).toMinutes());
		if (minutes <= 0)
			return u.hpCur;

		long heal = minutes * (long) u.hpRegen;
		long effective = (long) u.hpCur + heal;
		if (effective >= u.hpMax)
			return u.hpMax;
		return (int) effective;
	}

	/**
	 * 유저가 등록되어 있지 않거나 공격 타겟이 없는 경우 안내 메시지를 반환. - 번호 또는 이름으로 타겟 설정 가능: /타겟 1 또는 /타겟
	 * 토끼
	 */
	public String guideSetTargetMessage() {
		List<Monster> monsters = botNewService.selectAllMonsters();
		String NL = "♬";
		StringBuilder sb = new StringBuilder();
		sb.append("공격 타겟이 없습니다. 먼저 타겟을 설정해주세요.").append(NL).append("예) /타겟 1   또는   /타겟 토끼").append(NL).append(NL)
				.append("선택 가능한 몬스터 목록").append(NL);
		for (Monster m : monsters) {
			sb.append(m.monNo).append(" : ").append(m.monName).append(NL);
		}
		return sb.toString();
	}

	/* ===== Helpers ===== */

	private CooldownCheck checkCooldown(String userName, String roomName) {
		Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
		if (last == null)
			return CooldownCheck.ok();
		long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
		if (sec >= COOLDOWN_SECONDS)
			return CooldownCheck.ok();
		long remain = COOLDOWN_SECONDS - sec;
		return CooldownCheck.block((int) Math.ceil(remain / 60.0));
	}

	private String buildBelowHalfMsg(String userName, String roomName, User u) {
		int regenMin = minutesToHalf(u);
		int coolMin = cooldownRemainMinutes(userName, roomName);
		if (regenMin == Integer.MAX_VALUE)
			return userName + "님, 체력이 50% 미만이며 자동 회복(분당 +" + u.hpRegen + ")이 불가합니다. 회복 수단을 사용해주세요.";
		int waitMin = Math.max(regenMin, coolMin);
		if (waitMin > 0)
			return userName + "님, 약 " + waitMin + "분 후 공격 가능 (회복 필요 " + regenMin + "분, 쿨타임 " + coolMin + "분)";
		return null;
	}

	private Flags rollFlags(User u, Monster m) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Flags f = new Flags();
		f.atkCrit = r.nextDouble(0, 100) < clamp(u.critRate, 0, 100);
		f.monPattern = (m.monPatten <= 1) ? 1 : r.nextInt(1, m.monPatten + 1);
		return f;
	}

	private AttackCalc calcDamage(User u, Monster m, Flags f) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		AttackCalc c = new AttackCalc();

		int baseAtk = r.nextInt(u.atkMin, u.atkMax + 1);
		c.atkDmg = f.atkCrit ? (int) Math.round(baseAtk * Math.max(1.0, u.critDmg / 100.0)) : baseAtk;

		String name = m.monName; // 자연어 메시지에 사용
		switch (f.monPattern) {
		case 1: // WAIT
			c.monDmg = 0;
			c.patternMsg = name + "이(가) 대기합니다! (피해 0)";
			break;
		case 2: // ATTACK (50%~100% 랜덤)
			int minDmg = Math.max(1, (int) Math.floor(m.monAtk * 0.5));
			int maxDmg = m.monAtk;
			c.monDmg = ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1);
			c.patternMsg = name + "이(가) 공격합니다! (피해 " + c.monDmg + ")";
			break;
		case 3: // DEFEND
			c.atkDmg = (int) Math.round(c.atkDmg * 0.5);
			c.monDmg = 0;
			c.patternMsg = name + "이(가) 방어합니다! (당신의 피해 50%로 감소)";
			break;
		case 4: // SPECIAL
			c.monDmg = (int) Math.round(m.monAtk * 2.0);
			c.patternMsg = name + "의 필살기! (피해 " + c.monDmg + ")";
			break;
		default:
			c.monDmg = 0;
			c.patternMsg = name + "의 알 수 없는 행동… (피해 0)";
		}
		return c;
	}

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill) {
		Resolve r = new Resolve();
		r.killed = willKill;
		r.gainExp = r.killed ? m.monExp : Math.max(0, Math.min(1, m.monExp / 5));
		r.dropYn = r.killed && ThreadLocalRandom.current().nextDouble(0, 100) < 30.0;
		return r;
	}

	/** HP/EXP/LV 반영 + 로그 기록. LevelUpResult 반환해 메시지에서 사용 */
	private LevelUpResult persist(String userName, String roomName, User u, Monster m, Flags f, AttackCalc c,
			Resolve res) {
		u.hpCur = Math.max(0, u.hpCur - c.monDmg);
		LevelUpResult up = applyExpAndLevelUp(u, res.gainExp);

		botNewService.updateUserAfterBattleTx(userName, roomName, u.lv, u.expCur, u.expNext, u.hpCur, u.hpMax, u.atkMin,
				u.atkMax);

		botNewService.insertBattleLogTx(new BattleLog().setUserName(userName).setRoomName(roomName).setLv(up.beforeLv)
				.setTargetMonLv(m.monNo).setGainExp(up.gainedExp).setAtkDmg(c.atkDmg).setMonDmg(c.monDmg)
				.setAtkCritYn(f.atkCrit ? 1 : 0).setMonPatten(f.monPattern).setKillYn(res.killed ? 1 : 0).setNowYn(1)
				.setDropYn(res.dropYn ? 1 : 0));

		res.levelUpCount = up.levelUpCount;
		return up;
	}

	/** 최종 메시지 */
	private String buildAttackMessage(String userName, User u, Monster m, Flags flags, AttackCalc calc, Resolve res,
			LevelUpResult up, int monHpRemainBefore, int monMaxHp) {
		StringBuilder sb = new StringBuilder();
		sb.append(userName).append("님, ").append(m.monName).append("을(를) 공격!").append(NL);
		if (flags.atkCrit)
			sb.append("치명타 발생!").append(NL);

		if (calc.patternMsg != null && !calc.patternMsg.isEmpty()) {
			sb.append(calc.patternMsg).append(NL);
		}

		int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);
		sb.append("가한 피해: ").append(calc.atkDmg).append(" / 받은 피해: ").append(calc.monDmg).append(NL).append("몬스터 HP: ")
				.append(monHpAfter).append("/").append(monMaxHp).append(NL);

		if (res.killed) {
			sb.append("처치 성공! +경험치 ").append(res.gainExp).append(NL);
			if (res.dropYn)
				sb.append("드랍 획득: ").append(m.monDrop).append(NL);
		}

		sb.append("현재 체력: ").append(u.hpCur).append(" / ").append(u.hpMax).append(NL);

		// 경험치/레벨업 안내
		sb.append("경험치 +").append(up.gainedExp).append(NL);
		if (up.levelUpCount > 0) {
			sb.append("레벨업! Lv.").append(up.beforeLv).append(" → Lv.").append(up.afterLv).append(" (+")
					.append(up.levelUpCount).append(")").append(NL);

			boolean comma = false;
			if (up.hpMaxDelta > 0 || up.atkMinDelta > 0 || up.atkMaxDelta > 0) {
				sb.append("상승치: ");
				if (up.hpMaxDelta > 0) {
					sb.append("HP_MAX +").append(up.hpMaxDelta);
					comma = true;
				}
				if (up.atkMinDelta > 0) {
					if (comma)
						sb.append(", ");
					sb.append("ATK_MIN +").append(up.atkMinDelta);
					comma = true;
				}
				if (up.atkMaxDelta > 0) {
					if (comma)
						sb.append(", ");
					sb.append("ATK_MAX +").append(up.atkMaxDelta);
				}
				sb.append(NL);
			}
		}
		// 현재 EXP 상황(다음 레벨까지 남은 EXP)
		int remain = Math.max(0, u.expNext - u.expCur);
		sb.append("현재 EXP: ").append(u.expCur).append(" / ").append(u.expNext).append(" (다음 레벨까지 ").append(remain)
				.append(")").append(NL);

		return sb.toString();
	}

	/* ===== Regen & Time Helpers ===== */

	private RegenResult applyPassiveRegen(String userName, String roomName, User u) {
		Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
		if (last == null && u.insertDate != null)
			last = u.insertDate;
		if (last == null || u.hpCur >= u.hpMax || u.hpRegen <= 0)
			return new RegenResult(0, 0, u.hpCur);

		long minutes = Math.max(0, Duration.between(last.toInstant(), Instant.now()).toMinutes());
		if (minutes <= 0)
			return new RegenResult(0, 0, u.hpCur);

		long heal = minutes * (long) u.hpRegen;
		int newHp = (int) Math.min((long) u.hpMax, (long) u.hpCur + heal);
		int healed = newHp - u.hpCur;
		if (healed <= 0)
			return new RegenResult(0, (int) minutes, u.hpCur);

		botNewService.updateUserHpOnlyTx(userName, roomName, newHp);
		return new RegenResult(healed, (int) minutes, newHp);
	}

	private int minutesToHalf(User u) {
		return minutesToHalf(u, u.hpCur);
	}

	private int minutesToHalf(User u, int currentHp) {
		int threshold = (int) Math.ceil(u.hpMax * 0.5);
		if (currentHp >= threshold)
			return 0;
		if (u.hpRegen <= 0)
			return Integer.MAX_VALUE;
		int need = threshold - currentHp;
		return (int) Math.ceil(need / (double) u.hpRegen);
	}

	private int cooldownRemainMinutes(String userName, String roomName) {
		Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
		if (last == null)
			return 0;
		long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
		if (sec >= COOLDOWN_SECONDS)
			return 0;
		return (int) Math.ceil((COOLDOWN_SECONDS - sec) / 60.0);
	}

	/* ===== DTOs (inner simple) ===== */

	private static class RegenResult {
		final int healed, elapsedMinutes, newHpCur;

		RegenResult(int healed, int elapsedMinutes, int newHpCur) {
			this.healed = healed;
			this.elapsedMinutes = elapsedMinutes;
			this.newHpCur = newHpCur;
		}
	}

	private static class Flags {
		boolean atkCrit;
		int monPattern;
	}

	private static class AttackCalc {
		int atkDmg;
		int monDmg;
		String patternMsg;
	}

	private static class Resolve {
		boolean killed;
		boolean dropYn;
		int gainExp;
		int levelUpCount;
	}

	private static class CooldownCheck {
		final boolean ok;
		final int remainMinutes;

		private CooldownCheck(boolean ok, int remainMinutes) {
			this.ok = ok;
			this.remainMinutes = remainMinutes;
		}

		static CooldownCheck ok() {
			return new CooldownCheck(true, 0);
		}

		static CooldownCheck block(int remainMin) {
			return new CooldownCheck(false, remainMin);
		}
	}

	/** 레벨업 처리 결과 */
	public static class LevelUpResult {
		public int gainedExp, beforeLv, afterLv, beforeExpCur, afterExpCur, afterExpNext, levelUpCount;
		public int hpMaxDelta, atkMinDelta, atkMaxDelta;
	}

	/** EXP 반영 + 레벨업 처리 */
	private LevelUpResult applyExpAndLevelUp(User u, int gainedExp) {
		LevelUpResult r = new LevelUpResult();
		r.gainedExp = Math.max(0, gainedExp);
		r.beforeLv = u.lv;
		r.beforeExpCur = u.expCur;

		int lv = u.lv;
		int expCur = u.expCur + r.gainedExp;
		int expNext = u.expNext;

		int hpMax = u.hpMax;
		int atkMin = u.atkMin;
		int atkMax = u.atkMax;
		double crit = u.critRate; // % 단위 (예: 0, 2, 4, ...)

		int hpDelta = 0, atkMinDelta = 0, atkMaxDelta = 0;
		double critDelta = 0.0;
		int upCount = 0;

		while (expCur >= expNext) {
			expCur -= expNext;
			lv++;
			upCount++;

			// 다음 레벨 필요치 규칙 (원래 쓰던 규칙 유지)
			expNext = calcNextExp(lv, expNext);

			// ★ 레벨업 보상: HP+10, ATK_MIN+1, ATK_MAX+2, CRIT_RATE+2%
			hpMax += 10;
			hpDelta += 10;
			atkMin += 1;
			atkMinDelta += 1;
			atkMax += 2;
			atkMaxDelta += 2;
			crit += 2.0;
			critDelta += 2.0;
		}

		// 결과 반영
		u.lv = lv;
		u.expCur = expCur;
		u.expNext = expNext;
		u.hpMax = hpMax;
		u.atkMin = atkMin;
		u.atkMax = atkMax;
		u.critRate = Math.max(0, Math.min(100, crit)); // 0~100% 클램프

		r.afterLv = lv;
		r.afterExpCur = expCur;
		r.afterExpNext = expNext;
		r.levelUpCount = upCount;
		r.hpMaxDelta = hpDelta;
		r.atkMinDelta = atkMinDelta;
		r.atkMaxDelta = atkMaxDelta;
		// (원하면 LevelUpResult에 critDelta 필드 추가해서 메시지로도 보여줄 수 있음)
		return r;
	}

	private int calcNextExp(int newLv, int prevExpNext) {
		return prevExpNext + 200 + 250 * newLv; // 필요 시 커스텀
	}

	private static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
