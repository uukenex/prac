const fs = require('fs');
const file = 'src/main/java/my/prac/api/loa/controller/BossAttackController.java';
let content = fs.readFileSync(file, 'utf8');

const METHOD_START = content.indexOf('\tpublic String monsterAttack(HashMap<String, Object> map) {');
const METHOD_END   = content.indexOf('\r\n\t/** \uae30\uc874 \ud638\ud658\uc6a9 \uc624\ubc84\ub85c\ub4dc', METHOD_START);

if (METHOD_START < 0 || METHOD_END < 0) {
    console.error('FAIL: method boundaries not found', METHOD_START, METHOD_END);
    process.exit(1);
}
console.log('Found monsterAttack:', METHOD_START, '->', METHOD_END, '(', METHOD_END - METHOD_START, 'chars)');

// 새 코드 (LF → 마지막에 CRLF 변환)
const newCode = `\tpublic String monsterAttack(HashMap<String, Object> map) {
\t\tmap.put("cmd", "monster_attack");
\t\tAttackSession s = new AttackSession(map);
\t\tString earlyMsg;

\t\t// 0~1) \uc785\ub825 \uac80\uc99d / \ub9e4\ud06c\ub85c \uc7a0\uae08
\t\tif ((earlyMsg = ma_validate(s)) != null) return earlyMsg;

\t\t// 2~4) \uacf5\ud1b5 \uc2a4\ud0ef + \uc9c1\uc5c5 \uacf5\uaca9\ubc30\uc728
\t\tif ((earlyMsg = ma_calcStats(s)) != null) return earlyMsg;

\t\t// 5~6) \ubd80\ud65c \ucc98\ub9ac / \uc9c4\ud589\uc911\xb7\uc2e0\uaddc \ubaac\uc2a4\ud130 \uc124\uc815
\t\tif ((earlyMsg = ma_resolveMonster(s)) != null) return earlyMsg;

\t\t// 7) \ucfe8\ud0c0\uc784\xb78) HP \ud655\uc815 / [S3] \ud5f0\ubcf4\uc2a4 \ubd84\uae30
\t\tif ((earlyMsg = ma_cooldownAndHp(s)) != null) return earlyMsg;

\t\t// 8-\ud6c4) berserkMul + Flags \ub864
\t\tma_preDmgJobBuffs(s);

\t\t// 9~11) HP5% \uc81c\ud55c / \ub3c4\uc0ac\ubc84\ud504 / \uc2a4\ud398\uc140\ubc84\ud504 / \ub370\ubbf8\uc9c0 \uacc4\uc0b0
\t\tif ((earlyMsg = ma_applyBuffsAndCalcDmg(s)) != null) return earlyMsg;

\t\t// [\ub3c4\uc801] 2\ud0c0 \uc0ac\uc804 \uacc4\uc0b0
\t\tma_thiefDoubleAtkPreCalc(s);

\t\t// 12) \uc0ac\ub9dd \ucc98\ub9ac
\t\tif ((earlyMsg = ma_deathCheck(s)) != null) return earlyMsg;

\t\t// 13) \ucc98\uce58\xb7\ub4dc\ub78d \ud310\ub2e8 + \uc9c1\uc5c5\ubcc4 \uc2a4\ud0ac
\t\tma_resolveKillAndJobSkills(s);

\t\t// 14) DB \ubc18\uc601 + \uc5c5\uc801 \ubd80\uc5ec
\t\tma_persistAndAchv(s);

\t\t// 15~16) \uba54\uc2dc\uc9c0 \uad6c\uc131 + \ud3ec\uc778\ud2b8 \ucd9c\ub825
\t\treturn ma_buildMessage(s);
\t}

\t// \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\t//  AttackSession \u2014 monsterAttack \uc12c\uc158 \uac04 \uacf5\uc720 \uc0c1\ud0dc
\t// \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate static class AttackSession {
\t\t/* \uc785\ub825 */
\t\tHashMap<String,Object> map;
\t\tString userName, roomName, param1;
\t\tboolean master;

\t\t/* \uc2a4\ud0ef / \uc9c1\uc5c5 */
\t\tUserBattleContext ctx;
\t\tUser u;
\t\tString job;
\t\tint effAtkMin, effAtkMax, critRate, critDmg, hpMax, regen;
\t\tdouble berserkMul = 1.0;

\t\t/* \ubaac\uc2a4\ud130 */
\t\tMonster m;
\t\tint monMaxHp, monHpRemainBefore, monAtk, monLv;
\t\tboolean lucky, dark, gray, nightmare, hell;
\t\tint beforeJobSkillYn;
\t\tint killCountForThisMon, nmKillCountForThisMon, hellKillCountForThisMon;

\t\t/* \ubc84\ud504 / \ucfe8\ud0c0\uc784 */
\t\tHashMap<String,Object> activeBuff;
\t\tSpecialBuffResult buff;
\t\tint cooldownBuff;
\t\tString cdJob;
\t\tAttackDeathStat cachedAds;
\t\tboolean revivedThisTurn;

\t\t/* \ub370\ubbf8\uc9c0 */
\t\tFlags flags;
\t\tDamageOutcome dmg, dmg2;
\t\tAttackCalc calc, calc2;
\t\tboolean willKill, thiefDoubleAtk;

\t\t/* \ucc98\uce58 / \ubcf4\uc0c1 */
\t\tResolve res;
\t\tLevelUpResult up;
\t\tint buffStart, buffIng;
\t\tString buffCode;

\t\t/* \uc5c5\uc801 */
\t\tList<KillStat> cachedKillStats;
\t\tList<AchievementCount> userAchvList;
\t\tSet<String>         achievedCmdSet = new HashSet<>();
\t\tMap<String,Integer> globalAchvMap  = new HashMap<>();
\t\tMap<String,Integer> userAchvMap    = new HashMap<>();

\t\t/* \uba54\uc2dc\uc9c0 \uc870\uac01 */
\t\tString dosabuffMsg = "";
\t\tString stealMsg = "", stealPoint = "", stealBonus = "";
\t\tString newPoint = "", newBonus  = "";
\t\tString dosaCastMsg = null;
\t\tString bagDropMsg  = "";
\t\tString bonusMsg    = "";

\t\tAttackSession(HashMap<String,Object> map) {
\t\t\tthis.map      = map;
\t\t\tthis.userName = Objects.toString(map.get("userName"), "");
\t\t\tthis.roomName = Objects.toString(map.get("roomName"), "");
\t\t}
\t}

\t// \u2500 0~1) \uc785\ub825 \uac80\uc99d / \ub9e4\ud06c\ub85c \uc7a0\uae08 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_validate(AttackSession s) {
\t\tif (s.roomName.isEmpty() || s.userName.isEmpty())
\t\t\treturn "\ubc29/\uc720\uc800 \uc815\ubcf4\uac00 \ub204\ub77d\ub418\uc5c8\uc2b5\ub2c8\ub2e4.";

\t\ts.master = "\ub78c\ucfb4\ubd07 \ubb38\uc758\ubc29".equals(s.roomName) && "\uc77c\uc5b4\ub09c\ub2e4\ub78c\ucfb4/\uce74\ub2e8".equals(s.userName);
\t\tif (s.master) s.map.put("param1", "test");

\t\tif ("\ub78c\ucfb4\ubd07 \ubb38\uc758\ubc29".equals(s.roomName) && !s.master)
\t\t\treturn "\ubb38\uc758\ubc29\uc5d0\uc11c\ub294 \ubd88\uac00\ub2a5\ud569\ub2c8\ub2e4.";

\t\tHashMap<String,Object> lockParam = botNewService.lockMacroUser(s.userName);
\t\tint lockCode = (Integer) lockParam.get("outCode");
\t\tif (lockCode == 1 || lockCode == 2)
\t\t\treturn "\uacf5\uaca9\ubd88\uac00 \uc0c1\ud0dc\uc785\ub2c8\ub2e4 code:" + lockParam.get("outMsg");

\t\ts.param1 = Objects.toString(s.map.get("param1"), "");
\t\treturn null;
\t}

\t// \u2500 2~4) \uacf5\ud1b5 \uc2a4\ud0ef + \uc9c1\uc5c5 \uacf5\uaca9\ubc30\uc728 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_calcStats(AttackSession s) {
\t\tHashMap<String,Object> statMap = new HashMap<>(s.map);
\t\tstatMap.put("param1", "");
\t\ts.ctx = calcUserBattleContext(statMap);
\t\tif (!s.ctx.success) return s.ctx.errorMessage;

\t\ts.u   = s.ctx.user;
\t\ts.job = (s.u.job == null ? "" : s.u.job.trim());
\t\tif (s.job.isEmpty())
\t\t\treturn s.userName + " \ub2d8, /\uc9c1\uc5c5 \uc744 \ud1b5\ud574 \uba3c\uc800 \uc804\uc9c1\ud574\uc8fc\uc138\uc694." + NL
\t\t\t\t + "12/15 \uc5c5\ub370\uc774\ud2b8 \uc774\ud6c4 \uac00\ubc29\uc73c\ub85c \ub2a5\ub825\uce58 \ubcc0\uacbd\uc744 \ud655\uc778\ud574\uc8fc\uc138\uc694.";

\t\tint atkMin = s.ctx.atkMin;
\t\tint atkMax = s.ctx.atkMax;
\t\ts.regen   = s.ctx.regen;
\t\ts.hpMax   = s.ctx.hpMax;
\t\ts.critRate = s.ctx.crit;
\t\ts.critDmg  = s.ctx.critDmg;

\t\tdouble jobDmgMul  = 1.0;
\t\tint    jobBonusMin = 0, jobBonusMax = 0;
\t\tif      ("\uad81\uc218".equals(s.job))   jobDmgMul = 3.0;
\t\telse if ("\uc0ac\ub099\uaf3c".equals(s.job))  jobDmgMul = 3.0;
\t\telse if ("\uad81\uc0ac".equals(s.job))   jobDmgMul = 1.0;
\t\telse if ("\uc804\uc0ac".equals(s.job))   jobDmgMul = 1.4;
\t\telse if ("\uac80\uc131".equals(s.job))   jobDmgMul = 2.2;
\t\telse if ("\uc5b4\uc4f0\uc2e0".equals(s.job))  jobDmgMul = 1.3;
\t\telse if ("\uc81c\ub108\ub7f4".equals(s.job))  jobDmgMul = 1.2;
\t\telse if ("\uc800\uaca9\uc218".equals(s.job))  jobDmgMul = 2.0;
\t\telse if ("\ucc98\ub2e8\uc790".equals(s.job))  jobDmgMul = 1.4;
\t\telse if ("\uc6a9\uc0ac".equals(s.job))   jobDmgMul = 1.4;
\t\telse if ("\ubcf5\uc218\uc790".equals(s.job))  jobDmgMul = 0.2;
\t\telse if ("\uc74c\uc591\uc0ac".equals(s.job))  jobDmgMul = 1.6;

\t\ts.effAtkMin = (int)Math.round(atkMin * jobDmgMul + jobBonusMin);
\t\ts.effAtkMax = (int)Math.round(atkMax * jobDmgMul + jobBonusMax);
\t\tif (s.effAtkMax < s.effAtkMin) s.effAtkMax = s.effAtkMin;
\t\treturn null;
\t}

\t// \u2500 5~6) \ubd80\ud65c / \uc9c4\ud589\uc911\xb7\uc2e0\uaddc \ubaac\uc2a4\ud130 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_resolveMonster(AttackSession s) {
\t\tString reviveMsg = reviveAfter1hIfDead(s.userName, s.roomName, s.u, s.hpMax, s.regen);
\t\tif (reviveMsg != null) {
\t\t\tif (!reviveMsg.isEmpty()) return reviveMsg;
\t\t\ts.revivedThisTurn = true;
\t\t}

\t\tList<AchievementCount> globalList = getAchvGlobalCached();
\t\tif (globalList != null) {
\t\t\tfor (AchievementCount ac : globalList) {
\t\t\t\tif (ac == null || ac.getCmd() == null) continue;
\t\t\t\ts.globalAchvMap.put(ac.getCmd(), ac.getCnt());
\t\t\t}
\t\t}

\t\ts.nightmare = s.ctx.user.nightmareYn >= 1;
\t\ts.hell      = s.ctx.user.nightmareYn == 2;

\t\ttry { s.cachedKillStats = botNewService.selectKillStats(s.userName, s.roomName); } catch (Exception ignore) {}

\t\tOngoingBattle ob = botNewService.selectOngoingBattle(s.userName, s.roomName);
\t\treturn (ob != null) ? ma_resolveOngoing(s, ob) : ma_resolveNew(s);
\t}

\tprivate String ma_resolveOngoing(AttackSession s, OngoingBattle ob) {
\t\ts.m = getMonsterCached(ob.monNo);
\t\tif (s.m == null) return "\uc9c4\ud589\uc911 \ubaac\uc2a4\ud130 \uc815\ubcf4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4.";
\t\ts.beforeJobSkillYn = ob.beforeJobSkillYn;
\t\ts.monMaxHp = s.m.monHp;  s.monAtk = s.m.monAtk;  s.monLv = s.m.monLv;
\t\tif (s.nightmare) {
\t\t\ts.monMaxHp *= NM_MUL_HP_ATK;
\t\t\ts.monAtk   *= NM_MUL_HP_ATK;
\t\t\ts.monLv    += s.hell ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
\t\t}
\t\ts.lucky = (ob.luckyYn != null && ob.luckyYn == 1);
\t\ts.dark  = (ob.luckyYn != null && ob.luckyYn == 2);
\t\ts.gray  = (ob.luckyYn != null && ob.luckyYn == 3);
\t\tif (s.dark) applyDarkMonsterScale(s);
\t\ts.monHpRemainBefore = Math.max(0, s.monMaxHp - ob.totalDealtDmg);
\t\tresolveKillCounts(s);
\t\treturn null;
\t}

\tprivate String ma_resolveNew(AttackSession s) {
\t\tif (s.u.targetMon == 99) {
\t\t\ts.m = null; s.monMaxHp = 0; s.monHpRemainBefore = 0; s.monAtk = 0; s.monLv = 0;
\t\t\treturn null;
\t\t}
\t\ts.m = getMonsterCached(s.u.targetMon);
\t\tif (s.m == null) return "\ub300\uc0c1 \ubaac\uc2a4\ud130\uac00 \uc9c0\uc815\ub418\uc5b4 \uc788\uc9c0 \uc54a\uc2b5\ub2c8\ub2e4. (TARGET_MON \uc5c6\uc74c)";
\t\ts.beforeJobSkillYn  = -1;
\t\ts.monMaxHp          = s.m.monHp;
\t\ts.monHpRemainBefore = s.m.monHp;
\t\ts.monAtk            = s.m.monAtk;
\t\ts.monLv             = s.m.monLv;
\t\tif (s.nightmare) {
\t\t\ts.monMaxHp          *= NM_MUL_HP_ATK;
\t\t\ts.monHpRemainBefore *= NM_MUL_HP_ATK;
\t\t\ts.monAtk            *= NM_MUL_HP_ATK;
\t\t\ts.monLv             += s.hell ? HEL_ADD_MON_LV : NM_ADD_MON_LV;
\t\t}
\t\tresolveKillCounts(s);
\t\trollDarkAndLucky(s);
\t\treturn null;
\t}

\tprivate void resolveKillCounts(AttackSession s) {
\t\tif (s.cachedKillStats == null || s.m == null) return;
\t\tfor (KillStat ks : s.cachedKillStats) {
\t\t\tif (ks.monNo == s.m.monNo) {
\t\t\t\ts.killCountForThisMon     = ks.killCount;
\t\t\t\ts.nmKillCountForThisMon   = ks.nmKillCount;
\t\t\t\ts.hellKillCountForThisMon = ks.hellKillCount;
\t\t\t\tbreak;
\t\t\t}
\t\t}
\t}

\tprivate void applyDarkMonsterScale(AttackSession s) {
\t\tif      (s.m.monNo < 15)  { s.monMaxHp = s.monMaxHp * 3;                              s.monAtk = (int)Math.round(s.monAtk * 1.50); }
\t\telse if (s.m.monNo >= 25) { s.monMaxHp = (int)Math.round(s.monMaxHp * 1.75); s.monAtk = (int)Math.round(s.monAtk * 1.10); }
\t\telse if (s.m.monNo >= 15) { s.monMaxHp = (int)Math.round(s.monMaxHp * 2.50); s.monAtk = (int)Math.round(s.monAtk * 1.25); }
\t}

\tprivate void rollDarkAndLucky(AttackSession s) {
\t\tMonster m = s.m;
\t\tint levelGap = s.monLv - s.u.lv;
\t\tdouble darkRate = Math.max(0, levelGap / 100) * 0.20;
\t\tif ("\uc5b4\ub461\uc0ac\ub099\uaf3c".equals(s.job)) darkRate += DARK_RATE_DARK;
\t\tif ((!s.nightmare && s.killCountForThisMon   >= 350 && m.monNo >= 15) || (s.nightmare && s.nmKillCountForThisMon > 150 && m.monNo >= 15)) darkRate += 0.05;
\t\tif ((!s.nightmare && s.killCountForThisMon   >= 300 && m.monNo <  15) || (s.nightmare && s.nmKillCountForThisMon > 150 && m.monNo <  15)) darkRate += 0.10;
\t\tif (ThreadLocalRandom.current().nextDouble() < darkRate) s.dark = true;

\t\tdouble luckyRate = "\ub3c4\uc0ac".equals(s.job) ? LUCKY_RATE_DOSA : LUCKY_RATE;
\t\ts.lucky = (s.killCountForThisMon >= 50) && ThreadLocalRandom.current().nextDouble() < luckyRate;

\t\tint globalCnt = s.globalAchvMap.getOrDefault("ACHV_FIRST_CLEAR_MON_" + m.monNo, 0);
\t\tif (s.dark  || globalCnt == 0 || m.monNo > 50)                         s.lucky = false;
\t\tif (s.lucky || globalCnt == 0 || m.monNo > 50 || "\uc0ac\uc2e0".equals(s.job)) s.dark  = false;

\t\tif ("\uc74c\uc591\uc0ac".equals(s.job)) s.gray = ThreadLocalRandom.current().nextDouble() < 0.05;
\t\tif (s.gray) { s.lucky = false; s.dark = false; }
\t\tif ("\uacf0".equals(s.job))  { s.lucky = false; s.dark = false; }

\t\tif (s.dark) { applyDarkMonsterScale(s); s.monHpRemainBefore = s.monMaxHp; }
\t}

\t// \u2500 7) \ucfe8\ud0c0\uc784 / 8) HP \ud655\uc815 / [S3] \ubd84\uae30 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_cooldownAndHp(AttackSession s) {
\t\ts.buff = handleSpecialBuff(s.userName);
\t\ts.activeBuff   = s.buff.activeBuff;
\t\ts.cooldownBuff = 0;
\t\tif (s.activeBuff != null) {
\t\t\tif ("\ucfe8\ud0c0\uc784".equals(s.activeBuff.get("FLAG_CODE")))
\t\t\t\ts.cooldownBuff = (int)Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\tif ("\ucfe8\ud0c0\uc784\uac10\uc18c".equals(s.activeBuff.get("FLAG_CODE")))
\t\t\t\ts.cooldownBuff = -1;
\t\t}

\t\ttry { s.cachedAds = botNewService.selectAttackDeathStats(s.userName, s.roomName); } catch (Exception ignore) {}
\t\tTimestamp cachedLastAtk = (s.cachedAds != null) ? s.cachedAds.lastAttackTime : null;
\t\ts.cdJob = (s.cachedAds != null && s.cachedAds.lastAttackJob != null) ? s.cachedAds.lastAttackJob : s.job;

\t\tCooldownCheck cd = checkCooldown(s.userName, s.roomName, s.param1, s.cdJob, s.cooldownBuff, cachedLastAtk);
\t\tif (!cd.ok) {
\t\t\tlong min = cd.remainSeconds / 60;
\t\t\tlong sec = cd.remainSeconds % 60;
\t\t\treturn String.format("%s\ub2d8, \uacf5\uaca9 \ucfe8\ud0c0\uc784 %d\ubd84 %d\ucd08 \ub0a8\uc558\uc2b5\ub2c8\ub2e4.", s.userName, min, sec);
\t\t}

\t\tint effectiveHp = s.revivedThisTurn
\t\t\t\t? s.u.hpCur
\t\t\t\t: computeEffectiveHpFromLastAttack(s.userName, s.roomName, s.u, s.hpMax, s.regen, cachedLastAtk);
\t\ts.u.hpCur = effectiveHp;

\t\tif (s.u.targetMon == 99) return bossAttackS3Controller.attackBossS3(s.map, s.ctx);

\t\ts.userAchvList = botNewService.selectAchvCountsGlobal(s.userName, s.roomName);
\t\tif (s.userAchvList != null) {
\t\t\tfor (AchievementCount ac : s.userAchvList) {
\t\t\t\ts.achievedCmdSet.add(ac.getCmd());
\t\t\t\tif (ac.getCmd() != null) s.userAchvMap.put(ac.getCmd(), ac.getCnt());
\t\t\t}
\t\t}
\t\treturn null;
\t}

\t// \u2500 8-\ud6c4) berserkMul + Flags \ub864 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate void ma_preDmgJobBuffs(AttackSession s) {
\t\tif ("\ud30c\uc774\ud130".equals(s.job) && s.hpMax > 0) {
\t\t\tdouble hpRatio = (double) s.u.hpCur / s.hpMax;
\t\t\tif (hpRatio < 1) s.berserkMul = 1.0 + (1 - hpRatio) * 0.5;
\t\t}
\t\tif ("\uc6a9\uc0ac".equals(s.job)    && s.dark)              s.berserkMul = 1.5;
\t\tif ("\ucc98\ub2e8\uc790".equals(s.job)  && s.lucky)             s.berserkMul = 1.5;
\t\tif ("\uc74c\uc591\uc0ac".equals(s.job)  && (s.lucky || s.dark)) s.berserkMul = 1.5;
\t\tif ("\uc5b4\ub451\uc0ac\ub099\uaf3c".equals(s.job) && s.dark)   s.berserkMul = 3.0;
\t\ts.flags = rollFlags(s.u, s.m);
\t}

\t// \u2500 9~11) HP5% \uc81c\ud55c / \ub3c4\uc0ac\ubc84\ud504 / \uc2a4\ud398\uc140\ubc84\ud504 / \ub370\ubbf8\uc9c0 \uacc4\uc0b0 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_applyBuffsAndCalcDmg(AttackSession s) {
\t\t// 9) HP 5% \uc81c\ud55c \uccb4\ud06c
\t\tint origHpMax  = s.u.hpMax;
\t\tint origRegen  = s.u.hpRegen;
\t\ts.u.hpMax   = s.hpMax;
\t\ts.u.hpRegen = s.regen;
\t\ttry {
\t\t\tString hpMsg = buildBelowHalfMsg(s.userName, s.roomName, s.u, s.param1, s.cooldownBuff, s.cdJob);
\t\t\tif (!"\uc0ac\uc2e0".equals(s.job) && hpMsg != null) return hpMsg;
\t\t} finally {
\t\t\ts.u.hpMax   = origHpMax;
\t\t\ts.u.hpRegen = origRegen;
\t\t}

\t\t// 10) \ub3c4\uc0ac \ubc84\ud504 (\ubcf8\uc778 + \ubc29 \uc804\uccb4, \ud5f0 \uc81c\uc678)
\t\tif (!s.hell) {
\t\t\tDosaBuffEffect self = null;
\t\t\tif ("\ub3c4\uc0ac".equals(s.job) || "\uc74c\uc591\uc0ac".equals(s.job)) {
\t\t\t\tself = buildDosaBuffEffect(s.u, s.u.lv, s.roomName, 1);
\t\t\t\ts.effAtkMin += self.addAtkMin;  s.effAtkMax += self.addAtkMax;
\t\t\t\ts.critRate  += self.addCritRate; s.critDmg   += self.addCritDmg;
\t\t\t\ts.u.hpCur   += self.addHp;
\t\t\t}
\t\t\tDosaBuffEffect room = loadRoomDosaBuffAndBuild(s.roomName);
\t\t\tif (room != null) {
\t\t\t\ts.effAtkMin += room.addAtkMin;  s.effAtkMax += room.addAtkMax;
\t\t\t\ts.critRate  += room.addCritRate; s.critDmg   += room.addCritDmg;
\t\t\t\ts.u.hpCur   += room.addHp;
\t\t\t\tbotNewService.clearRoomBuff(s.roomName);
\t\t\t}
\t\t\tif (room != null || self != null) s.dosabuffMsg = buildUnifiedDosaBuffMessage(self, room);
\t\t}

\t\ts.u.hunterGrade = s.ctx.hunterGrade;

\t\t// \uc2a4\ud398\uc140\ubc84\ud504 \uc801\uc6a9 (\uacf5\uaca9\ub825/\uce58\ud53c/\uce58\ud655)
\t\tif (s.activeBuff != null) {
\t\t\tif ("\uacf5\uaca9\ub825".equals(s.activeBuff.get("FLAG_CODE"))) {
\t\t\t\tString effectType = (String) s.activeBuff.get("EFFECT_TYPE");
\t\t\t\tdouble value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\t\tif ("\ubc30\uc728".equals(effectType)) { s.effAtkMin = (int)Math.round(s.effAtkMin * value); s.effAtkMax = (int)Math.round(s.effAtkMax * value); }
\t\t\t\telse                                   { s.effAtkMin += (int)value; s.effAtkMax += (int)value; }
\t\t\t}
\t\t\tif ("\uce58\ud53c".equals(s.activeBuff.get("FLAG_CODE"))) {
\t\t\t\tdouble value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\t\tif (s.hell) value = Math.max(0, (int)Math.round(value * MiniGameUtil.getHellNerfMult(s.ctx.hunterGrade)));
\t\t\t\ts.critDmg += (int)value;
\t\t\t}
\t\t\tif ("\uce58\ud655".equals(s.activeBuff.get("FLAG_CODE"))) {
\t\t\t\tdouble value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\t\tif (s.hell) value = Math.max(0, (int)Math.round(value * MiniGameUtil.getHellNerfMult(s.ctx.hunterGrade)));
\t\t\t\ts.critRate += (int)value;
\t\t\t}
\t\t}

\t\tboolean hasBless = (s.u.blessYn == 1);
\t\tif (hasBless) {
\t\t\ts.effAtkMin = (int)Math.round(s.effAtkMin * 1.5);
\t\t\ts.effAtkMax = (int)Math.round(s.effAtkMax * 1.5);
\t\t}

\t\t// 11) \ub370\ubbf8\uc9c0 \uacc4\uc0b0
\t\ts.dmg = calculateDamage(s.u, s.m, s.flags,
\t\t\t\ts.effAtkMin, s.effAtkMax, s.critRate, s.critDmg,
\t\t\t\ts.berserkMul, s.monHpRemainBefore, s.hpMax, s.beforeJobSkillYn, s.nightmare);
\t\ts.calc     = s.dmg.calc;
\t\ts.flags    = s.dmg.flags;
\t\ts.willKill = s.dmg.willKill;

\t\t// 11-\ud6c4) \ucd95\ubcf5\uc220\uc0ac
\t\tif ("\ucd95\ubcf5\uc220\uc0ac".equals(s.job) && s.dmg.calc.atkDmg > 0) {
\t\t\tint blessCount = (s.u.lv / 100) + 1;
\t\t\tint cnt = botNewService.updateRandomBlessUser(s.userName, blessCount);
\t\t\tif (cnt > 0) s.dmg.dmgCalcMsg += NL + "\u2728\ub79c\ub364\ud55c " + blessCount + "\uba85\uc5d0\uac8c \ucd95\ubcf5\uc774 \ub0b4\ub824\uc84c\uc2b5\ub2c8\ub2e4!";
\t\t}

\t\t// 11-\ud6c4) \ucd95\ubcf5 \ud790
\t\tif (hasBless) {
\t\t\tint heal = (int)Math.round(s.hpMax * 0.3);
\t\t\tint beforeHp = s.u.hpCur;
\t\t\ts.u.hpCur = Math.min(s.hpMax, s.u.hpCur + heal);
\t\t\tif (s.u.hpCur > beforeHp) s.dmg.dmgCalcMsg += NL + "\u2728 \ucd95\ubcf5\uc758 \uce58\uc720! " + (s.u.hpCur - beforeHp) + " \ud68c\ubcf5";
\t\t\tbotNewService.clearBlessYn(s.userName);
\t\t}

\t\t// 11-\ud6c4) \uc2a4\ud398\uc140\ubc84\ud504 \ud68c\ubcf5
\t\tif (s.activeBuff != null && "\ud68c\ubcf5".equals(s.activeBuff.get("FLAG_CODE"))) {
\t\t\tdouble value = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\tint heal = (int)Math.round(s.hpMax * value);
\t\t\tint beforeHp = s.u.hpCur;
\t\t\ts.u.hpCur = Math.min(s.hpMax, s.u.hpCur + heal);
\t\t\tif (s.u.hpCur > beforeHp) s.dmg.dmgCalcMsg += NL + "\u2728 \uc2a4\ud398\uc140\ud0c0\uc784-\ud68c\ubcf5! " + (s.u.hpCur - beforeHp);
\t\t}

\t\t// \ud328\ud134 6: \uc804\ud22c \uc885\ub8cc
\t\tif (s.calc.endBattle) {
\t\t\tbotNewService.closeOngoingBattleTx(s.userName, s.roomName);
\t\t\tResolve empty = new Resolve(); empty.killed = false; empty.gainExp = 0; empty.dropCode = "0";
\t\t\treturn buildAttackMessage(s.userName, s.u, s.m, s.flags, s.calc,
\t\t\t\t\tempty, null, s.monHpRemainBefore, s.monMaxHp,
\t\t\t\t\ts.effAtkMin, s.effAtkMax, s.hpMax, null, null, null, s.nightmare, s.ctx);
\t\t}
\t\treturn null;
\t}

\t// \u2500 [\ub3c4\uc801] 2\ud0c0 \uc0ac\uc804 \uacc4\uc0b0 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate void ma_thiefDoubleAtkPreCalc(AttackSession s) {
\t\tif (!"\ub3c4\uc801".equals(s.job) || s.m.monNo > 50) return;
\t\tdouble dRate = 0.30 + ThreadLocalRandom.current().nextDouble() * 0.20;
\t\ts.thiefDoubleAtk = ThreadLocalRandom.current().nextDouble() < dRate;
\t\tif (s.thiefDoubleAtk) {
\t\t\tFlags f2 = rollFlags(s.u, s.m);
\t\t\ts.dmg2  = calculateDamage(s.u, s.m, f2, s.effAtkMin, s.effAtkMax, s.critRate, s.critDmg,
\t\t\t\t\ts.berserkMul, s.monHpRemainBefore, s.hpMax, s.beforeJobSkillYn, s.nightmare);
\t\t\ts.calc2 = s.dmg2.calc;
\t\t}
\t}

\t// \u2500 12) \uc0ac\ub9dd \ucc98\ub9ac \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_deathCheck(AttackSession s) {
\t\tint newHpPreview = Math.max(0, s.u.hpCur - s.calc.monDmg);
\t\tif (newHpPreview > 0) return null;

\t\tint dealtThisTurn  = Math.max(0, s.calc.atkDmg);
\t\tint monRemainAfter = Math.max(0, s.monHpRemainBefore - dealtThisTurn);

\t\tbotNewService.closeOngoingBattleTx(s.userName, s.roomName);
\t\tbotNewService.updateUserHpOnlyTx(s.userName, s.roomName, 0);
\t\tbotNewService.insertBattleLogTx(new BattleLog()
\t\t\t\t.setUserName(s.userName).setRoomName(s.roomName).setLv(s.u.lv)
\t\t\t\t.setTargetMonLv(s.m.monNo).setGainExp(0)
\t\t\t\t.setAtkDmg(s.calc.atkDmg).setMonDmg(s.calc.monDmg)
\t\t\t\t.setAtkCritYn(s.flags.atkCrit ? 1 : 0).setMonPatten(s.flags.monPattern)
\t\t\t\t.setKillYn(0).setNowYn(0).setDropYn(0).setDeathYn(1).setLuckyYn(0)
\t\t\t\t.setJobSkillYn(0).setJob(s.job).setNightmareYn(s.ctx.user.nightmareYn));

\t\tString deathAchvMsg = grantDeathAchievements(s.userName, s.roomName);
\t\treturn s.userName + "\ub2d8, \uc774\ubc88\uc804\ud22c\uc5d0\uc11c \ud328\ubc30\ud558\uc5ec, \uc804\ud22c \ubd88\ub2a5\uc774 \ub418\uc5c8\uc2b5\ub2c8\ub2e4." + NL
\t\t\t\t+ s.calc.monDmg + " \ud53c\ud574\ub85c \uc0ac\ub9dd!" + NL
\t\t\t\t+ "\u25b6 \uc774\ubc88\uc5d0 \uc900 \ud53c\ud574: " + dealtThisTurn + NL
\t\t\t\t+ "\u25b6 \ubaac\uc2a4\ud130 \ub0a8\uc740 \uccb4\ub825: " + monRemainAfter + " / " + s.monMaxHp + NL
\t\t\t\t+ "\ud604\uc7ac \uccb4\ub825: 0 / " + s.hpMax + NL
\t\t\t\t+ "5\ubd84 \ub4a4 \ucd5c\ub300 \uccb4\ub825\uc758 10%\ub85c \ubd80\ud65c\ud558\uba70," + NL
\t\t\t\t+ "\uc774\ud6c4 5\ubd84\ub9c8\ub2e4 HP_REGEN \ub9cc\ud07c \uc11c\uc11c\ud788 \ud68c\ubcf5\ub429\ub2c8\ub2e4." + NL
\t\t\t\t+ deathAchvMsg;
\t}

\t// \u2500 13) \ucc98\uce58\xb7\ub4dc\ub78d \ud310\ub2e8 + \uc9c1\uc5c5\ubcc4 \uc2a4\ud0ac \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate void ma_resolveKillAndJobSkills(AttackSession s) {
\t\ts.res = resolveKillAndDrop(s.m, s.calc, s.willKill, s.u, s.lucky, s.dark, s.gray, s.ctx.user.nightmareYn);
\t\tif ("\uad81\uc218".equals(s.u.job) || "\uc0ac\ub099\uaf3c".equals(s.u.job)) s.res.gainExp *= 3;

\t\t// \u2014\u2014 \ub3c4\uc801: \ub354\ube14\uc5b4\ud0dd + \uc2a4\ud2f8 \u2014\u2014
\t\tif ("\ub3c4\uc801".equals(s.job) && !(s.m.monNo > 50)) {
\t\t\tif (ThreadLocalRandom.current().nextDouble() < 0.50) {
\t\t\t\tString dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
\t\t\t\tif (!dn.isEmpty()) try {
\t\t\t\t\tInteger id = getItemIdCached(dn);
\t\t\t\t\tif (id != null) {
\t\t\t\t\t\tbotNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
\t\t\t\t\t\ts.stealMsg += "\u2728 [1\ud0c0] " + s.m.monName + "\uc758 \uc544\uc774\ud15c\uc744 \ud6d4\ucce4\uc2b5\ub2c8\ub2e4! (" + dn + "\uc870\uac01)";
\t\t\t\t\t\ts.calc.jobSkillUsed = true;
\t\t\t\t\t}
\t\t\t\t\tString[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b); s.stealBonus += b[0];
\t\t\t\t} catch (Exception ignore) {}
\t\t\t}
\t\t\tif (s.thiefDoubleAtk && s.calc2 != null && ThreadLocalRandom.current().nextDouble() < 0.50) {
\t\t\t\tString dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
\t\t\t\tif (!dn.isEmpty()) try {
\t\t\t\t\tInteger id = getItemIdCached(dn);
\t\t\t\t\tif (id != null) {
\t\t\t\t\t\tbotNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
\t\t\t\t\t\ts.stealMsg += (s.stealMsg.isEmpty() ? "" : NL) + "\u2728 [2\ud0c0] " + s.m.monName + "\uc758 \uc544\uc774\ud15c\uc744 \ud6d4\ucce4\uc2b5\ub2c8\ub2e4! (" + dn + "\uc870\uac01)";
\t\t\t\t\t\ts.calc2.jobSkillUsed = true;
\t\t\t\t\t}
\t\t\t\t\tString[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b); s.stealBonus += b[0];
\t\t\t\t} catch (Exception ignore) {}
\t\t\t}
\t\t}

\t\t// \u2014\u2014 \ucc98\ub2e8\uc790: \ucd94\uac00 \ub4dc\ub78d \u2014\u2014
\t\tif ("\ucc98\ub2e8\uc790".equals(s.job) && !(s.m.monNo > 50) && s.willKill) {
\t\t\tint monHp = s.m.monHp * (s.nightmare ? NM_MUL_HP_ATK : 1);
\t\t\tint extra = Math.min((s.calc.atkDmg / monHp) - 1, 5);
\t\t\tString dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
\t\t\tif (!dn.isEmpty()) try {
\t\t\t\tInteger id = getItemIdCached(dn);
\t\t\t\tint qty = 2 + extra;
\t\t\t\tif (id != null) {
\t\t\t\t\tboolean bonus = ThreadLocalRandom.current().nextDouble() < 0.10;
\t\t\t\t\tif (bonus) qty *= 2;
\t\t\t\t\tHashMap<String,Object> inv = buildStealInv(s.userName, s.roomName, id);
\t\t\t\t\tinv.put("qty", qty);
\t\t\t\t\tbotNewService.insertInventoryLogTx(inv);
\t\t\t\t\ts.stealMsg = "\u2728 \ub0a0\uce74\ub85c\uc6b4 \ucc98\ub2e8\uc73c\ub85c \ucd94\uac00\ud68d\ub4dd (+" + dn + "\uc870\uac01" + qty + ")" + (bonus ? "\u2728 \ubcf4\ub108\uc2a4!" : "");
\t\t\t\t\ts.calc.jobSkillUsed = true;
\t\t\t\t}
\t\t\t\tString[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", qty, s.nightmare, b); s.stealBonus += b[0];
\t\t\t} catch (Exception ignore) {}
\t\t}

\t\t// \u2014\u2014 \uc6a9\uc0ac: \uc2a4\ud2f8 \u2014\u2014
\t\tif ("\uc6a9\uc0ac".equals(s.job) && !(s.m.monNo > 50) && ThreadLocalRandom.current().nextDouble() < 0.60) {
\t\t\tString dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
\t\t\tif (!dn.isEmpty()) try {
\t\t\t\tInteger id = getItemIdCached(dn);
\t\t\t\tif (id != null) {
\t\t\t\t\tbotNewService.insertInventoryLogTx(buildStealInv(s.userName, s.roomName, id));
\t\t\t\t\ts.stealMsg += ThreadLocalRandom.current().nextDouble() < 0.5
\t\t\t\t\t\t? "\u2728 " + s.m.monName + "\uc640  \uc2f8\uc6b0\ub358 \ub9c8\uc744\uc8fc\ubbfc\uc5d0\uac8c\uc11c \uc57d\ud0c8\ud588\ub2e4! (" + dn + "\uc870\uac01)"
\t\t\t\t\t\t: "\u2728 \ucd08\uc7a5 \uc9d1\uc5d0\uc11c " + s.m.monName + "\uc758 \uc544\uc774\ud15c\uc744 \ubc1c\uacac\ud588\ub2e4! (" + dn + "\uc870\uac01)";
\t\t\t\t\ts.calc.jobSkillUsed = true;
\t\t\t\t}
\t\t\t\tString[] b={""}; s.stealPoint += " +" + baroSellItem(dn, id, s.res, s.userName, s.roomName, s.ctx, s.u, "STEAL", 1, s.nightmare, b); s.stealBonus += b[0];
\t\t\t} catch (Exception ignore) {}
\t\t}

\t\t// \u2014\u2014 \uc74c\uc591\uc0ac: \uae30\uc6d0 \uba54\uc2dc\uc9c0 \u2014\u2014
\t\tif ("\uc74c\uc591\uc0ac".equals(s.job)) s.dosaCastMsg = "\u2728" + s.job + "\uc758 \uae30\uc6d0! \ub2e4\uc74c \uacf5\uaca9\uc790 \uac15\ud654!";

\t\t// DROP SP
\t\tif (s.res.killed && !"0".equals(s.res.dropCode)) {
\t\t\tString dn = (s.m.monDrop == null ? "" : s.m.monDrop.trim());
\t\t\tif (!dn.isEmpty()) {
\t\t\t\tString[] nb={""}; s.newPoint += " +" + baroSellItem(dn, 0, s.res, s.userName, s.roomName, s.ctx, s.u, "DROP", 1, s.nightmare, nb); s.newBonus += nb[0];
\t\t\t}
\t\t}

\t\t// \uacbd\ud5d8\uce58 \uc2a4\ud398\uc140\ubc84\ud504
\t\tif (s.activeBuff != null && "\uacbd\ud5d8\uce58".equals(s.activeBuff.get("FLAG_CODE"))) {
\t\t\tdouble pct = Double.parseDouble(s.activeBuff.get("EFFECT_VALUE").toString());
\t\t\ts.res.gainExp = (int)(s.res.gainExp * (1 + pct / 100.0));
\t\t}
\t}

\t/** buildStealInv \ud5ec\ud37c */
\tprivate HashMap<String,Object> buildStealInv(String userName, String roomName, Integer itemId) {
\t\tHashMap<String,Object> inv = new HashMap<>();
\t\tinv.put("userName", userName); inv.put("roomName", roomName);
\t\tinv.put("itemId", itemId);     inv.put("qty", 1);
\t\tinv.put("delYn", "1");         inv.put("gainType", "STEAL");
\t\treturn inv;
\t}

\t// \u2500 14) DB \ubc18\uc601 + \uc5c5\uc801 \ubd80\uc5ec \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate void ma_persistAndAchv(AttackSession s) {
\t\ts.buffStart = s.buff.started ? 1 : 0;
\t\ts.buffIng   = s.activeBuff != null ? 1 : 0;
\t\ts.buffCode  = s.activeBuff != null ? (String) s.activeBuff.get("FLAG_CODE") : null;
\t\ts.up = persist(s.userName, s.roomName, s.u, s.m, s.flags, s.calc, s.res, s.hpMax, s.nightmare, s.buffStart, s.buffIng, s.buffCode);

\t\t// [\ub3c4\uc801] 2\ud0c0 \ubc30\ud2c0\ub85c\uadf8 (PK \ucda9\ub3cc \ubc29\uc9c0: shotIndex=1)
\t\tif (s.thiefDoubleAtk && s.calc2 != null && s.m != null) {
\t\t\ttry {
\t\t\t\tbotNewService.insertBattleLogTx(new BattleLog()
\t\t\t\t\t\t.setUserName(s.userName).setRoomName(s.roomName).setLv(s.up.beforeLv)
\t\t\t\t\t\t.setTargetMonLv(s.m.monNo).setGainExp(0)
\t\t\t\t\t\t.setAtkDmg(s.calc2.atkDmg).setMonDmg(0)
\t\t\t\t\t\t.setAtkCritYn(s.dmg2.flags != null && s.dmg2.flags.atkCrit ? 1 : 0)
\t\t\t\t\t\t.setMonPatten(0).setKillYn(0).setNowYn(1).setDropYn(0)
\t\t\t\t\t\t.setDeathYn(0).setLuckyYn(0)
\t\t\t\t\t\t.setJobSkillYn(s.calc2.jobSkillUsed ? 1 : 0).setJob(s.job)
\t\t\t\t\t\t.setNightmareYn(s.ctx.user.nightmareYn)
\t\t\t\t\t\t.setSpecialBuffStart(0).setSpecialBuffIng(s.buffIng)
\t\t\t\t\t\t.setSpecialBuffCode(s.buffCode).setShotIndex(1));
\t\t\t} catch (Exception ignore) {}
\t\t}

\t\tif (s.res.killed) {
\t\t\tbotNewService.closeOngoingBattleTx(s.userName, s.roomName);
\t\t\tHashMap<String,Object> achvInvCounts = null;
\t\t\ttry { achvInvCounts = botNewService.selectAchievementInventoryCounts(s.userName); } catch (Exception ignore) {}
\t\t\tList<HashMap<String,Object>> achvGainRows = buildGainRowsFromCounts(achvInvCounts);
\t\t\tint achvBagTotal    = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("BAG_COUNT",    0)).intValue() : 0;
\t\t\tint achvSoldCount   = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("SOLD_COUNT",   0)).intValue() : 0;
\t\t\tint achvPotionCount = achvInvCounts != null ? ((Number)achvInvCounts.getOrDefault("POTION_COUNT", 0)).intValue() : 0;
\t\t\tMiniGameUtil.POTION_USE_CACHE.put(s.userName, achvPotionCount);

\t\t\tList<HashMap<String,Object>> achvJobSkillRows = null;
\t\t\ttry { achvJobSkillRows = botNewService.selectJobSkillUseCountAllJobs(s.userName, s.roomName); } catch (Exception ignore) {}
\t\t\tList<HashMap<String,Object>> achvBuffRows = null;
\t\t\ttry { achvBuffRows = botNewService.selectSpecialBuffAchvStats(s.userName); } catch (Exception ignore) {}

\t\t\tString killAchvMsg     = grantKillAchievements(s.userName, s.roomName, s.achievedCmdSet, s.cachedKillStats);
\t\t\tString itemAchvMsg     = grantLightDarkItemAchievements(s.userName, s.roomName, s.achievedCmdSet, achvGainRows);
\t\t\tString bagAchvMsg      = grantBagAcquireAchievementsFast(s.userName, s.roomName, s.achievedCmdSet, achvBagTotal);
\t\t\tString attackAchvMsg   = grantAttackCountAchievements(s.userName, s.roomName, s.achievedCmdSet, s.cachedAds);
\t\t\tString jobSkillAchvMsg = grantJobSkillUseAchievementsAllJobs(s.userName, s.roomName, s.achievedCmdSet, achvJobSkillRows);
\t\t\tString shopSellAchvMsg = grantShopSellAchievementsFast(s.userName, s.roomName, s.achievedCmdSet, achvSoldCount);
\t\t\tString potionAchvMsg   = grantPotionUseAchievements(s.userName, s.roomName, s.achievedCmdSet, achvPotionCount);
\t\t\tString buffAchvMsg     = grantSpecialBuffAchievements(s.userName, s.roomName, s.achievedCmdSet, achvBuffRows);
\t\t\tString achvRewardMsg   = grantAchievementBasedReward(s.userName, s.roomName, s.userAchvList);
\t\t\tString specialAchvMsg  = grantSpecialHistoricalAchievements(s.userName, s.roomName);

\t\t\tif (isAnyNonEmpty(killAchvMsg, itemAchvMsg, attackAchvMsg, jobSkillAchvMsg, shopSellAchvMsg, potionAchvMsg, achvRewardMsg, bagAchvMsg, specialAchvMsg, buffAchvMsg)) {
\t\t\t\ts.bonusMsg = NL + killAchvMsg + itemAchvMsg + attackAchvMsg + jobSkillAchvMsg
\t\t\t\t\t\t + shopSellAchvMsg + potionAchvMsg + achvRewardMsg + bagAchvMsg + specialAchvMsg + buffAchvMsg;
\t\t\t}
\t\t}

\t\ts.bagDropMsg = tryDropBag(s.userName, s.roomName, s.m, s.nightmare, s.buff);
\t\tif (s.thiefDoubleAtk && s.m != null) {
\t\t\tString bag2 = tryDropBag(s.userName, s.roomName, s.m, s.nightmare, s.buff);
\t\t\tif (bag2 != null && !bag2.isEmpty())
\t\t\t\ts.bagDropMsg = (s.bagDropMsg == null || s.bagDropMsg.isEmpty()) ? bag2 : s.bagDropMsg + NL + bag2;
\t\t}
\t}

\t// \u2500 15~16) \uba54\uc2dc\uc9c0 \uad6c\uc131 + \ud3ec\uc778\ud2b8 \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
\tprivate String ma_buildMessage(AttackSession s) {
\t\tStringBuilder mid    = new StringBuilder();
\t\tStringBuilder hunter = new StringBuilder();
\t\tStringBuilder bot    = new StringBuilder();

\t\tif (s.dmg.dmgCalcMsg != null && !s.dmg.dmgCalcMsg.isEmpty()) mid.append(s.dmg.dmgCalcMsg);
\t\tif (s.dmg.hunterMsg  != null && !s.dmg.hunterMsg.isEmpty())  hunter.append(s.dmg.hunterMsg);
\t\tif (s.dosabuffMsg    != null && !s.dosabuffMsg.isEmpty())     mid.append(NL).append(s.dosabuffMsg);
\t\tif (s.dosaCastMsg    != null && !s.dosaCastMsg.isEmpty())     bot.append(NL).append(s.dosaCastMsg);
\t\tif (s.thiefDoubleAtk && s.calc2 != null) {
\t\t\tbot.append(NL).append("\u2694\ufe0f2\ud0c0 \ub370\ubbf8\uc9c0: ").append(formatWan(s.calc2.atkDmg));
\t\t\tif (s.dmg2 != null && s.dmg2.flags != null && s.dmg2.flags.atkCrit) bot.append(" \u2728\ud06c\ub9ac!");
\t\t}
\t\tif (s.stealMsg != null && !s.stealMsg.isEmpty()) bot.append(NL).append(s.stealMsg);

\t\tString msg = buildAttackMessage(s.userName, s.u, s.m, s.flags, s.calc, s.res, s.up,
\t\t\t\ts.monHpRemainBefore, s.monMaxHp, s.effAtkMin, s.effAtkMax, s.hpMax,
\t\t\t\tmid.toString(), hunter.toString(), bot.toString(), s.nightmare, s.ctx);

\t\tif (!s.bonusMsg.isEmpty()) msg += s.bonusMsg;

\t\tString curSpStr = "";
\t\ttry {
\t\t\tHashMap<String,Object> pointRow = botNewService.selectCurrentPoint(s.userName, s.roomName);
\t\t\tdouble cv = Double.parseDouble(Objects.toString(pointRow.get("SCORE"), "0"));
\t\t\tString  ce = Objects.toString(pointRow.get("SCORE_EXT"), "");
\t\t\tcurSpStr = new SP(cv, ce).toString();
\t\t} catch (Exception ignore) {}

\t\tif (!s.stealPoint.isEmpty()) { msg += "\u2728\ucd94\uac00\ud68d\ub4dd" + s.stealPoint; if (!s.stealBonus.isEmpty()) msg += s.stealBonus; msg += NL; }
\t\tif (!s.newPoint.isEmpty())   { msg += "\u2728\uc804\ud22c\ud68d\ub4dd" + s.newPoint;   if (!s.newBonus.isEmpty())   msg += s.newBonus;   msg += NL; }
\t\tmsg += "\u2728\ud3ec\uc778\ud2b8: " + curSpStr;

\t\tif (s.bagDropMsg != null && !s.bagDropMsg.isEmpty()) msg += NL + s.bagDropMsg;
\t\tif (s.buff.started)                msg += NL + s.buff.startMsg;
\t\telse if (s.buff.runningMsg != null) msg += NL + s.buff.runningMsg;

\t\ttry {
\t\t\tbotNewService.execSPMsgTest(s.map);
\t\t\tmsg += NL + Objects.toString(s.map.get("outMsg"), "");
\t\t} catch (Exception e) { e.printStackTrace(); }

\t\treturn msg;
\t}

\t/** isAnyNonEmpty \ud5ec\ud37c */
\tprivate static boolean isAnyNonEmpty(String... strs) {
\t\tfor (String s : strs) if (s != null && !s.isEmpty()) return true;
\t\treturn false;
\t}`;

// LF → CRLF
const newCodeCRLF = newCode.replace(/\n/g, '\r\n');

// 기존 메서드 영역 교체
const before = content.slice(0, METHOD_START);
const after  = content.slice(METHOD_END);
const result = before + newCodeCRLF + after;

fs.writeFileSync(file, result, 'utf8');
console.log('Done! New length:', result.length, '(was', content.length, ')');
console.log('Method replaced: chars', METHOD_START, '->', METHOD_END, '(', METHOD_END - METHOD_START, 'removed,', newCodeCRLF.length, 'inserted)');
