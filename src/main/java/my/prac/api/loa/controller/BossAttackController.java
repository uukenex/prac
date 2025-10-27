package my.prac.api.loa.controller;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.prjbot.service.BotSettleService;


@Controller
public class BossAttackController {
	static Logger logger = LoggerFactory.getLogger(BossAttackController.class);
	@Autowired
	LoaPlayController play;
	
	@Resource(name = "core.prjbot.BotService")
	BotService botService;
	@Resource(name = "core.prjbot.BotNewService")
	BotNewService botNewService;
	
	@Resource(name = "core.prjbot.BotSettleService")
	BotSettleService botSettleService;

	final String enterStr= "♬";
	final String spaceStr= "`";
	final String tabStr= "◐";
	final String allSeeStr = "===";
	final String anotherMsgStr = "®";
	final String listSeparatorStr = "㈜";
	
	public String monsterAttack(HashMap<String, Object> map) {
		map.put("cmd", "monster_attack");
		final String roomName = Objects.toString(map.get("roomName"), "");
		final String userName = Objects.toString(map.get("userName"), "");

		// 1) 유저정보 세팅
		User u = botNewService.selectUser(userName, roomName);
		if (u == null) return "유저 정보를 찾을 수 없습니다.";

		// 진행중 전투 조회 (NOW_YN=1 묶음)
		OngoingBattle ob = botNewService.selectOngoingBattle(userName, roomName);

		// 몬스터정보 세팅
		Monster m;
		int monMaxHp;
		int monHpRemainBefore;
		if (ob != null) {
			// 진행중 전투가 있으면, 그 전투의 몬스터로 진행
			m = botNewService.selectMonsterByNo(ob.monNo);
			if (m == null) return "진행중 몬스터 정보를 찾을 수 없습니다.";
			monMaxHp = m.monHp;
			monHpRemainBefore = Math.max(0, m.monHp - ob.totalDealtDmg);
		} else {
			// 현재 전투가 없으면, 유저의 TARGET_MON으로 신규 전투 시작
			m = botNewService.selectMonsterByNo(u.targetMon);
			if (m == null) return "대상 몬스터가 지정되어 있지 않습니다. (TARGET_MON 없음)";
			monMaxHp = m.monHp;
			monHpRemainBefore = m.monHp;
			// (선택) 전투 시작을 명확히 하려면 0데미지 로그를 먼저 남길 수도 있음
			// botNewService.insertBattleLog(startLog... NOW_YN=1)
		}

		// 2) 쿨타임 체크(5분) + 현재 체력 50% 미만 차단
		CooldownCheck cd = checkCooldown5m(userName, roomName);
		if (!cd.ok) {
			return userName + "님, 공격 쿨타임 " + cd.remainMinutes + "분 남았습니다.";
		}
		if (u.hpCur * 2 < u.hpMax) {
			return userName + "님, 체력이 50% 미만입니다. 회복 후 공격 가능합니다.";
		}

		// 3) 확률 플래그 롤
		Flags flags = rollFlags(u, m);

		// 4) 준데미지/받은데미지 계산
		AttackCalc calc = calcDamage(u, m, flags);

		// 이번 공격으로 처치되는지(남은 HP 기준)
		boolean willKill = calc.atkDmg >= monHpRemainBefore;

		// 5) 처치/드랍율 계산
		Resolve res = resolveKillAndDrop(m, calc, willKill);

		// 6) DB 반영 (유저 HP/EXP/LV 갱신 + 로그 기록)
		persist(userName, roomName, u, m, flags, calc, res);

		// 처치 시: NOW_YN=1 → 0 으로 종료
		if (res.killed) {
			botNewService.closeOngoingBattle(userName, roomName);
		}

		// 7) 메시지 생성 (메서드 분리)
		return buildAttackMessage(
				userName, u, m, flags, calc, res,
				monHpRemainBefore, monMaxHp
		);
	}

	/* ===== Helpers ===== */

	private CooldownCheck checkCooldown5m(String userName, String roomName) {
		Timestamp last = botNewService.selectLastAttackTime(userName, roomName);
		if (last == null) return CooldownCheck.ok();
		long sec = Duration.between(last.toInstant(), Instant.now()).getSeconds();
		if (sec >= 300) return CooldownCheck.ok();
		long remain = 300 - sec;
		return CooldownCheck.block((int)Math.ceil(remain / 60.0));
	}

	private Flags rollFlags(User u, Monster m) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Flags f = new Flags();
		// 크리티컬
		f.atkCrit = r.nextDouble(0, 100) < clamp(u.critRate, 0, 100);
		// 몬스터 패턴: MON_PATTEN = 1~4 -> 1:대기, 2:공격, 3:방어, 4:필살
		if (m.monPatten <= 1) f.monPattern = 1;
		else f.monPattern = r.nextInt(1, m.monPatten + 1); // 1~patten
		return f;
	}

	private AttackCalc calcDamage(User u, Monster m, Flags f) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		AttackCalc c = new AttackCalc();

		// 유저 공격력
		int baseAtk = r.nextInt(u.atkMin, u.atkMax + 1);
		if (f.atkCrit) {
			double mult = Math.max(1.0, u.critDmg / 100.0); // CRIT_DMG=150 -> x1.5
			c.atkDmg = (int)Math.round(baseAtk * mult);
		} else {
			c.atkDmg = baseAtk;
		}

		// 몬스터 행동별 피해(간단룰)
		// 1:대기 -> 0, 2:공격 -> MON_ATK, 3:방어 -> 유저댐 50%감소, 4:필살 -> MON_ATK*1.5
		switch (f.monPattern) {
			case 1: // WAIT
				c.monDmg = 0;
				break;
			case 2: // ATTACK
				c.monDmg = m.monAtk;
				break;
			case 3: // DEFEND
				c.atkDmg = (int)Math.round(c.atkDmg * 0.5);
				c.monDmg = 0;
				break;
			case 4: // SPECIAL
				c.monDmg = (int)Math.round(m.monAtk * 1.5);
				break;
			default:
				c.monDmg = 0;
		}
		return c;
	}

	private Resolve resolveKillAndDrop(Monster m, AttackCalc c, boolean willKill) {
		Resolve r = new Resolve();
		r.killed = willKill;
		// 경험치: 처치 시 풀, 그렇지 않으면 일부(예: EXP/5, 최소 0~1 사이 보정)
		r.gainExp = r.killed ? m.monExp : Math.max(0, Math.min(1, m.monExp / 5));
		// 드랍율(예: 30%)
		r.dropYn = r.killed && ThreadLocalRandom.current().nextDouble(0, 100) < 30.0;
		return r;
	}

	private void persist(String userName, String roomName, User u, Monster m, Flags f, AttackCalc c, Resolve r) {
		// 유저 HP 반영
		int newHp = Math.max(0, u.hpCur - c.monDmg);

		// 경험치/레벨업 처리 (샘플 곡선)
		int newLv = u.lv;
		int newExpCur = u.expCur + r.gainExp;
		int newExpNext = u.expNext;
		int levelUpCount = 0;

		while (newExpCur >= newExpNext) {
			newExpCur -= newExpNext;
			newLv += 1;
			levelUpCount++;
			newExpNext += 1000 + (newLv * 1500); // 필요 시 조정
			// 레벨업 시 기본치 상승(샘플): HP_MAX +10, ATK +1~2
			u.hpMax = (int)Math.round(u.hpMax + 10);
			u.atkMin += 1;
			u.atkMax += 2;
		}
		r.levelUpCount = levelUpCount;

		// 유저 업데이트
		botNewService.updateUserAfterBattle(
				userName, roomName,
				newLv, newExpCur, newExpNext,
				newHp, u.hpMax, u.atkMin, u.atkMax
		);

		// 로그 적재 (현재 전투 묶음: NOW_YN=1 계속 누적)
		botNewService.insertBattleLog(new BattleLog()
				.setUserName(userName)
				.setRoomName(roomName)
				.setLv(u.lv) // 공격 시점 레벨
				.setTargetMonLv(m.monNo) // MON_NO
				.setGainExp(r.gainExp)
				.setAtkDmg(c.atkDmg)
				.setMonDmg(c.monDmg)
				.setAtkCritYn(f.atkCrit ? 1 : 0)
				.setMonPatten(f.monPattern)
				.setKillYn(r.killed ? 1 : 0)
				.setNowYn(1)
				.setDropYn(r.dropYn ? 1 : 0)
		);
	}

	/** 메시지 생성 분리 */
	private String buildAttackMessage(
			String userName,
			User u,
			Monster m,
			Flags flags,
			AttackCalc calc,
			Resolve res,
			int monHpRemainBefore,
			int monMaxHp
	) {
		StringBuilder sb = new StringBuilder();
		sb.append(userName).append("님, ").append(m.monName).append("을(를) 공격!").append(enterStr);
		if (flags.atkCrit) sb.append("치명타 발생!").append(enterStr);

		int monHpAfter = Math.max(0, monHpRemainBefore - calc.atkDmg);

		sb.append("가한 피해: ").append(calc.atkDmg)
		  .append(" / 받은 피해: ").append(calc.monDmg).append(enterStr);
		sb.append("몬스터 HP: ").append(monHpAfter).append("/").append(monMaxHp).append(enterStr);

		if (res.killed) {
			sb.append("처치 성공! +경험치 ").append(res.gainExp).append(enterStr);
			if (res.dropYn) sb.append("드랍 획득: ").append(m.monDrop).append(enterStr);
		}
		sb.append("현재 체력: ").append(Math.max(0, u.hpCur - calc.monDmg))
		  .append(" / ").append(u.hpMax).append(enterStr);

		if (res.levelUpCount > 0) {
			sb.append("레벨업! +").append(res.levelUpCount)
			  .append(" → Lv.").append(u.lv + res.levelUpCount).append(enterStr);
		}
		return sb.toString();
	}

	/* ---------------- DTOs ---------------- */

	public static class User {
		public String userName;
		public String roomName;
		public int lv;
		public int expCur;
		public int expNext;
		public int hpCur;
		public int hpMax;
		public int hpRegen;
		public int atkMin;
		public int atkMax;
		public double critRate; // %
		public double critDmg;  // %
		public int targetMon;
	}

	public static class Monster {
		public int monNo;
		public int monHp;
		public int monAtk;
		public int monExp;
		public String monDrop;
		public int monPatten;
		public String monName;
	}

	public static class BattleLog {
		private String userName;
		private String roomName;
		private int lv;
		private int targetMonLv;
		private int gainExp;
		private int atkDmg;
		private int monDmg;
		private int atkCritYn;
		private int monPatten;
		private int killYn;
		private int nowYn;
		private int dropYn;

		public BattleLog setUserName(String v){this.userName=v;return this;}
		public BattleLog setRoomName(String v){this.roomName=v;return this;}
		public BattleLog setLv(int v){this.lv=v;return this;}
		public BattleLog setTargetMonLv(int v){this.targetMonLv=v;return this;}
		public BattleLog setGainExp(int v){this.gainExp=v;return this;}
		public BattleLog setAtkDmg(int v){this.atkDmg=v;return this;}
		public BattleLog setMonDmg(int v){this.monDmg=v;return this;}
		public BattleLog setAtkCritYn(int v){this.atkCritYn=v;return this;}
		public BattleLog setMonPatten(int v){this.monPatten=v;return this;}
		public BattleLog setKillYn(int v){this.killYn=v;return this;}
		public BattleLog setNowYn(int v){this.nowYn=v;return this;}
		public BattleLog setDropYn(int v){this.dropYn=v;return this;}
	}

	private static class Flags {
		boolean atkCrit;
		int monPattern; // 1~4
	}

	private static class AttackCalc {
		int atkDmg;
		int monDmg;
	}

	private static class Resolve {
		boolean killed;
		boolean dropYn;
		int gainExp;
		int levelUpCount;
	}

	public static class OngoingBattle {
		public int monNo;          // TARGET_MON_LV = MON_NO
		public int totalDealtDmg;  // NOW_YN=1 묶음에서 누적 가한 피해 합계
	}

	private static class CooldownCheck {
		final boolean ok;
		final int remainMinutes;
		private CooldownCheck(boolean ok, int remainMinutes) { this.ok = ok; this.remainMinutes = remainMinutes; }
		static CooldownCheck ok(){ return new CooldownCheck(true, 0); }
		static CooldownCheck block(int remainMin){ return new CooldownCheck(false, remainMin); }
	}

	private static double clamp(double v, double min, double max){ return Math.max(min, Math.min(max, v)); }
}