package my.prac.api.loa.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import my.prac.core.game.dto.User;
import my.prac.core.game.dto.UserBattleContext;
import my.prac.core.prjbot.service.BotNewService;
import my.prac.core.prjbot.service.BotS3Service;
import my.prac.core.util.MiniGameUtil;
import my.prac.core.util.SP;

/**
 * [시즌3] 헬모드 전용 보스 컨트롤러
 *
 * 진입 경로:
 *   A) BossAttackController.monsterAttack → targetMon==99 분기 → attackBossS3(map, ctx)
 *      - 쿨타임/HP확정은 monsterAttack 에서 이미 처리됨
 *   B) 직접 호출 → attackBossS3(map)
 *      - 유저 조회, 헬모드 체크, 쿨타임 포함 전체 처리
 *
 * 보스 테이블: TBOT_POINT_NEW_BOSS (전역 공유, ROOM_NAME 없음)
 * 공격 로그  : TBOT_POINT_NEW_BATTLE_LOG (NIGHTMARE_YN='2', TARGET_MON_LV=999)
 * 보상       : 7000번대 미소지자 중 기여도(횟수70%+데미지30%) 가중 랜덤 1명에게 1개 지급
 */
@Controller
public class BossAttackS3Controller {

    private static final String NL          = "♬";
    private static final String ALL_SEE_STR = "===";

    // =========================================================
    // 헬보스 능력치 랜덤 범위 설정 (필요 시 수정)
    // =========================================================
    /** 보스 공격 발동률 (%) */
    static final int BOSS_ATK_RATE_MIN   = 15,  BOSS_ATK_RATE_MAX   = 30;
    /** 보스 공격 데미지: 유저 최대HP의 X% (체력비례 퍼센트 데미지) */
    static final int BOSS_ATK_POWER_MIN  = 10,  BOSS_ATK_POWER_MAX  = 99;
    /** 보스 방어 발동률 (%) */
    static final int BOSS_DEF_RATE_MIN   = 15,  BOSS_DEF_RATE_MAX   = 35;
    /** 보스 방어: 유저 공격의 X% 감소 */
    static final int BOSS_DEF_POWER_MIN  = 15,  BOSS_DEF_POWER_MAX  = 100;
    /** 보스 회피율 (%) */
    static final int BOSS_EVADE_RATE_MIN = 10,  BOSS_EVADE_RATE_MAX = 30;
    /** 보스 치명 저항 (%) */
    static final int BOSS_CRIT_DEF_MIN   = 10,  BOSS_CRIT_DEF_MAX   = 30;
    /** 보스 최대 HP (raw) — 100a~300a 범위 (1a = 10,000 raw) */
    static final long BOSS_MAX_HP_MIN    = 1_000_000L; // 100a
    static final long BOSS_MAX_HP_MAX    = 3_000_000L; // 300a

    /** 헬보스 보상 아이템 타입 */
    private static final String HELL_ITEM_TYPE = "BOSS_HELL";

    // ── 대악마 감금스킬 ──
    private static final Map<String, Long> IMPRISONED_UNTIL   = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int IMPRISON_DURATION_MS = 5 * 60 * 1000; // 5분
    private static final int IMPRISON_CHANCE_PCT  = 10;             // 10%
    /** 보스아이템 최대 강화 단계 (기본1 + 강화1 = QTY 2) */
    public static final int MAX_BOSS_ENHANCE = 2;

    // =========================================================
    // ★ 보스 아이템 강화 효과 테이블 (수치 직접 수정 가능)
    //   배열: [기본효과, +1강화, +2강화, ...]  (인덱스 = qty - 1)
    //   qty=1 → [0], qty=2 → [1], qty=3 → [2]
    //   값의 의미는 아이템별 주석 참고
    // =========================================================
    static final java.util.Map<Integer, int[]> BOSS_ENHANCE_TABLE;
    static {
        java.util.Map<Integer, int[]> m = new java.util.LinkedHashMap<>();
        // 7001: 천벌 — debuff 배수(×10회): +0=×1(10회), +1=×2(20회)
        m.put(7001, new int[]{ 1,  2 });
        // 7002: 예리한칼날 — 도적 더블어택 확률(%)
        m.put(7002, new int[]{ 35, 45, 55 });
        // 7003: 주작궁 — 궁사 연사 추가(회)
        m.put(7003, new int[]{ 1,  2,  3  });
        // 7004: 모래시계 — 쿨타임 감소율(%)
        m.put(7004, new int[]{ 20, 30 });
        // 7005: 가시갑옷 — 반사율(퍼밀 /1000): 10=1.0%, 15=1.5%
        m.put(7005, new int[]{ 10, 15 });
        // 7006: 강한자의어금니 — 바람가르기 추가 확률(%p)
        m.put(7006, new int[]{ 15, 30 });
        // 7007: 헌터의자격 — 잔존율 추가(×10 저장: 30=3.0%p, 35=3.5%p)
        m.put(7007, new int[]{ 30, 35 });
        // 7008: 가난한자의부적 — 추가 드랍 수
        m.put(7008, new int[]{ 1,  2,  3  });
        // 7009: 진화의시대 — 레벨당 공격력(0강화:150/cap300, 1강화:200/cap500)
        m.put(7009, new int[]{ 150, 200 });
        // 7010: 주시자의눈 — 회피저지 확률(%)
        m.put(7010, new int[]{ 30, 60 });
        // 7011: 개척자 — 숨기무시 확률(%)
        m.put(7011, new int[]{ 30, 60 });
        // 7012: 수선도사의머리띠 — 도사 버프 계수(배수)
        m.put(7012, new int[]{ 3,  4,  5  });
        // 7013: 과거의영광 — 어제 공격자수당 공격력 보너스
        m.put(7013, new int[]{ 1000, 1600 });
        // 7014: 달의부름 — 곰 스킬실패 패널티 완화 확률(%)
        m.put(7014, new int[]{ 50, 65, 80 });
        // 7015: 무한의대검 — 초강력치명타 추가 확률(%p)
        m.put(7015, new int[]{ 10, 15, 20 });
        // 7016: 복수의시간 — 복수자 처치 시 체력회복률(%)
        m.put(7016, new int[]{ 20, 30 });
        // 7017: 연금술의대가 — 엘릭서 할인율(%)
        m.put(7017, new int[]{ 50, 70 });
        // 7018: 상자수집가 — 추가 상자 수(0강화:+1, 1강화:+2)
        m.put(7018, new int[]{ 1,  2  });
        // 7019: 어세신의부름 — 도적 스틸 성공 시 추가 획득 수
        m.put(7019, new int[]{ 1,  2,  3  });
        BOSS_ENHANCE_TABLE = java.util.Collections.unmodifiableMap(m);
    }

    // =========================================================
    // 보스 아이템 효과 라벨 (상점/설명 표시용)
    // {라벨, 단위, 특수메모(null 가능)}  ← boss-item-reference.md 참조
    // =========================================================
    private static final java.util.Map<Integer, String[]> BOSS_ITEM_EFFECT;
    static {
        java.util.Map<Integer, String[]> e = new java.util.HashMap<>();
        e.put(7001, new String[]{"천벌 발동 횟수",        "회",  "디버프 활성시 발동 불가"});
        e.put(7002, new String[]{"도적 2타 확률",          "%",   null});
        e.put(7003, new String[]{"궁사 연사 추가",         "발",  "최대 7연사"});
        e.put(7004, new String[]{"아이템 쿨타임 감소",     "%",   null});
        e.put(7005, new String[]{"받은 피해 반사",         "",    "0강화 1.0% / 1강화 1.5%"});
        e.put(7006, new String[]{"바람가르기 확률",        "%",   "검성 전용"});
        e.put(7007, new String[]{"헬너프 감소",            "",    "0강화 3.0%p / 1강화 3.5%p"});
        e.put(7008, new String[]{"일반몬스터 드랍 추가",   "개",  null});
        e.put(7009, new String[]{"레벨당 공격력 추가",     "",    "0강화 max300 / 1강화 max500"});
        e.put(7010, new String[]{"보스 회피 무시 확률",    "%",   "헬보스 전용"});
        e.put(7011, new String[]{"보스 은신 무시 확률",    "%",   "헬보스 전용"});
        e.put(7012, new String[]{"직업 버프 계수",         "배",  "도사/음양사 전용"});
        e.put(7013, new String[]{"어제공격자수 x 공격력",  "",    "최대 40명 / 0강max40,000 / 1강max64,000"});
        e.put(7014, new String[]{"곰 스킬실패 패널티 완화", "%",  "곰 전용"});
        e.put(7015, new String[]{"슈퍼크리티컬 확률",      "%",   "헬보스 전용"});
        e.put(7016, new String[]{"HP 흡수율",              "%",   "미보유시 기본 10%"});
        e.put(7017, new String[]{"상점 할인율",            "%",   null});
        e.put(7018, new String[]{"출석 상자 추가",         "개",  null});
        e.put(7019, new String[]{"저주 스택 추가",         "개",  null});
        e.put(7020, new String[]{"슈퍼크리티컬 배율",      "배",  "미보유 5배 / 0강화 6배 / 1강화 6.5배"});
        e.put(7021, new String[]{"아이템 수량 2배 확률",   "%",   "미보유 10% / 0강화 20% / 1강화 25%"});
        BOSS_ITEM_EFFECT = java.util.Collections.unmodifiableMap(e);
    }

        /** 보스 아이템 강화 옵션 한 줄 설명 반환 (상점 표시용) */
    public static String getBossItemEnhanceDesc(int itemId) {
        int[] vals = BOSS_ENHANCE_TABLE.get(itemId);
        String[] eff = BOSS_ITEM_EFFECT.get(itemId);
        if (eff == null) return "";
        String label = eff[0], unit = eff[1], memo = eff[2];
        // BOSS_ENHANCE_TABLE 없는 아이템은 memo로만 설명
        if (vals == null) return memo != null ? label + ": " + memo : label;
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(": ").append(vals[0]).append(unit).append("(0강화)");
        if (vals.length >= 2) sb.append(" → ").append(vals[1]).append(unit).append("(+1강화)");
        if (memo != null) sb.append(" [").append(memo).append("]");
        return sb.toString();
    }

    /** 아이템 강화 효과값 반환 (qty 기반) */
    static int getBossEnhanceVal(int itemId, int qty) {
        int[] table = BOSS_ENHANCE_TABLE.get(itemId);
        if (table == null || qty < 1) return 0;
        int level = Math.min(qty - 1, table.length - 1);
        return table[level];
    }

    /** 아이템 강화 등급 표시 문자열 (qty=1→, qty=2→+1, qty=3→+2) */
    static String enhanceSuffix(int qty) {
        return qty >= 2 ? "+" + (qty - 1) : "";
    }

    /** 헬보스 보상 아이템 목록 (BOSS_HELL 타입 기반 동적 로드, 캐시) */
    private volatile List<Integer> hellRewardItemsCache = null;

    private List<Integer> getHellRewardItems() {
        if (hellRewardItemsCache == null || hellRewardItemsCache.isEmpty()) {
            try {
                List<Integer> ids = botNewService.selectItemIdsByType(HELL_ITEM_TYPE);
                if (ids != null && !ids.isEmpty()) hellRewardItemsCache = ids;
            } catch (Exception ignore) {}
        }
        return hellRewardItemsCache != null ? hellRewardItemsCache : Collections.emptyList();
    }

    /* ===== DI ===== */
    @Autowired BossAttackController bossAttackController;
    @Resource(name = "core.prjbot.BotNewService") BotNewService botNewService;
    @Resource(name = "core.prjbot.BotS3Service")  BotS3Service  botS3Service;


    // =========================================================
    // 진입점 A: BossAttackController.monsterAttack 에서 분기
    //   - 쿨타임/HP확정은 monsterAttack 에서 완료된 상태
    // =========================================================
    String attackBossS3(HashMap<String, Object> map, UserBattleContext ctx) {
        User user = ctx.user;
        if (user.nightmareYn != 2) {
            return user.userName + "님," + NL + "헬모드 유저만 도전 가능한 보스입니다.";
        }
        return doBossAttack(map, ctx);
    }


    // =========================================================
    // 진입점 B: 직접 호출 (유저 조회 + 쿨타임 포함)
    // =========================================================
    public String attackBossS3(HashMap<String, Object> map) {
        final String roomName = Objects.toString(map.get("roomName"), "");
        final String userName = Objects.toString(map.get("userName"), "");

        // 1. 유저 조회
        User user;
        try {
            user = botNewService.selectUser(userName, roomName);
        } catch (Exception e) {
            return userName + "님, 유저 정보 조회에 실패했습니다.";
        }
        if (user == null) return userName + "님, 게임 가입이 필요합니다.";

        // 2. 데스 상태 체크
        if (user.hpCur <= 0) {
            return userName + "님, 데스 상태입니다. 체력을 회복 후 공격 가능합니다.";
        }

        // 3. 헬모드 체크
        if (user.nightmareYn != 2) {
            return userName + "님," + NL + "헬모드 유저만 도전 가능한 보스입니다.";
        }

        // 3. 유저 전투 컨텍스트 (S2 calcUserBattleContext 재사용)
        map.put("param0", "/ㄱ");
        map.put("param1", "");
        UserBattleContext ctx = bossAttackController.calcUserBattleContext(map);
        if (!ctx.success) return userName + "님, " + ctx.errorMessage;

        return doBossAttack(map, ctx);
    }


    // =========================================================
    // 핵심 보스 공격 로직 (쿨타임/유저 검증 완료 후 호출)
    // =========================================================
    private String doBossAttack(HashMap<String, Object> map, UserBattleContext ctx) {
        final String roomName = Objects.toString(map.get("roomName"), "");
        final String userName = Objects.toString(map.get("userName"), "");
        final User   user     = ctx.user;

        // 데스 상태 체크 (진입점 A: monsterAttack 경유 시)
        if (user.hpCur <= 0) {
            return userName + "님, 데스 상태입니다. 체력을 회복 후 공격 가능합니다.";
        }

        // 대악마/마왕 감금 상태 체크
        Long imprisonUntil = IMPRISONED_UNTIL.get(userName);
        if (imprisonUntil != null) {
            if (System.currentTimeMillis() < imprisonUntil) {
                long remainSec = (imprisonUntil - System.currentTimeMillis()) / 1000;
                long remMin = remainSec / 60, remSec = remainSec % 60;
                return userName + "님, [감금스킬] 공격 불가 상태입니다. (" + remMin + "분 " + remSec + "초 남음)";
            }
            IMPRISONED_UNTIL.remove(userName);
        }

        // 보스 정보 조회 (전역, ROOM_NAME 없음)
        HashMap<String, Object> boss;
        long hp, maxHp;
        // try 블록 밖에서 사용해야 하므로 미리 선언
        double curHpNum = 0;
        String curHpExt = "";
        SP curHpSp = null;
        int seq;
        int bossAtkRate, bossAtkPower, bossDefRate, bossDefPower, bossEvadeRate, critDefRate;
        int debuff, debuff1;
        String bossStartDate;
        String hideRule;
        String bossRewardType;
        String bossDemonType = "상급악마";
        try {
            boss = botS3Service.selectHellBoss();
            if (boss == null || boss.get("CUR_HP") == null) {
                String lastReward = botS3Service.getLastKillRewardMsg();
                if (lastReward != null && !lastReward.isEmpty()) {
                    return "현재 출현한 상급악마가 없습니다." + NL + NL + lastReward;
                }
                return "현재 출현한 상급악마가 없습니다.";
            }
            // HP: CUR_HP(소수 가능) + CUR_HP_EXT 조합 → SP로 raw 계산
            curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
            curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
            curHpSp  = SP.of(curHpNum, curHpExt);
            hp       = SP.toBaseValue(curHpSp);

            double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
            String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
            maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));

            seq           = Integer.parseInt(boss.get("SEQ").toString());
            bossAtkRate   = boss.get("ATK_RATE")     != null ? Integer.parseInt(boss.get("ATK_RATE").toString())     : 20;
            bossAtkPower  = boss.get("ATK_POWER")    != null ? Integer.parseInt(boss.get("ATK_POWER").toString())    : 100;
            bossDefRate   = boss.get("DEF_RATE")     != null ? Integer.parseInt(boss.get("DEF_RATE").toString())     : 20;
            bossDefPower  = boss.get("DEF_POWER")    != null ? Integer.parseInt(boss.get("DEF_POWER").toString())    : 100;
            bossEvadeRate = boss.get("EVADE_RATE")   != null ? Integer.parseInt(boss.get("EVADE_RATE").toString())   : 10;
            critDefRate   = boss.get("CRIT_DEF_RATE")!= null ? Integer.parseInt(boss.get("CRIT_DEF_RATE").toString()): 0;
            debuff        = boss.get("DEBUFF")       != null ? Integer.parseInt(boss.get("DEBUFF").toString())       : 0;
            debuff1       = boss.get("DEBUFF1")      != null ? Integer.parseInt(boss.get("DEBUFF1").toString())      : 0;
            bossStartDate  = boss.get("START_DATE")   != null ? boss.get("START_DATE").toString()  : "";
            hideRule       = boss.get("HIDE_RULE")    != null ? boss.get("HIDE_RULE").toString()    : "0";
            bossRewardType = boss.get("REWARD_TYPE") != null ? boss.get("REWARD_TYPE").toString() : "";
            bossDemonType  = boss.get("BOSS_TYPE")   != null ? boss.get("BOSS_TYPE").toString()   : "상급악마";
        } catch (Exception e) {
            return "보스 정보를 가져오는데 실패했습니다.";
        }


        // 상급악마/대악마/마왕: 스탯 상한 적용 (공격력 100%, 나머지 50%)
        boolean isMajorBoss = "상급악마".equals(bossDemonType) || "대악마".equals(bossDemonType) || "마왕".equals(bossDemonType);
        if (isMajorBoss) {
            bossAtkRate   = Math.max(BOSS_ATK_RATE_MIN,   Math.min(50, bossAtkRate));
            bossAtkPower  = Math.max(BOSS_ATK_POWER_MIN,  Math.min(100, bossAtkPower));
            bossDefRate   = Math.max(BOSS_DEF_RATE_MIN,   Math.min(50, bossDefRate));
            bossDefPower  = Math.max(BOSS_DEF_POWER_MIN,  Math.min(50, bossDefPower));
            bossEvadeRate = Math.max(BOSS_EVADE_RATE_MIN, Math.min(50, bossEvadeRate));
            critDefRate   = Math.max(0,                   Math.min(50, critDefRate));
        }
        // 재생성 쿨타임 체크 (INSERT_DATE가 미래면 아직 등장 전)
        if (!bossStartDate.isEmpty()) {
            try {
                LocalDateTime spawnTime = LocalDateTime.parse(bossStartDate,
                        DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
                if (LocalDateTime.now().isBefore(spawnTime)) {
                    long remainMin = java.time.Duration.between(LocalDateTime.now(), spawnTime).toMinutes();
                    String remainStr = remainMin >= 60
                            ? (remainMin / 60) + "시간 " + (remainMin % 60) + "분"
                            : remainMin + "분";
                    StringBuilder sb = new StringBuilder();
                    sb.append(userName).append("님,").append(NL)
                      .append("상급악마가 재정비 중입니다.").append(NL)
                      .append("등장까지 ").append(remainStr).append(" 남았습니다.");
                    try {
                        String lastReward = botS3Service.getLastKillRewardMsg();
                        if (lastReward != null && !lastReward.isEmpty()) {
                            sb.append(NL).append(NL).append("[이전 클리어 이력]").append(NL).append(lastReward);
                        }
                    } catch (Exception ignored) {}
                    return sb.toString();
                }
            } catch (Exception ignored) {}
        }

        // 보스 아이템 보유 여부: calcUserBattleContext에서 이미 로드된 ctx.ownedBossItems 재사용
        Set<Integer> ownedBoss = ctx.ownedBossItems;
        boolean hasHeavenItem = ownedBoss.contains(7001); // 7001: 천벌 발동

        // 보스 아이템 강화 등급 (bossItemQty: itemId -> totalQty, 강화=qty-1)
        @SuppressWarnings("unchecked")
        java.util.Map<Integer,Integer> bossItemQtyMap =
            (java.util.Map<Integer,Integer>) bossAttackController.getInvBuffCachedPublic(ctx.targetUser).getOrDefault("bossItemQty", java.util.Collections.emptyMap());
        int heaven7001Qty = bossItemQtyMap.getOrDefault(7001, hasHeavenItem ? 1 : 0);

        Random rand = new Random();

        // 천벌 발동 (강화 테이블 기준, 디버프 활성 시 발동 불가)
        int heavenRate = getBossEnhanceVal(7001, heaven7001Qty);
        boolean heavensPunishment = false;
        String punishMsg = "";
        if (hasHeavenItem && debuff == 0) {
            if (rand.nextInt(100) < heavenRate) {
                heavensPunishment = true;
                punishMsg = "[천벌" + enhanceSuffix(heaven7001Qty) + "] 효과! 보스회피/방어를 무시하고 초강력치명타!" + NL;
            }
        }

        // 디버프 상태 처리
        boolean flag_boss_debuff  = debuff  > 0;
        boolean flag_boss_debuff1 = debuff1 > 0;
        boolean debuff1_start = false;
        String debuff1Msg = "";

        if (flag_boss_debuff) {
            bossAtkRate   = 0;
            bossDefRate   = 0;
            bossEvadeRate = 0;
        }
        if (flag_boss_debuff1) {
            critDefRate = Math.max(0, critDefRate - 5);
            debuff1Msg = "[디버프1] 보스 치명저항 감소" + NL;
        }

        // 보스 회피 판정
        boolean isEvade = !heavensPunishment && (Math.random() < bossEvadeRate / 100.0);

        // [7010] 주시자의눈: 회피저지 확률(기본30%, +1=60%)
        String bossEvadeIgnoreMsg = "";
        if (isEvade && ownedBoss.contains(7010)) {
            int qty7010 = bossItemQtyMap.getOrDefault(7010, 1);
            int evadeBreakChance = getBossEnhanceVal(7010, qty7010);
            if (ThreadLocalRandom.current().nextInt(100) < evadeBreakChance) {
                isEvade = false;
                bossEvadeIgnoreMsg = "[주시자의눈" + enhanceSuffix(qty7010) + "] 보스 회피 저지! (" + evadeBreakChance + "%)" + NL;
            }
        }

        // 데미지 계산
        long damage = 0;
        boolean isCritical = false;
        boolean isSuperCritical = false;
        String dmgMsg = "";
        String bossDefMsg = "";
        String hideMsg = "";
        String hideIgnoreMsg = "";
        String windSlashMsg = "";
        // [7015] 무한의대검: 슈퍼크리티컬 추가 확률(기본+10%, +1=+15%, +2=+20%)
        double superCritChance = 0.10;
        if (ownedBoss.contains(7015)) {
            int qty7015 = bossItemQtyMap.getOrDefault(7015, 1);
            superCritChance += getBossEnhanceVal(7015, qty7015) / 100.0;
        }

        if (!isEvade) {
            String atkRangeStr = "(" + (ctx.atkMin / 100) + "~" + (ctx.atkMax / 100) + ") ";
            // 보스 기본 크리율 10% 고정 (S2 크리율 미적용), 초강력치명타는 크리 발동 시 10% 확률
            int totalCritPercent = Math.max(0, 100 - critDefRate);

            // HIDE_RULE: 특정 시간대 치명타 불가 (천벌/디버프/7011 확률 성공 시 무시)
            // [7011] 개척자: 숨기무시 확률(기본30%, +1=60%)
            boolean has7011 = false;
            if (ownedBoss.contains(7011)) {
                int qty7011 = bossItemQtyMap.getOrDefault(7011, 1);
                int hideBreakChance = getBossEnhanceVal(7011, qty7011);
                if (ThreadLocalRandom.current().nextInt(100) < hideBreakChance) {
                    has7011 = true;
                    if (!applyHideRule(hideRule, false).isEmpty()) {
                        hideIgnoreMsg = "[개척자" + enhanceSuffix(qty7011) + "] 보스 숨기 무시! (" + hideBreakChance + "%)" + NL;
                    }
                }
            }
            hideMsg = applyHideRule(hideRule, heavensPunishment || flag_boss_debuff || has7011);

            double critMultiplier = Math.max(1.0, ctx.critDmg / 100.0);

            if ("궁사".equals(ctx.job)) {
                // [궁사] 연사 로직 (S2와 동일 구조)
                int range = Math.max(0, ctx.atkMax - ctx.atkMin);
                double rangeRatio = ctx.atkMax > 0 ? (double) range / ctx.atkMax : 0.0;
                int hitCount;
                if      (rangeRatio >= 0.50) hitCount = 5;
                else if (rangeRatio >= 0.30) hitCount = 4;
                else if (rangeRatio >= 0.10) hitCount = 3;
                else                         hitCount = 2;
                if (ownedBoss.contains(7003)) { // [7003] 주작궁: 연사 추가(기본+1, +1강화=+2, +2강화=+3)
                    int qty7003 = bossItemQtyMap.getOrDefault(7003, 1);
                    hitCount = Math.min(hitCount + getBossEnhanceVal(7003, qty7003), 7);
                }

                int remainCrit = Math.max(0, heavensPunishment ? 100 : totalCritPercent);
                double perHitRate = hitCount > 1 ? Math.min(80.0, (double) remainCrit / (hitCount - 1)) : 0.0;

                long totalMultiDmg = 0;
                boolean allCrit = true;
                StringBuilder multiMsg = new StringBuilder();
                multiMsg.append("궁사의 연사 발동! ").append(hitCount).append("연사").append(NL);

                for (int i = 1; i <= hitCount; i++) {
                    int shotAtk = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
                    if (shotAtk < 1) shotAtk = 1;
                    shotAtk = (int) Math.round(shotAtk * 0.7);
                    boolean shotCrit = heavensPunishment || (i == 1) || (!hideMsg.isEmpty() ? false : ThreadLocalRandom.current().nextInt(0, 101) <= perHitRate);
                    long shotDmg;
                    if (heavensPunishment) {
                        // 천벌: 초강력치명타 (×3 × critMultiplier)
                        shotDmg = (long)(shotAtk * 3 * critMultiplier * 0.70);
                        multiMsg.append(i).append("타: ").append(shotDmg).append(" (✨초강력치명타!)").append(NL);
                    } else {
                        shotDmg = shotCrit ? (long)(shotAtk * critMultiplier * 0.70) : shotAtk;
                        multiMsg.append(i).append("타: ").append(shotDmg);
                        if (shotCrit) multiMsg.append(" (치명!)");
                        multiMsg.append(NL);
                    }
                    totalMultiDmg += shotDmg;
                    if (!shotCrit) allCrit = false;
                }
                if (allCrit) {
                    long before = totalMultiDmg;
                    totalMultiDmg = (long)(totalMultiDmg * 1.3);
                    multiMsg.append("ALL 치명! ").append(before).append(" → ").append(totalMultiDmg).append(" (+30%)").append(NL);
                } else {
                    multiMsg.append("총합 데미지: ").append(totalMultiDmg).append("!").append(NL);
                }
                isCritical = allCrit;
                isSuperCritical = heavensPunishment;
                damage = totalMultiDmg;
                dmgMsg = atkRangeStr + multiMsg;
            } else {
                // 일반 단타 로직
                int baseAtk = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
                if (baseAtk < 1) baseAtk = 1;

                // 직업 추가 데미지: 상급악마(악마 속성)
                if ("어둠사냥꾼".equals(ctx.job))      baseAtk = (int) Math.round(baseAtk * 2.0);
                else if ("용사".equals(ctx.job)) baseAtk = (int) Math.round(baseAtk * 1.25);
                else if ("엘프".equals(ctx.job) || "엘프궁수".equals(ctx.job) || "엘프마법사".equals(ctx.job)) {
                    int hour = java.time.LocalTime.now().getHour();
                    boolean elfNight = (hour >= 18 || hour < 6);
                    double elfMul = elfNight ? 3.0 : 2.0;
                    baseAtk = (int) Math.round(baseAtk * elfMul);
                    if (elfNight) windSlashMsg += "[다크엘프 각성] 데미지 3배" + NL;
                }

                // [검성] 바람가르기 (기본 6.5%; 7006 보유 시 SET: 기본=15%, +1강화=30%)
                if ("검성".equals(ctx.job)) {
                    double windProb = 0.065;
                    if (ownedBoss.contains(7006)) {
                        int qty7006 = bossItemQtyMap.getOrDefault(7006, 1);
                        windProb = getBossEnhanceVal(7006, qty7006) / 100.0;
                    }
                    if (Math.random() < windProb) {
                        windSlashMsg = "[바람가르기] " + baseAtk + "→";
                        baseAtk = (int) Math.round(baseAtk * 4);
                        windSlashMsg += baseAtk + " (4배 데미지!)" + NL;
                    }
                }

                // [도박사] 공격 도박
                String gamblerAtkMsg = "";
                if ("도박사".equals(ctx.job)) {
                    int gRoll = ThreadLocalRandom.current().nextInt(1, 101);
                    int gMul = 1;
                    if      (gRoll <= 1)  gMul = 50;
                    else if (gRoll <= 3)  gMul = 25;
                    else if (gRoll <= 6)  gMul = 16;
                    else if (gRoll <= 10) gMul = 12;
                    else if (gRoll <= 15) gMul = 10;
                    else if (gRoll <= 21) gMul = 8;
                    else if (gRoll <= 28) gMul = 7;
                    else if (gRoll <= 36) gMul = 6;
                    else if (gRoll <= 45) gMul = 5;
                    else if (gRoll <= 55) gMul = 5;
                    if (gRoll > 55) {
                        gamblerAtkMsg = "도박 실패! (크리티컬 해제)" + NL;
                    } else {
                        int before = baseAtk;
                        baseAtk = baseAtk * gMul;
                        gamblerAtkMsg = "도박 성공! (피해량 ×" + gMul + ") " + before + " ⇒ " + baseAtk + NL;
                    }
                }

                if (heavensPunishment) {
                    isCritical = true; isSuperCritical = true;
                } else {
                    isCritical = "도박사".equals(ctx.job) && gamblerAtkMsg.startsWith("도박 실패") ? false
                            : totalCritPercent > 0 && Math.random() < totalCritPercent / 100.0;
                    // 도박사는 슈퍼크리티컬 불가 (7001 천벌 효과는 가능)
                    if (isCritical) isSuperCritical = !"도박사".equals(ctx.job) && Math.random() < superCritChance;
                }

                if (!hideMsg.isEmpty()) {
                    isCritical = false;
                    isSuperCritical = false;
                }

                if (isSuperCritical) {
                    // [7020] 초강력치명타: 슈퍼크리티컬 5배 → 6배, 1강화 6.5배
                    double superMul = 1.0;
                    if (ownedBoss.contains(7020)) {
                        int qty7020 = bossItemQtyMap.getOrDefault(7020, 1);
                        superMul = (qty7020 >= 2) ? 6.5 / 5.0 : 6.0 / 5.0;
                    }
                    damage = (long)(baseAtk * 3 * critMultiplier * superMul);
                    dmgMsg = "[✨초강력 치명타!!] " + atkRangeStr + baseAtk + " → " + damage;
                } else if (isCritical) {
                    damage = (long)(baseAtk * critMultiplier);
                    dmgMsg = "[✨치명타!] " + atkRangeStr + baseAtk + " → " + damage;
                } else {
                    damage = baseAtk;
                    dmgMsg = atkRangeStr + baseAtk + " 로 공격!";
                }
                if (!gamblerAtkMsg.isEmpty()) dmgMsg = gamblerAtkMsg + dmgMsg;
            }

            // 천벌 디버프 상태 표시 (데미지 2배 효과 제거)
            if (flag_boss_debuff) {
                punishMsg = "[천벌디버프] " + (debuff - 1) + "회 남음" + NL;
            }

            // 보스 방어 (천벌/디버프 무시, 방어력: 데미지의 X% 감소)
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                int defPct = ThreadLocalRandom.current().nextInt(BOSS_DEF_POWER_MIN, bossDefPower + 1);
                long defAmt = damage * defPct / 100;
                damage = Math.max(0, damage - defAmt);
                bossDefMsg = "보스가 방어하였습니다! 데미지 " + defPct + "% 감소 (-" + defAmt + ")!" + NL;
            }
        }

        // [도적] 2타 계산 (기본 20% / 7002 보유 시 35% 확률, 회피 시 발동 안함)
        long damage2 = 0;
        boolean thiefHit2 = false;
        String dmgMsg2 = "", bossDefMsg2 = "";
        boolean isCritical2 = false, isSuperCritical2 = false;
        double thiefProb = 0.20;
        if (ownedBoss.contains(7002)) {
            int qty7002 = bossItemQtyMap.getOrDefault(7002, 1);
            thiefProb = getBossEnhanceVal(7002, qty7002) / 100.0;
        }
        if (!isEvade && "도적".equals(ctx.job) && Math.random() < thiefProb) {
            thiefHit2 = true;
            int baseAtk2 = (ctx.atkMin + rand.nextInt(Math.max(1, ctx.atkMax - ctx.atkMin + 1))) / 100;
            if (baseAtk2 < 1) baseAtk2 = 1;
            String atkRangeStr2 = "(" + (ctx.atkMin / 100) + "~" + (ctx.atkMax / 100) + ") ";
            // 직업 추가 데미지 (2타)
            if ("어둠사냥꾼".equals(ctx.job))      baseAtk2 = (int) Math.round(baseAtk2 * 2.0);
            else if ("용사".equals(ctx.job)) baseAtk2 = (int) Math.round(baseAtk2 * 1.25);
            int totalCrit2 = Math.max(0, 10 - critDefRate); // 보스 기본 크리율 10% 고정
            if (heavensPunishment) {
                // 천벌: 2타도 초강력치명타
                isCritical2 = true;
                isSuperCritical2 = true;
            } else {
                isCritical2 = totalCrit2 > 0 && Math.random() < totalCrit2 / 100.0;
                if (isCritical2) isSuperCritical2 = Math.random() < superCritChance;
            }
            double critMul2 = Math.max(1.0, ctx.critDmg / 100.0);
            if (isSuperCritical2) {
                // [7020] 초강력치명타: 슈퍼크리티컬 5배 → 6배, 1강화 6.5배
                double superMul2 = 1.0;
                if (ownedBoss.contains(7020)) {
                    int qty7020 = bossItemQtyMap.getOrDefault(7020, 1);
                    superMul2 = (qty7020 >= 2) ? 6.5 / 5.0 : 6.0 / 5.0;
                }
                damage2 = (long)(baseAtk2 * 3 * critMul2 * superMul2);
                dmgMsg2 = "[✨초강력 치명타!!] " + atkRangeStr2 + baseAtk2 + " → " + damage2;
            } else if (isCritical2) {
                damage2 = (long)(baseAtk2 * critMul2);
                dmgMsg2 = "[✨치명타!] " + atkRangeStr2 + baseAtk2 + " → " + damage2;
            } else {
                damage2 = baseAtk2;
                dmgMsg2 = atkRangeStr2 + baseAtk2 + " 로 공격!";
            }
            if (!heavensPunishment && !flag_boss_debuff && Math.random() < bossDefRate / 100.0) {
                int defPct2 = ThreadLocalRandom.current().nextInt(BOSS_DEF_POWER_MIN, bossDefPower + 1);
                long defAmt2 = damage2 * defPct2 / 100;
                damage2 = Math.max(0, damage2 - defAmt2);
                bossDefMsg2 = "보스가 2타 방어! 데미지 " + defPct2 + "% 감소 (-" + defAmt2 + ")!" + NL;
            }
        }
        long totalDamage = damage + damage2;

        String dmgLimitMsg = "";
        boolean isMaWang = "마왕".equals(bossDemonType);

        String maWangMulMsg = "";
        if (isMaWang) {
            // 마왕: 10배 데미지, 용사 추가 2배, 100만 cap
            long beforeMul = totalDamage;
            totalDamage *= 10L;
            maWangMulMsg = "⚔️ [마왕] 데미지 ×10 적용: " + beforeMul + " → " + totalDamage + NL;
            if ("용사".equals(ctx.job)) {
                long before2 = totalDamage;
                totalDamage *= 2L;
                maWangMulMsg += "⚔️ [용사 특권] 추가 ×2 적용: " + before2 + " → " + totalDamage + NL;
            }
            final long MAWANG_CAP = 1_000_000L;
            if (totalDamage > MAWANG_CAP) {
                dmgLimitMsg = "[데미지 제한] " + totalDamage + " → " + MAWANG_CAP + " (마왕 100만 cap)" + NL;
                totalDamage = MAWANG_CAP;
            }
        } else {
            // 보스 최대체력의 20% 이상 피해 제한
            long maxDamageLimit = Math.max(1L, maxHp / 5);
            if (totalDamage > maxDamageLimit) {
                long beforeLimit = totalDamage;
                totalDamage = maxDamageLimit;
                dmgLimitMsg = "[데미지 제한] "
                        + SP.fromSp(beforeLimit)
                        + " → "
                        + SP.fromSp(totalDamage)
                        + " (보스 최대체력 20% 제한)"
                        + NL;
            }
        }
        
        // [7012] 도사 버프 적용 (보스전)
        String dosaBossBuffMsg = "";
        {
            boolean dosaSelf = "도사".equals(ctx.job) || "음양사".equals(ctx.job);
            HashMap<String,Object> dosaRoomRaw = botNewService.selectDosaBuffInfo();
            boolean dosaRoom = dosaRoomRaw != null;
            int buffCount = (dosaSelf ? 1 : 0) + (dosaRoom ? 1 : 0);
            if (buffCount > 0 && !isEvade) {
                int coef = 1;
                if (ownedBoss.contains(7012)) {
                    int qty7012 = bossItemQtyMap.getOrDefault(7012, 1);
                    coef = getBossEnhanceVal(7012, qty7012); // 기본×3, +1=×4, +2=×5
                }
                long flatBonus = (long) buffCount * 1000 * coef;
                totalDamage += Math.round(totalDamage * (buffCount * 5 * coef) / 100.0);
                totalDamage += flatBonus;
                int healAmt = (int) Math.round(ctx.hpMax * (buffCount * 5 * coef) / 100.0);
                int beforeHp = user.hpCur;
                user.hpCur = Math.min(ctx.hpMax, user.hpCur + healAmt);
                int actualHeal = user.hpCur - beforeHp;
                StringBuilder dosaSb = new StringBuilder("※도사 기원: 최종 데미지 +").append(flatBonus);
                int rateBonus = buffCount * 5 * coef;
                if (rateBonus > 0) dosaSb.append(", +").append(rateBonus).append("%");
                if (coef > 1) dosaSb.append(" [수선도사의머리띠 ×").append(coef).append("]");
                if (actualHeal > 0) dosaSb.append(", HP +").append(actualHeal).append(" 회복");
                dosaBossBuffMsg = dosaSb.toString() + NL;
                if (dosaRoom) botNewService.clearRoomBuff();
            }
        }

        // HP 차감 (1차)
        SP newHpSp = isEvade || totalDamage <= 0
                ? SP.of(curHpNum, curHpExt)
                : SP.of(curHpNum, curHpExt).subtract(SP.fromSp(totalDamage));
        boolean isKill = SP.toBaseValue(newHpSp) <= 0;
        long newHp = isKill ? 0 : SP.toBaseValue(newHpSp);

        // 보스 반격 (유저 최대HP × bossAtkPower% 비례 데미지)
        int bossAtkApplied = 0;
        boolean flag_boss_attack = !isKill && !heavensPunishment && Math.random() < bossAtkRate / 100.0;
        if (flag_boss_attack) {
            int atkPct = ThreadLocalRandom.current().nextInt(BOSS_ATK_POWER_MIN, bossAtkPower + 1);
            bossAtkApplied = Math.max(1, (int)(ctx.hpMax * atkPct / 100.0));
        }

        // 세트 회피: 보스 반격 회피 판정
        String bossAtkEvadeMsg = "";
        if (flag_boss_attack && bossAtkApplied > 0 && ctx.setEvasionRate > 0
                && ThreadLocalRandom.current().nextInt(100) < ctx.setEvasionRate) {
            bossAtkApplied = 0;
            flag_boss_attack = false;
            bossAtkEvadeMsg = "[회피!] 보스의 반격을 피했습니다!" + NL;
        }

        // [도박사] 보스 반격 도박 판정
        String gamblerDefMsg = "";
        if ("도박사".equals(ctx.job) && flag_boss_attack && bossAtkApplied > 0) {
            int gRoll = ThreadLocalRandom.current().nextInt(1, 101);
            if (gRoll <= 11) {
                bossAtkApplied = 0;
                flag_boss_attack = false;
                gamblerDefMsg = "[도박대성공!] 보스 반격 완전 회피!" + NL;
            } else if (gRoll <= 44) {
                int reduced = bossAtkApplied / 2;
                bossAtkApplied = reduced;
                gamblerDefMsg = "[도박성공!] 보스 반격 50% 감소 → " + reduced + NL;
            } else if (gRoll <= 88) {
                int doubled = bossAtkApplied * 2;
                bossAtkApplied = doubled;
                gamblerDefMsg = "[도박실패!] 보스 반격 2배 → " + doubled + NL;
            } else {
                int tripled = bossAtkApplied * 3;
                bossAtkApplied = tripled;
                gamblerDefMsg = "[도박대실패!] 보스 반격 3배 → " + tripled + NL;
            }
        }

        // [7005] 가시갑옷: 보스전에서는 기존(10%) 대비 10% 성능 → 받은 피해의 1% 반사
        int reflectDmg = 0;
        String reflectMsg = "";
        if (ownedBoss.contains(7005) && bossAtkApplied > 0) {
            int qty7005 = bossItemQtyMap.getOrDefault(7005, 1);
            int reflectPermille = getBossEnhanceVal(7005, qty7005); // 10=1.0%, 15=1.5%
            reflectDmg = Math.max(1, (int)Math.round(bossAtkApplied * reflectPermille / 1000.0));
            totalDamage += reflectDmg;
            newHpSp = SP.of(curHpNum, curHpExt).subtract(SP.fromSp(totalDamage));
            isKill = SP.toBaseValue(newHpSp) <= 0;
            newHp = isKill ? 0 : SP.toBaseValue(newHpSp);
            reflectMsg = "[가시갑옷] 반사 +" + reflectDmg + "!" + NL;
        }

        // 플레이어 HP 차감 + 사망 판정
        boolean playerDead = false;
        if (flag_boss_attack && bossAtkApplied > 0) {
            user.hpCur = Math.max(0, user.hpCur - bossAtkApplied);
            playerDead = (user.hpCur == 0);
        }
        // 플레이어 HP DB 저장 (레벨/스탯은 그대로, HP만 갱신)
        if (flag_boss_attack && bossAtkApplied > 0) {
            try {
                int baseHpMax   = my.prac.core.util.MiniGameUtil.calcBaseHpMax(user.lv);
                int baseAtkMin  = my.prac.core.util.MiniGameUtil.calcBaseAtkMin(user.lv);
                int baseAtkMax  = my.prac.core.util.MiniGameUtil.calcBaseAtkMax(user.lv);
                int baseCrit    = my.prac.core.util.MiniGameUtil.calcBaseCritRate(user.lv);
                int baseHpRegen = my.prac.core.util.MiniGameUtil.calcBaseHpRegen(user.lv);
                botNewService.updateUserAfterBattleTx(
                    userName, roomName,
                    user.lv, user.expCur, user.expNext,
                    user.hpCur, baseHpMax, baseAtkMin, baseAtkMax, baseCrit, baseHpRegen
                );
            } catch (Exception ignored) {}
        }

        // 대악마/마왕 감금스킬 발동 (10% 확률)
        if (("대악마".equals(bossDemonType) || "마왕".equals(bossDemonType)) && rand.nextInt(100) < IMPRISON_CHANCE_PCT) {
            IMPRISONED_UNTIL.put(userName, System.currentTimeMillis() + IMPRISON_DURATION_MS);
            // 감금 메시지는 아래 최종 msg 조립 후 추가
        }

        // DB 저장 (HP 업데이트 + 배틀 로그)
        // hp    : 낙관적 잠금 WHERE 절용 → DB 원본값 그대로 (double)
        // newHp : SP 변환 결과 소수값 (예: 2.99832)
        // newHpExt: SP 변환 결과 단위 (예: "b"), 없으면 null
        double newHpDbVal = isKill ? 0.0 : newHpSp.getValue();
        String newHpDbExt = isKill || newHpSp.getUnit().isEmpty() ? null : newHpSp.getUnit();
        map.put("hp",       curHpNum);
        map.put("newHp",    newHpDbVal);
        map.put("newHpExt", newHpDbExt);
        map.put("seq",          seq);
        map.put("endYn",        isKill ? "1" : "0");
        map.put("lv",           user.lv);
        map.put("atkDmg",       totalDamage);
        map.put("monDmg",       bossAtkApplied);
        map.put("atkCritYn",    isCritical ? "1" : "0");
        map.put("killYn",       isKill ? "1" : "0");
        map.put("job",          ctx.job);
        if (heavensPunishment)                   map.put("heavensPunishment", getBossEnhanceVal(7001, heaven7001Qty));
        if (flag_boss_debuff)                    map.put("useDebuff", 1);
        if (debuff1_start)                       map.put("debuff1_start", 1);
        if (flag_boss_debuff1 && !debuff1_start) map.put("useDebuff1", 1);

        try {
            botS3Service.updateHellBossTx(map);
        } catch (Exception e) {
            return "저장 중 오류가 발생했습니다.";
        }

        // MON_KILL_STAT 실시간 카운터 업데이트 (공격 + 킬)
        try {
            int _killInc = isKill ? 1 : 0;
            HashMap<String,Object> ks = new HashMap<>();
            ks.put("userName",         userName);
            ks.put("monNo",            999);
            ks.put("killInc",          _killInc);
            ks.put("nmKillInc",        0);
            ks.put("hellKillInc",      _killInc);
            ks.put("hellbossAtkInc",   1);
            ks.put("hellbossClearInc", 0);
            botNewService.upsertMonKillStat(ks);
        } catch (Exception ignore) {}

        // 보스 처치 시 참여자 전원 HELLBOSS_CLEAR_CNT +1
        if (isKill) {
            try {
                HashMap<String,Object> cp = new HashMap<>();
                cp.put("bossStartDate", bossStartDate);
                botNewService.upsertHellBossClearForParticipants(cp);
            } catch (Exception ignore) {}
        }

        // 공격 보상: 마왕은 GP, 그 외는 SP
        String spRewardMsg = "";
        if (isMaWang && !isEvade && totalDamage > 0) {
            // 마왕 GP 보상: 0.02 ~ 0.40 GP (데미지 비례, 100만 대비)
            try {
                double gpRatio = Math.min(1.0, totalDamage / 1_000_000.0);
                double gpMin = 0.02, gpMax = 0.40;
                double gpAmount = gpMin + (gpMax - gpMin) * gpRatio;
                gpAmount = Math.round(gpAmount * 100) / 100.0;
                if (gpAmount < gpMin) gpAmount = gpMin;
                HashMap<String, Object> gp = new HashMap<>();
                gp.put("userName", userName);
                gp.put("roomName", roomName);
                gp.put("score",    gpAmount);
                gp.put("cmd",      "MAWANG_ATK_GP");
                botNewService.insertGpRecord(gp);
                double gpBalance = botNewService.selectGpBalance(userName);
                spRewardMsg = String.format("✨GP 획득! +%.2f GP (보유: %.2f GP)%s", Math.floor(gpAmount * 100) / 100, Math.floor(gpBalance * 100) / 100, NL);
            } catch (Exception e) {
                // GP 지급 실패는 무시
            }
        } else if (!isMaWang) {
            try {
                long rawSpVal = totalDamage * 10000L;
                boolean isGreatDemonSp = "대악마".equals(bossDemonType);
                long spCap = isGreatDemonSp ? 3_000_000_000L : 1_000_000_000L;
                long spMin2 = isGreatDemonSp ? 30_000_000L   : 10_000_000L;
                if (isGreatDemonSp) rawSpVal *= 3;
                boolean spCapped = rawSpVal > spCap;
                boolean spMin    = rawSpVal < spMin2;
                rawSpVal = Math.max(Math.min(rawSpVal, spCap), spMin2);
                SP spReward = SP.fromSp(rawSpVal);
                HashMap<String, Object> pr = new HashMap<>();
                pr.put("userName", userName);
                pr.put("roomName", roomName);
                pr.put("score",    spReward.getValue());
                pr.put("scoreExt", spReward.getUnit());
                pr.put("cmd",      "BOSS_HELL_ATK");
                botNewService.insertPointRank(pr);
                spRewardMsg = " 획득 SP: " + spReward + (spCapped ? " (max)" : spMin ? " (min)" : "") + NL;
            } catch (Exception e) {
                // SP 지급 실패는 무시
            }
        }

        // 헬보스 업적 체크 (공격 업적, 클리어 참여 업적)
        String hellAchvMsg = grantHellBossAchievements(userName, roomName, isKill, bossStartDate);

        // 처치 보상 + 보스 재생성
        String killMsg = "";
        if (isKill) {
            if (isMaWang) {
                killMsg = "[마왕] " + userName + "님이 마왕을 처치했습니다! (처치 보상 없음)";
            } else {
                killMsg = calcHellBossReward(roomName, bossStartDate, maxHp, bossRewardType, bossDemonType);
            }
            botS3Service.saveLastKillMsg(killMsg); // 대기화면 표시용 캐시
            respawnHellBoss(bossStartDate);
        }

        // 결과 메시지
        StringBuilder msg = new StringBuilder();
        msg.append(userName).append("님이 [").append(bossDemonType).append("]를 공격했습니다!").append(NL);

        if (!isEvade) {
            if (isMaWang) {
                msg.append("▶ 기본 데미지: ").append(damage);
                if (thiefHit2) msg.append(" + ").append(damage2).append(" (2타)");
                msg.append(NL);
                msg.append(maWangMulMsg);
            } else {
                msg.append("▶ 입힌 데미지: ").append(damage).append(NL);
            }
            msg.append(dmgMsg).append(NL);
            if (!bossEvadeIgnoreMsg.isEmpty()) msg.append(bossEvadeIgnoreMsg);
            if (!windSlashMsg.isEmpty()) msg.append(windSlashMsg);
            if (thiefHit2 && !isMaWang) {
                msg.append("⚔ 2타 데미지: ").append(damage2).append(NL);
                msg.append(dmgMsg2).append(NL);
                if (!bossDefMsg2.isEmpty()) msg.append(bossDefMsg2);
            } else if (thiefHit2 && isMaWang) {
                msg.append(dmgMsg2).append(NL);
                if (!bossDefMsg2.isEmpty()) msg.append(bossDefMsg2);
            }
            if (!hideIgnoreMsg.isEmpty()) msg.append(hideIgnoreMsg);
            if (!hideMsg.isEmpty())     msg.append(hideMsg);
            if (!reflectMsg.isEmpty())  msg.append(reflectMsg);
            if (!hellAchvMsg.isEmpty()) msg.append(hellAchvMsg);
            if (!punishMsg.isEmpty())   msg.append(punishMsg);
            if (!debuff1Msg.isEmpty())  msg.append(debuff1Msg);
            if (!bossDefMsg.isEmpty())  msg.append(bossDefMsg);
            if (!dosaBossBuffMsg.isEmpty()) msg.append(dosaBossBuffMsg);
            if (!dmgLimitMsg.isEmpty()) msg.append(dmgLimitMsg);
        } else {
            msg.append("보스가 공격을 회피했습니다! 데미지 0!").append(NL);
        }
        if (!spRewardMsg.isEmpty()) msg.append(spRewardMsg);

        if (!bossAtkEvadeMsg.isEmpty()) msg.append(bossAtkEvadeMsg);
        if (!gamblerDefMsg.isEmpty()) msg.append(gamblerDefMsg);
        if (flag_boss_attack && bossAtkApplied > 0) {
            msg.append("▶ 보스의 반격! 최대HP의 피해! (").append(bossAtkApplied).append(")").append(NL);
            msg.append("  └ 남은체력: ").append(user.hpCur).append("/").append(ctx.hpMax).append(NL);
            if (playerDead) msg.append("  ☠ 체력이 0이 되었습니다! 데스 상태 — 체력 회복 후 공격 가능합니다.").append(NL);
        }

        msg.append(NL);
        if (isKill) {
            msg.append("✨").append(bossDemonType).append("를 처치했습니다!").append(NL).append(killMsg);
            msg.append(NL).append("새로운 상급악마가 출현했습니다!").append(NL);
        } else {
            String curHpDisp = SP.fromSp(newHp).toString();
            String maxHpDisp = SP.fromSp(maxHp).toString();
            double hpPct = maxHp > 0 ? (newHp * 100.0) / maxHp : 0;
            msg.append("보스 체력: ").append(curHpDisp).append("/").append(maxHpDisp)
               .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);
        }

        // 스페셜타임 진행중이면 표기
        String specialTimeMsg = bossAttackController.getActiveSpecialTimeMsg();
        if (!specialTimeMsg.isEmpty()) {
            msg.append(specialTimeMsg).append(NL);
        }

        // 대악마/마왕 감금스킬 발동 메시지
        if (IMPRISONED_UNTIL.containsKey(userName) &&
                System.currentTimeMillis() < IMPRISONED_UNTIL.get(userName)) {
            msg.append(NL).append("[감금스킬] ").append(userName).append("님이 5분간 공격 불가 상태가 됩니다!");
        }

        return msg.toString().trim();
    }


    // =========================================================
    // 보스 재생성: 처치 후 랜덤 능력치로 새 보스 INSERT
    // =========================================================
    private static final String[] HIDE_RULES = {"아침", "점심", "저녁", "새벽"};
    private static final DateTimeFormatter SPAWN_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 보스 재생성 — 참여 인원수에 따라 쿨타임/HP를 동적 산정
     *   쿨타임: 6명 이하 → 18시간(1080분), 13명 → 8시간(480분), 최대 단축 5시간(300분)
     *   수식: cooldownMin = max(300, min(1080, round(1080 - (n-6) * 600/7)))
     *   HP: 참여자 수 비례 보너스 배율 적용, 최대 50a(500,000 raw) 상한
     */
    private void respawnHellBoss(String bossStartDate) {
        try {
            Random rand = new Random();

            // 참여 인원수 조회 (쿨타임/HP 산정)
            int participantCount = 6; // 기본값 (조회 실패 시)
            try {
                HashMap<String, Object> q = new HashMap<>();
                q.put("bossStartDate", bossStartDate);
                List<HashMap<String, Object>> participants = botS3Service.selectHellTop3Contributors(q);
                if (participants != null && !participants.isEmpty()) participantCount = participants.size();
            } catch (Exception ignored) {}

            long cooldownMin = 1440; // 24시간 고정

            // 최근 공격 평균 데미지 기반으로 HP 산정 (2~3배 강화)
            long rawHp;
            try {
                Long avgDmg = botS3Service.selectRecentHellAvgDmg();
                if (avgDmg != null && avgDmg > 0) {
                    int hitTarget = 90 + rand.nextInt(23); // 90~112 랜덤
                    rawHp = avgDmg * hitTarget;
                } else {
                    // 데이터 없을 때 기본값
                    rawHp = BOSS_MAX_HP_MIN + (long)(rand.nextDouble() * (BOSS_MAX_HP_MAX - BOSS_MAX_HP_MIN + 1));
                }
            } catch (Exception e) {
                rawHp = BOSS_MAX_HP_MIN + (long)(rand.nextDouble() * (BOSS_MAX_HP_MAX - BOSS_MAX_HP_MIN + 1));
            }

            // [강화] 보스 체력 2~3배 배율 적용
            double hpMult = 2.0 + rand.nextDouble(); // 2.0 ~ 3.0
            rawHp = (long)(rawHp * hpMult);

            // 참여 인원수 기반 HP 보너스 배율 적용 (최대 75a = 750,000 raw 상한)
            if (participantCount > 6) {
                double participantMult = 1.0 + (participantCount - 6) * 0.1; // 1인당 +10%
                rawHp = (long)(rawHp * participantMult);
            }
            rawHp = Math.max(rawHp, 1_000_000L);  // 최소 100a
            rawHp = Math.min(rawHp, 3_000_000L);  // 최대 300a

            HashMap<String, Object> bossMap = new HashMap<>();
            bossMap.put("atkRate",     randInt(rand, BOSS_ATK_RATE_MIN,   BOSS_ATK_RATE_MAX));
            bossMap.put("atkPower",    randInt(rand, BOSS_ATK_POWER_MIN,  BOSS_ATK_POWER_MAX));
            bossMap.put("defRate",     randInt(rand, BOSS_DEF_RATE_MIN,   BOSS_DEF_RATE_MAX));
            bossMap.put("defPower",    randInt(rand, BOSS_DEF_POWER_MIN,  BOSS_DEF_POWER_MAX));
            bossMap.put("evadeRate",   randInt(rand, BOSS_EVADE_RATE_MIN, BOSS_EVADE_RATE_MAX));
            bossMap.put("critDefRate", randInt(rand, BOSS_CRIT_DEF_MIN,   BOSS_CRIT_DEF_MAX));
            // 참여 인원수 기반 쿨타임 후 등장
            bossMap.put("startDate",   LocalDateTime.now().plusMinutes(cooldownMin).format(SPAWN_DATE_FMT));
            bossMap.put("hideRule",    HIDE_RULES[rand.nextInt(HIDE_RULES.length)]);
            // 보상 타입 사전 결정 (GP:40%, BOX:40%, ITEM:20%)
            double rwDice = rand.nextDouble();
            String preRewardType = rwDice >= 0.80 ? "ITEM" : rwDice >= 0.40 ? "BOX" : "GP";
            bossMap.put("rewardType", preRewardType);
            // 보스 타입 결정: 마왕 20%, 대악마 15%, 상급악마 65%
            double bossTypeDice = rand.nextDouble();
            String bossType;
            if (bossTypeDice < 0.20) {
                bossType = "마왕";
            } else if (bossTypeDice < 0.35) {
                bossType = "대악마";
            } else {
                bossType = "상급악마";
            }
            boolean isGreatDemon = "대악마".equals(bossType);
            boolean isDemonKing  = "마왕".equals(bossType);
            bossMap.put("bossType", bossType);
            if (isGreatDemon) {
                // 대악마 HP: 1200a ~ 1500a 고정 범위
                rawHp = 12_000_000L + (long)(rand.nextDouble() * (15_000_000L - 12_000_000L + 1));
                bossMap.put("atkRate",     Math.min(100, (int) bossMap.get("atkRate")     * 3));
                bossMap.put("atkPower",    Math.min(100, (int) bossMap.get("atkPower")    * 3));
                bossMap.put("defRate",     Math.min(100, (int) bossMap.get("defRate")     * 3));
                bossMap.put("defPower",    Math.min(100, (int) bossMap.get("defPower")    * 3));
                bossMap.put("evadeRate",   Math.min(100, (int) bossMap.get("evadeRate")   * 3));
                bossMap.put("critDefRate", Math.min(100, (int) bossMap.get("critDefRate") * 3));
            } else if (isDemonKing) {
                // 마왕 HP: 90,000,000 ~ 110,000,000 raw SP (≈1b)
                rawHp = 90_000_000L + (long)(rand.nextDouble() * 20_000_001L);
                bossMap.put("atkRate",     Math.min(100, (int) bossMap.get("atkRate")     * 3));
                bossMap.put("atkPower",    Math.min(100, (int) bossMap.get("atkPower")    * 3));
                bossMap.put("defRate",     Math.min(100, (int) bossMap.get("defRate")     * 3));
                bossMap.put("defPower",    Math.min(100, (int) bossMap.get("defPower")    * 3));
                bossMap.put("evadeRate",   Math.min(100, (int) bossMap.get("evadeRate")   * 3));
                bossMap.put("critDefRate", Math.min(100, (int) bossMap.get("critDefRate") * 3));
            }
            SP hpSp = SP.fromSp(rawHp);
            bossMap.put("maxHp",    (long) hpSp.getValue());
            bossMap.put("maxHpExt", hpSp.getUnit().isEmpty() ? null : hpSp.getUnit());
            botS3Service.insertHellBoss(bossMap);
        } catch (Exception e) {
            // 재생성 실패는 무시 (처치 결과 메시지에 영향 없음)
        }
    }

    private int randInt(Random rand, int min, int max) {
        return min + rand.nextInt(max - min + 1);
    }

    // =====================================================
    // 헬보스 업적 체크 및 지급
    // =====================================================
    private static final int[] HELL_ATK_THRESHOLDS   = {10, 50, 100, 300, 500, 1000, 2000, 5000};
    private static final int[] HELL_CLEAR_THRESHOLDS = {1, 3, 5, 10, 20, 30, 50};
    /** 1a = 10000 raw SP */
    private static final long HELL_ACHV_REWARD_SP = 10_000L;

    private String grantHellBossAchievements(String userName, String roomName,
                                              boolean isKill, String bossStartDate) {
        StringBuilder sb = new StringBuilder();
        try {
            // 기달성 CMD 로드
            Set<String> achieved = new HashSet<>();
            List<HashMap<String, Object>> achvRows = botNewService.selectAchievementsByUser(userName, roomName);
            if (achvRows != null) {
                for (HashMap<String, Object> r : achvRows) {
                    String cmd = Objects.toString(r.get("CMD"), "");
                    if (cmd.startsWith("ACHV_HELL_ATK_") || cmd.startsWith("ACHV_HELL_CLEAR_")) {
                        achieved.add(cmd);
                    }
                }
            }

            // 공격 횟수 업적
            int atkCount = botNewService.selectHellBossAttackCount(userName);
            for (int th : HELL_ATK_THRESHOLDS) {
                if (atkCount < th) break;
                String cmd = "ACHV_HELL_ATK_" + th;
                if (achieved.contains(cmd)) continue;
                sb.append(grantHellAchv(userName, roomName, cmd, HELL_ACHV_REWARD_SP));
                achieved.add(cmd);
            }

            // 클리어 참여 횟수 업적
            int clearCount = botNewService.selectHellBossClearCount(userName);
            for (int th : HELL_CLEAR_THRESHOLDS) {
                if (clearCount < th) break;
                String cmd = "ACHV_HELL_CLEAR_" + th;
                if (achieved.contains(cmd)) continue;
                sb.append(grantHellAchv(userName, roomName, cmd, HELL_ACHV_REWARD_SP));
                achieved.add(cmd);
            }
        } catch (Exception ignore) {}
        return sb.toString();
    }

    private String grantHellAchv(String userName, String roomName, String cmd, long rewardRawSp) {
        try {
            SP sp = SP.fromSp(rewardRawSp);
            HashMap<String, Object> pr = new HashMap<>();
            pr.put("userName", userName);
            pr.put("roomName", roomName);
            pr.put("score",    sp.getValue());
            pr.put("scoreExt", sp.getUnit());
            pr.put("cmd",      cmd);
            botNewService.insertPointRank(pr);
            return "✨ 헬보스 업적! [" + cmd + "] +" + sp + " 지급" + NL;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HIDE_RULE 시간대 체크 (S1 동일 방식)
     * @param skip 천벌/디버프 활성 시 무시
     * @return 활성 시 메시지, 비활성 시 빈 문자열
     */
    private String applyHideRule(String hideRule, boolean skip) {
        if (skip) return "";
        LocalTime now = LocalTime.now();
        LocalTime s, e;
        String msg;
        switch (hideRule) {
            case "아침":
                s = LocalTime.of(6,  0); e = LocalTime.of(10, 0);
                msg = "보스가 안개에 숨었습니다 (치명타 불가, 06~10시)" + NL; break;
            case "점심":
                s = LocalTime.of(10, 0); e = LocalTime.of(15, 0);
                msg = "보스가 구름에 숨었습니다 (치명타 불가, 10~15시)" + NL; break;
            case "저녁":
                s = LocalTime.of(15, 0); e = LocalTime.of(19, 0);
                msg = "보스가 퇴근길에 숨었습니다 (치명타 불가, 15~19시)" + NL; break;
            default: // 새벽
                s = LocalTime.of(2,  0); e = LocalTime.of(6,  0);
                msg = "보스가 어둠에 숨었습니다 (치명타 불가, 02~06시)" + NL; break;
        }
        return (!now.isBefore(s) && now.isBefore(e)) ? msg : "";
    }


    // =========================================================
    // 보상: 2% 이상 데미지 기여자 중 등확률 랜덤 지급
    //   40% → GP 0.5~1 (7000번대 무관)
    //   40% → 93번 상자 (지옥의유물상자)
    //   20% → 아이템 (7000번대 미소지자만)
    // =========================================================
    private String calcHellBossReward(String roomName, String bossStartDate, long maxHp, String preRewardType, String demonType) {
        boolean isGreatDemon = "대악마".equals(demonType);
        Random rand = new Random();

        // ── 전체 참여자 조회 (데미지% 계산용) ──
        List<HashMap<String, Object>> allContributors;
        try {
            HashMap<String, Object> q = new HashMap<>();
            q.put("bossStartDate", bossStartDate);
            allContributors = botS3Service.selectHellTop3Contributors(q);
        } catch (Exception e) { allContributors = new ArrayList<>(); }

        // 전체 데미지 합산
        long totScore = 0;
        for (HashMap<String, Object> row : allContributors)
            totScore += ((Number) row.get("SCORE")).longValue();

        // 룰렛 대상: 헬모드 몬스터 kill 이력 + proc_date > trunc(sysdate)-3
        // 보상 대상: 해당 보스 등장 이후 실제 공격한 유저로 한정
        List<String> allNames = new ArrayList<>();
        for (HashMap<String, Object> row : allContributors)
            allNames.add(row.get("USER_NAME").toString());

        // 당첨자 풀 및 인원 결정 (대악마/상급악마 동일 기준)
        List<String> qualifiedPool = new ArrayList<>(allNames);
        int participantCount = allNames.size();
        int winnerCount = participantCount >= 20 ? 6 : participantCount >= 15 ? 5 : participantCount >= 10 ? 4 : 3;

        StringBuilder msg = new StringBuilder();

        // 아이템 보상 더보기(？) 상세 블록
        StringBuilder itemDetailBlock = new StringBuilder();

        if (isGreatDemon) {
            // 대악마: 아이템 + GP + 플래티넘유물상자 전부 지급
            msg.append("[대악마] 이번 클리어 보상은 아이템 + GP + 플래티넘유물상자 입니다!").append(NL);

            // ── [1] 아이템 보상 ──
            msg.append(NL).append("▶ 아이템 보상").append(NL);
            if (!qualifiedPool.isEmpty()) {
                List<String> itemWinners = pickWinners(qualifiedPool, winnerCount, rand);

                List<Integer> allItems = new ArrayList<>();
                HashMap<Integer, String[]> itemInfoMap = new HashMap<>();
                try {
                    List<HashMap<String, Object>> rewardMeta = botNewService.selectHellRewardItemsWithOwnCount();
                    for (HashMap<String, Object> row : rewardMeta) {
                        int iid      = Integer.parseInt(row.get("ITEM_ID").toString());
                        String iName = Objects.toString(row.get("ITEM_NAME"), "아이템#" + iid);
                        String iDesc = Objects.toString(row.get("ITEM_DESC"), "");
                        itemInfoMap.put(iid, new String[]{iName, iDesc});
                        allItems.add(iid);
                    }
                } catch (Exception e) {
                    allItems = new ArrayList<>(getHellRewardItems());
                }
                if (allItems.isEmpty()) allItems = new ArrayList<>(getHellRewardItems());

                Map<String, List<Integer>> winnerItemIds    = new LinkedHashMap<>();
                Map<String, List<Integer>> winnerEnhItemIds = new LinkedHashMap<>();
                Map<String, List<String>>  winnerItemNames  = new LinkedHashMap<>();
                Map<String, List<String>>  winnerDisplays   = new LinkedHashMap<>();
                for (String winner : itemWinners) {
                    List<Integer> codes    = new ArrayList<>();
                    List<Integer> enhIds   = new ArrayList<>();
                    List<String>  names    = new ArrayList<>();
                    List<String>  displays = new ArrayList<>();
                    for (int i = 0; i < 2; i++) { // 대악마: 2개
                        int giveItemId = allItems.get(rand.nextInt(allItems.size()));
                        String[] info  = itemInfoMap.getOrDefault(giveItemId, new String[]{"아이템#" + giveItemId, ""});
                        String iName   = info[0];
                        String enhanceDesc = (giveItemId >= 7001 && giveItemId <= 7019) ? getBossItemEnhanceDesc(giveItemId) : "";
                        String descToShow  = !enhanceDesc.isEmpty() ? enhanceDesc : (info[1] != null && !info[1].isEmpty() ? info[1] : "");
                        String displayName = descToShow.isEmpty() ? iName : iName + "  (" + descToShow + ")";
                        boolean alreadyOwned = false;
                        int existQty = 0;
                        try {
                            List<Integer> owned = botNewService.selectInventoryItemsByIds(winner, roomName, Collections.singleton(giveItemId));
                            alreadyOwned = owned != null && !owned.isEmpty();
                            if (alreadyOwned) {
                                List<HashMap<String,Object>> qtyList = botNewService.selectBossHellItemTotalQty(winner);
                                if (qtyList != null) for (HashMap<String,Object> qr : qtyList) {
                                    if (giveItemId == MiniGameUtil.parseIntSafe(Objects.toString(qr.get("ITEM_ID"),"0")))
                                        existQty = MiniGameUtil.parseIntSafe(Objects.toString(qr.get("TOTAL_QTY"),"1"));
                                }
                            }
                        } catch (Exception ignore) {}
                        int assignCode = !alreadyOwned ? giveItemId : (existQty < MAX_BOSS_ENHANCE) ? -3 : -2;
                        codes.add(assignCode);
                        enhIds.add(assignCode == -3 ? giveItemId : 0);
                        names.add(iName);
                        displays.add(displayName);
                    }
                    winnerItemIds.put(winner, codes);
                    winnerEnhItemIds.put(winner, enhIds);
                    winnerItemNames.put(winner, names);
                    winnerDisplays.put(winner, displays);
                }

                for (int w = 0; w < itemWinners.size(); w++) {
                    String winner = itemWinners.get(w);
                    List<Integer> codes = winnerItemIds.get(winner);
                    List<String>  names = winnerItemNames.get(winner);
                    msg.append(w + 1).append("번 보상: ").append(winner).append(NL);
                    for (int i = 0; i < codes.size(); i++) {
                        int code = codes.get(i);
                        msg.append("  ").append(i + 1).append(") ").append(names.get(i));
                        if (code == -2) msg.append(" [이미보유 자동판매 + 1GP]");
                        if (code == -3) msg.append(" [강화!+1]");
                        msg.append(NL);
                    }
                }
                for (int w = 0; w < itemWinners.size(); w++) {
                    String winner = itemWinners.get(w);
                    List<String> displays = winnerDisplays.get(winner);
                    itemDetailBlock.append("？").append(w + 1).append("번 보상: ").append(winner).append(NL);
                    for (int i = 0; i < displays.size(); i++)
                        itemDetailBlock.append("  ").append(i + 1).append(") ").append(displays.get(i)).append(NL);
                }
                itemDetailBlock.append(NL);

                for (String winner : itemWinners) {
                    List<Integer> codes  = winnerItemIds.get(winner);
                    List<Integer> enhIds = winnerEnhItemIds.get(winner);
                    Map<Integer, Integer> newItemQty = new LinkedHashMap<>();
                    Map<Integer, Integer> enhItemQty = new LinkedHashMap<>();
                    int dupGpCount = 0;
                    for (int i = 0; i < codes.size(); i++) {
                        int itemId = codes.get(i);
                        if (itemId == -3) {
                            int eid = enhIds.get(i);
                            if (eid > 0) enhItemQty.merge(eid, 1, Integer::sum);
                        } else if (itemId < 0) {
                            dupGpCount++;
                        } else {
                            newItemQty.merge(itemId, 1, Integer::sum);
                        }
                    }
                    for (Map.Entry<Integer, Integer> e : newItemQty.entrySet()) {
                        try {
                            HashMap<String, Object> inv = new HashMap<>();
                            inv.put("userName", winner); inv.put("roomName", roomName);
                            inv.put("itemId",   e.getKey()); inv.put("qty", e.getValue());
                            inv.put("gainType", "BOSS_HELL");
                            botNewService.insertInventoryLogTx(inv);
                        } catch (Exception ex) { /* 지급 실패 무시 */ }
                    }
                    for (Map.Entry<Integer, Integer> e : enhItemQty.entrySet()) {
                        try {
                            HashMap<String, Object> inv = new HashMap<>();
                            inv.put("userName", winner); inv.put("roomName", roomName);
                            inv.put("itemId",   e.getKey()); inv.put("qty", e.getValue());
                            inv.put("gainType", "BOSS_HELL");
                            botNewService.insertInventoryLogTx(inv);
                        } catch (Exception ex) { /* 강화 지급 실패 무시 */ }
                    }
                    if (dupGpCount > 0) {
                        try {
                            HashMap<String, Object> gp = new HashMap<>();
                            gp.put("userName", winner); gp.put("roomName", roomName);
                            gp.put("score",    1.0 * dupGpCount); gp.put("cmd", "BOSS_HELL_DUP_ITEM_GP");
                            botNewService.insertGpRecord(gp);
                        } catch (Exception ex) { /* 지급 실패 무시 */ }
                    }
                }
            }

            // ── [2] GP 보상 ──
            msg.append(NL).append("▶ GP 보상").append(NL);
            if (!qualifiedPool.isEmpty()) {
                List<String> gpWinners = pickWinners(qualifiedPool, winnerCount, rand);
                Map<String, Double> gpAmtMap = new LinkedHashMap<>();
                for (String winner : gpWinners) {
                    double base = 0.5 + rand.nextInt(6) * 0.1;
                    gpAmtMap.put(winner, base * 2); // 대악마: 2배 (1.0~2.0GP)
                }
                for (int w = 0; w < gpWinners.size(); w++) {
                    String winner = gpWinners.get(w);
                    msg.append("✨").append(w + 1).append("번 보상: [").append(winner).append("] ").append(String.format("%.1f", gpAmtMap.get(winner))).append("GP").append(NL);
                }
                msg.append(NL);
                for (String winner : gpWinners) {
                    try {
                        HashMap<String, Object> gpMap = new HashMap<>();
                        gpMap.put("userName", winner); gpMap.put("roomName", roomName);
                        gpMap.put("score",    gpAmtMap.get(winner)); gpMap.put("cmd", "BOSS_HELL_KILL_GP");
                        botNewService.insertGpRecord(gpMap);
                    } catch (Exception ignore) {}
                }
            }

            // ── [3] 플래티넘유물상자 보상 ──
            msg.append("▶ 플래티넘유물상자 보상").append(NL);
            {
                List<String> boxWinners = pickWinners(qualifiedPool, winnerCount, rand);
                for (int w = 0; w < boxWinners.size(); w++) {
                    String winner = boxWinners.get(w);
                    msg.append("✨").append(w + 1).append("번 보상: [").append(winner).append("] 플래티넘유물상자 3개").append(NL);
                }
                msg.append(NL);
                for (String winner : boxWinners) {
                    try {
                        HashMap<String, Object> inv = new HashMap<>();
                        inv.put("userName", winner); inv.put("roomName", roomName);
                        inv.put("itemId",   93); inv.put("qty", 3);
                        inv.put("gainType", "DROP_OPEN_P");
                        botNewService.insertInventoryLogTx(inv);
                    } catch (Exception e) { /* 지급 실패 무시 */ }
                }
            }

        } else {
            // 상급악마: 단일 보상 (20% 아이템 / 40% 상자 / 40% GP)
            String resolvedType  = (preRewardType != null && !preRewardType.isEmpty()) ? preRewardType
                                 : (rand.nextDouble() >= 0.80 ? "ITEM" : rand.nextDouble() >= 0.40 ? "BOX" : "GP");
            boolean isItemReward = "ITEM".equals(resolvedType);
            boolean isBoxReward  = "BOX".equals(resolvedType);
            String rewardTypeName = isItemReward ? "아이템" : isBoxReward ? "지옥의유물상자" : "GP";
            msg.append("이번 클리어 보상은 ").append(rewardTypeName).append(" 입니다!").append(NL);

            if (isBoxReward) {
                List<String> boxWinners = pickWinners(qualifiedPool, winnerCount, rand);
                Map<String, Integer> boxQtyMap = new LinkedHashMap<>();
                for (String winner : boxWinners) {
                    int baseQty = 5 + rand.nextInt(6); // 5~10
                    boxQtyMap.put(winner, baseQty);
                }
                msg.append(NL);
                for (int w = 0; w < boxWinners.size(); w++) {
                    String winner = boxWinners.get(w);
                    msg.append("✨").append(w + 1).append("번 보상: [").append(winner).append("] 지옥의유물상자 ").append(boxQtyMap.get(winner)).append("개").append(NL);
                }
                msg.append(NL);
                for (String winner : boxWinners) {
                    try {
                        HashMap<String, Object> inv = new HashMap<>();
                        inv.put("userName", winner);
                        inv.put("roomName", roomName);
                        inv.put("itemId",   93);
                        inv.put("qty",      boxQtyMap.get(winner));
                        inv.put("gainType", "BOSS_HELL");
                        botNewService.insertInventoryLogTx(inv);
                    } catch (Exception e) { /* 지급 실패 무시 */ }
                }

            } else if (isItemReward) {
                if (qualifiedPool.isEmpty()) {
                    msg.append("★ 보상 대상 없음").append(NL);
                } else {
                    List<String> itemWinners = pickWinners(qualifiedPool, winnerCount, rand);

                    List<Integer> allItems = new ArrayList<>();
                    HashMap<Integer, String[]> itemInfoMap = new HashMap<>();
                    try {
                        List<HashMap<String, Object>> rewardMeta = botNewService.selectHellRewardItemsWithOwnCount();
                        for (HashMap<String, Object> row : rewardMeta) {
                            int iid      = Integer.parseInt(row.get("ITEM_ID").toString());
                            String iName = Objects.toString(row.get("ITEM_NAME"), "아이템#" + iid);
                            String iDesc = Objects.toString(row.get("ITEM_DESC"), "");
                            itemInfoMap.put(iid, new String[]{iName, iDesc});
                            allItems.add(iid);
                        }
                    } catch (Exception e) {
                        allItems = new ArrayList<>(getHellRewardItems());
                    }
                    if (allItems.isEmpty()) allItems = new ArrayList<>(getHellRewardItems());

                    Map<String, List<Integer>> winnerItemIds    = new LinkedHashMap<>();
                    Map<String, List<Integer>> winnerEnhItemIds = new LinkedHashMap<>();
                    Map<String, List<String>>  winnerItemNames  = new LinkedHashMap<>();
                    Map<String, List<String>>  winnerDisplays   = new LinkedHashMap<>();
                    for (String winner : itemWinners) {
                        List<Integer> codes    = new ArrayList<>();
                        List<Integer> enhIds   = new ArrayList<>();
                        List<String>  names    = new ArrayList<>();
                        List<String>  displays = new ArrayList<>();
                        int giveItemId = allItems.get(rand.nextInt(allItems.size()));
                        String[] info  = itemInfoMap.getOrDefault(giveItemId, new String[]{"아이템#" + giveItemId, ""});
                        String iName   = info[0];
                        String enhanceDesc = (giveItemId >= 7001 && giveItemId <= 7019) ? getBossItemEnhanceDesc(giveItemId) : "";
                        String descToShow  = !enhanceDesc.isEmpty() ? enhanceDesc : (info[1] != null && !info[1].isEmpty() ? info[1] : "");
                        String displayName = descToShow.isEmpty() ? iName : iName + "  (" + descToShow + ")";
                        boolean alreadyOwned = false;
                        int existQty = 0;
                        try {
                            List<Integer> owned = botNewService.selectInventoryItemsByIds(winner, roomName, Collections.singleton(giveItemId));
                            alreadyOwned = owned != null && !owned.isEmpty();
                            if (alreadyOwned) {
                                List<HashMap<String,Object>> qtyList = botNewService.selectBossHellItemTotalQty(winner);
                                if (qtyList != null) for (HashMap<String,Object> qr : qtyList) {
                                    if (giveItemId == MiniGameUtil.parseIntSafe(Objects.toString(qr.get("ITEM_ID"),"0")))
                                        existQty = MiniGameUtil.parseIntSafe(Objects.toString(qr.get("TOTAL_QTY"),"1"));
                                }
                            }
                        } catch (Exception ignore) {}
                        int assignCode = !alreadyOwned ? giveItemId : (existQty < MAX_BOSS_ENHANCE) ? -3 : -2;
                        codes.add(assignCode);
                        enhIds.add(assignCode == -3 ? giveItemId : 0);
                        names.add(iName);
                        displays.add(displayName);
                        winnerItemIds.put(winner, codes);
                        winnerEnhItemIds.put(winner, enhIds);
                        winnerItemNames.put(winner, names);
                        winnerDisplays.put(winner, displays);
                    }

                    msg.append(NL);
                    for (int w = 0; w < itemWinners.size(); w++) {
                        String winner = itemWinners.get(w);
                        List<Integer> codes = winnerItemIds.get(winner);
                        List<String>  names = winnerItemNames.get(winner);
                        int code = codes.get(0);
                        msg.append(w + 1).append("번 보상: ").append(names.get(0)).append(" : ").append(winner);
                        if (code == -2) msg.append(" [이미보유 자동판매 + 1GP]");
                        if (code == -3) msg.append(" [강화!+1]");
                        msg.append(NL);
                    }
                    msg.append(NL);
                    for (int w = 0; w < itemWinners.size(); w++) {
                        String winner = itemWinners.get(w);
                        List<String> displays = winnerDisplays.get(winner);
                        itemDetailBlock.append("？").append(w + 1).append("번 보상: ").append(displays.get(0)).append(NL);
                    }
                    itemDetailBlock.append(NL);

                    for (String winner : itemWinners) {
                        List<Integer> codes  = winnerItemIds.get(winner);
                        List<Integer> enhIds = winnerEnhItemIds.get(winner);
                        Map<Integer, Integer> newItemQty = new LinkedHashMap<>();
                        Map<Integer, Integer> enhItemQty = new LinkedHashMap<>();
                        int dupGpCount = 0;
                        for (int i = 0; i < codes.size(); i++) {
                            int itemId = codes.get(i);
                            if (itemId == -3) {
                                int eid = enhIds.get(i);
                                if (eid > 0) enhItemQty.merge(eid, 1, Integer::sum);
                            } else if (itemId < 0) {
                                dupGpCount++;
                            } else {
                                newItemQty.merge(itemId, 1, Integer::sum);
                            }
                        }
                        for (Map.Entry<Integer, Integer> e : newItemQty.entrySet()) {
                            try {
                                HashMap<String, Object> inv = new HashMap<>();
                                inv.put("userName", winner); inv.put("roomName", roomName);
                                inv.put("itemId",   e.getKey()); inv.put("qty", e.getValue());
                                inv.put("gainType", "BOSS_HELL");
                                botNewService.insertInventoryLogTx(inv);
                            } catch (Exception ex) { /* 지급 실패 무시 */ }
                        }
                        for (Map.Entry<Integer, Integer> e : enhItemQty.entrySet()) {
                            try {
                                HashMap<String, Object> inv = new HashMap<>();
                                inv.put("userName", winner); inv.put("roomName", roomName);
                                inv.put("itemId",   e.getKey()); inv.put("qty", e.getValue());
                                inv.put("gainType", "BOSS_HELL");
                                botNewService.insertInventoryLogTx(inv);
                            } catch (Exception ex) { /* 강화 지급 실패 무시 */ }
                        }
                        if (dupGpCount > 0) {
                            try {
                                HashMap<String, Object> gp = new HashMap<>();
                                gp.put("userName", winner); gp.put("roomName", roomName);
                                gp.put("score",    1.0 * dupGpCount); gp.put("cmd", "BOSS_HELL_DUP_ITEM_GP");
                                botNewService.insertGpRecord(gp);
                            } catch (Exception ex) { /* 지급 실패 무시 */ }
                        }
                    }
                }

            } else {
                // GP 지급
                if (qualifiedPool.isEmpty()) {
                    msg.append("GP 지급 대상 없음").append(NL);
                } else {
                    List<String> gpWinners = pickWinners(qualifiedPool, winnerCount, rand);
                    msg.append(NL);
                    Map<String, Double> gpAmtMap = new LinkedHashMap<>();
                    for (String winner : gpWinners) {
                        double base = 0.5 + rand.nextInt(6) * 0.1;
                        gpAmtMap.put(winner, base);
                    }
                    for (int w = 0; w < gpWinners.size(); w++) {
                        String winner = gpWinners.get(w);
                        msg.append("✨").append(w + 1).append("번 보상: [").append(winner).append("] ").append(String.format("%.1f", gpAmtMap.get(winner))).append("GP").append(NL);
                    }
                    msg.append(NL);
                    for (String winner : gpWinners) {
                        try {
                            HashMap<String, Object> gpMap = new HashMap<>();
                            gpMap.put("userName", winner); gpMap.put("roomName", roomName);
                            gpMap.put("score",    gpAmtMap.get(winner)); gpMap.put("cmd", "BOSS_HELL_KILL_GP");
                            botNewService.insertGpRecord(gpMap);
                        } catch (Exception ignore) {}
                    }
                }
            }
        }

        // 전체 기여도 TOP — 더보기(===) 이후에 표시
        if (!allContributors.isEmpty() || itemDetailBlock.length() > 0) {
            msg.append(NL).append(ALL_SEE_STR).append(NL);
            if (itemDetailBlock.length() > 0) msg.append(itemDetailBlock);
            if (!allContributors.isEmpty()) {
                msg.append("-- 전체 기여도 TOP --").append(NL);
                for (HashMap<String, Object> row : allContributors) {
                    long score = row.get("SCORE") instanceof Number ? ((Number) row.get("SCORE")).longValue() : 0L;
                    double dmgPct = totScore > 0 ? score * 100.0 / totScore : 0;
                    msg.append(row.get("USER_NAME"))
                       .append(" - ").append(row.get("CNT")).append("회 / ")
                       .append(String.format("%,d", score)).append("dmg")
                       .append(String.format(" (%.1f%%)", dmgPct)).append(NL);
                }
            }
        }

        // ── 전체 참가자 헬보스 클리어 업적 일괄 체크 ──────────────────────
        // END_YN='1' 커밋 후 실행되므로 selectHellBossClearCount가 정확한 횟수 반환
        // 킬러 포함 모든 참가자에게 즉시 업적 지급
        if (!allNames.isEmpty()) {
            StringBuilder achvSb = new StringBuilder();
            for (String cName : allNames) {
                try {
                    String am = grantHellBossAchievements(cName, roomName, false, bossStartDate);
                    if (am != null && !am.isEmpty()) {
                        achvSb.append(cName).append(": ").append(am);
                    }
                } catch (Exception ignore) {}
            }
            if (achvSb.length() > 0) {
                msg.append(NL).append("[ 업적 달성 ]").append(NL).append(achvSb);
            }
        }

        return msg.toString();
    }


    /**
     * 리스트에서 중복 없이 최대 count명 랜덤 추첨.
     * pool 크기가 count보다 작으면 pool 전체 반환.
     */
    private List<String> pickWinners(List<String> pool, int count, Random rand) {
        List<String> copy    = new ArrayList<>(pool);
        List<String> winners = new ArrayList<>();
        for (int i = 0; i < count && !copy.isEmpty(); i++) {
            int idx = rand.nextInt(copy.size());
            winners.add(copy.remove(idx));
        }
        return winners;
    }

    // =========================================================
    // 헬보스 출현 알림 (BossAttackController.ma_buildMessage 에서 호출)
    // =========================================================
    /**
     * 일반 몬스터 공격 메시지 하단에 붙는 헬보스 상태 한 줄 알림.
     * 이력/추첨 결과는 보스 직접 공격(doBossAttack) 에서만 표시 — 여기선 절대 포함하지 않음.
     */
    public String getHellBossStatusMsg() {
        try {
            HashMap<String, Object> boss = botS3Service.selectHellBoss();
            if (boss == null || boss.get("CUR_HP") == null) return "";
            String startDateStr = boss.get("START_DATE") != null ? boss.get("START_DATE").toString() : "";
            if (startDateStr.isEmpty()) return "";
            LocalDateTime spawnTime = LocalDateTime.parse(startDateStr,
                    DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"));
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(spawnTime)) {
                // 재정비 중: 남은 시간만 한 줄로
                long remainMin = java.time.Duration.between(now, spawnTime).toMinutes();
                String remainStr;
                if (remainMin >= 60) {
                    long h = remainMin / 60, m = remainMin % 60;
                    remainStr = h + "시간 " + m + "분";
                } else {
                    remainStr = remainMin + "분";
                }
                return "※상급악마 재정비 중 (" + remainStr + " 후 출현)" + NL;
            } else {
                // 출현 중: 체력% 한 줄로
                String statusBossType = boss.get("BOSS_TYPE") != null ? boss.get("BOSS_TYPE").toString() : "상급악마";
                double curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
                String curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
                long hp = SP.toBaseValue(SP.of(curHpNum, curHpExt));
                double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
                String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
                long maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));
                int pct = maxHp > 0 ? (int)Math.round(hp * 100.0 / maxHp) : 0;
                return "현재 " + statusBossType + " 출현! [체력 " + pct + "%]" + NL;
            }
        } catch (Exception e) {
            return "";
        }
    }

    // =========================================================
    // 보스 정보 조회: /헬보스정보
    // =========================================================
    public String bossInfo(HashMap<String, Object> map) {
        HashMap<String, Object> boss;
        try {
            boss = botS3Service.selectHellBoss();
        } catch (Exception e) {
            return "보스 정보 조회에 실패했습니다.";
        }
        if (boss == null || boss.get("CUR_HP") == null) {
            String lastReward = botS3Service.getLastKillRewardMsg();
            if (lastReward != null && !lastReward.isEmpty()) {
                return "현재 출현한 상급악마가 없습니다." + NL + NL + lastReward;
            }
            return "현재 출현한 상급악마가 없습니다.";
        }

        double curHpNum = Double.parseDouble(boss.get("CUR_HP").toString());
        String curHpExt = boss.get("CUR_HP_EXT") != null ? boss.get("CUR_HP_EXT").toString() : "";
        long hp = SP.toBaseValue(SP.of(curHpNum, curHpExt));

        double maxHpNum = Double.parseDouble(boss.get("MAX_HP").toString());
        String maxHpExt = boss.get("MAX_HP_EXT") != null ? boss.get("MAX_HP_EXT").toString() : "";
        long maxHp = SP.toBaseValue(SP.of(maxHpNum, maxHpExt));

        String startDate = boss.get("START_DATE") != null ? boss.get("START_DATE").toString() : "";
        double hpPct = maxHp > 0 ? (hp * 100.0) / maxHp : 0;

        List<HashMap<String, Object>> contributors = new ArrayList<>();
        if (!startDate.isEmpty()) {
            try {
                HashMap<String, Object> q = new HashMap<>();
                q.put("bossStartDate", startDate);
                contributors = botS3Service.selectHellTop3Contributors(q);
            } catch (Exception e) {
                // 조회 실패 무시
            }
        }

        StringBuilder msg = new StringBuilder();
        String bossType = boss.get("BOSS_TYPE") != null ? boss.get("BOSS_TYPE").toString() : "상급악마";
        msg.append("[ ").append(bossType).append(" 정보 ]").append(NL);
        msg.append("체력: ").append(SP.fromSp(hp)).append("/").append(SP.fromSp(maxHp))
           .append(" (").append(String.format("%.1f", hpPct)).append("%)").append(NL);

        if (!contributors.isEmpty()) {
            msg.append(NL).append("-- 참여자 기여도 TOP --").append(NL);
            for (HashMap<String, Object> row : contributors) {
                msg.append(row.get("USER_NAME"))
                   .append(" - ").append(row.get("CNT")).append("회 / 데미지 ").append(String.format("%,d", ((Number)row.get("SCORE")).longValue()))
                   .append(NL);
            }
        } else {
            msg.append("아직 참여자가 없습니다.").append(NL);
        }

        return msg.toString().trim();
    }

}
