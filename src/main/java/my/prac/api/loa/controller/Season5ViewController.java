package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.prjbot.dao.BotDAO;
import my.prac.core.prjbot.dao.BotS5DAO;
import my.prac.core.prjbot.service.BotS5Service;
import my.prac.core.prjbot.service.BotService;
import my.prac.core.util.PP;

/**
 * [시즌5] 탑 등반 SPA 뷰 컨트롤러
 * - JSP: GET /loa/tower-view
 * - REST API: GET /loa/api/tower-*
 *
 * 기존 LoaUnifiedViewController 패턴(JSP + /api/* JSON)을 그대로 계승.
 * 화면 조회는 BotS5DAO를 직접 사용하고(다른 view 컨트롤러들과 동일 방식),
 * 게임 진행에 영향을 주는 액션은 반드시 BotS5Service를 통해 채팅 명령어와
 * 동일한 로직으로 처리한다.
 */
@Controller
@RequestMapping("/loa")
public class Season5ViewController {

    @Resource(name = "core.prjbot.BotS5Service")
    BotS5Service s5Service;

    @Resource(name = "core.prjbot.BotS5DAO")
    BotS5DAO s5Dao;

    @Resource(name = "core.prjbot.BotService")
    BotService botService;

    @Resource(name = "core.prjbot.BotDAO")
    BotDAO botDao;

    // ─────────────────────────────────────────────
    // JSP 뷰 페이지
    // ─────────────────────────────────────────────
    /** [2026-10-26] "구버전에서 업데이트 안 되는 사람, 새로고침해도 신버전이 안 된다" -- 배포(앱 시작)마다 바뀌는 화면 빌드 번호.
     *  화면에 박아 두고(pageBuild), 화면이 /api/tower-page-build로 물어 다르면 새 주소(?_b=번호)로 다시 불러온다. */
    static final String PAGE_BUILD = Long.toString(System.currentTimeMillis(), 36);

    /** 화면 HTML은 브라우저/카톡 웹뷰가 저장해 두지 않게(항상 서버에 다시 묻기). */
    private static void noStore(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
    }

    @GetMapping("/tower-view")
    public String towerViewPage(HttpServletRequest req, HttpServletResponse resp) {
        noStore(resp);
        req.setAttribute("pageBuild", PAGE_BUILD);
        return "nonsession/loa/tower_view";
    }

    /** [2026-10-26] 지금 배포된 화면 빌드 번호 -- 화면이 자기 번호와 다르면 새로 불러온다. */
    @GetMapping("/api/tower-page-build")
    @ResponseBody
    public ResponseEntity<?> apiTowerPageBuild() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("build", PAGE_BUILD);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    /** [2026-09-22] "전체 web포함 전투로그를 유저별로 볼수있게 페이지 구성해줘" 요청 -- tower-view
     *  SPA와는 별도의 독립 페이지(URL/파일 둘 다 분리). 데이터는 /api/tower-battle-log. */
    @GetMapping("/tower-battle-log")
    public String battleLogViewPage(HttpSession session, HttpServletResponse resp) {
        if (!Season5KakaoLoginController.isLoggedIn(session)) return "redirect:/loa/s5/login?next=/loa/tower-battle-log";
        if (!isLogAdmin(session)) return "nonsession/loa/s5_denied"; // [2026-10-28] 관리자만
        noStore(resp); // [2026-10-26] 새로 배포한 화면이 바로 보이게
        return "nonsession/loa/s5_battle_log_view";
    }

    /** [2026-10-25] "캐릭터별 현재 위치, 장착 아이템들, 레벨, 보유 수량 보이는 페이지" 요청 -- 로그 뷰어처럼 카카오 로그인 필요. 데이터는 /api/tower-mine-users. */
    @GetMapping("/tower-mine-users")
    public String mineUsersViewPage(HttpSession session, HttpServletResponse resp) {
        if (!Season5KakaoLoginController.isLoggedIn(session)) return "redirect:/loa/s5/login?next=/loa/tower-mine-users";
        if (!isLogAdmin(session)) return "nonsession/loa/s5_denied"; // [2026-10-28] 관리자만
        noStore(resp);
        return "nonsession/loa/s5_mine_users_view";
    }

    /** [2026-10-25] 로그 뷰어 -- 광산 로그 옆 캐릭터 그림용 외형 {유저명: look}(카카오 로그인 필요). */
    @GetMapping("/api/tower-mine-looks")
    @ResponseBody
    public ResponseEntity<?> apiMineLooks(HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        HashMap<String, Object> result = new HashMap<>();
        result.put("looks", s5Service.mineLooks());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-mine-users")
    @ResponseBody
    public ResponseEntity<?> apiMineUsers(HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        HashMap<String, Object> result = new HashMap<>();
        result.put("users", s5Service.mineUserOverview());
        result.put("now", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    // [2026-10-24] "밸런스 화면은 없애줘" -- /tower-balance-stats 페이지와 API를 지웠다(전투 결과 수집 TBOT_S5_BATTLE_STAT 쓰기는 그대로).

    // ─────────────────────────────────────────────
    // REST API
    // ─────────────────────────────────────────────

    @GetMapping("/api/tower-status")
    @ResponseBody
    public ResponseEntity<?> apiTowerStatus(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        HashMap<String, Object> progress = s5Service.selectUserProgress(userName);
        if (progress == null) {
            // [버그 수정] 정확히 일치하는 계정이 없다고 바로 새 계정을 만들면, "타락고냥이/바드"를
            // 찾으려다 "타락고냥이"만 입력해도 그 이름 그대로 엉뚱한 빈 계정이 새로 생겨버렸다
            // (신고로 확인, DB에서 여러 번 수동 삭제했다고 함). /탑현황 채팅 명령어와 동일하게
            // 먼저 부분 일치(앞부분 LIKE) 검색으로 실제 존재하는 계정인지 확인하고, 그것도 없을
            // 때만 진짜 신규 유저로 보고 생성한다.
            String resolved = s5Service.resolveUserName(userName);
            if (resolved != null) {
                userName = resolved;
                progress = s5Service.selectUserProgress(userName);
            }
            if (progress == null) {
                s5Service.initUser(userName);
                progress = s5Service.selectUserProgress(userName);
            }
        }
        result.put("progress", progress);

        // [2026-09-17] "전투 화면을 포켓몬 배틀 화면처럼" 요청 -- 전투 중인 몬스터의 이름을
        // 보여주려면 최소한 이름 한 글자는 필요한데, progress row(CUR_MONSTER_HP_VALUE 등)엔
        // 몬스터 이름이 없다. 최대HP는 몬스터마다 하드코어 스케일링 등으로 달라질 수 있어
        // 클라이언트가 "이 몬스터를 처음 본 순간의 HP"를 자체적으로 100% 기준선으로 잡아
        // 쓰므로 여기선 이름만 추가로 내려준다(전투 진행/판정 로직은 전혀 건드리지 않음).
        // [2026-09-17 버그수정] "전투화면 몬스터 이름/텍스트 전투로그가 서로 다르다" 신고 --
        // 처음엔 여기서 TBOT_S5_MONSTER_INFO.MONSTER_NAME을 직접 읽었는데, 사냥터(비보스)
        // 몬스터는 실제 전투 로그가 그 DB 이름을 안 쓰고 층 위치 기준 순환 이름 목록
        // (BotS5ServiceImpl.FLOOR_MONSTER_NAME/floorMonsterName)을 쓴다는 걸 놓쳤다 --
        // DB 이름을 그대로 내려주다 보니 실제로는 "어둠 숲도둑 고블린"과 싸우고 있는데
        // 화면엔 그 블록 DB상 이름("뒤틀린 차원 촉수괴" 등, 보스 이름 대역과 겹쳐 있던 값)이
        // 뜨는 불일치가 났다. 전투 로그와 항상 같은 이름이 나오도록 그 로직을 그대로 쓰는
        // s5Service.currentFloorMonsterName()으로 교체(보스는 원래도 DB 이름 그대로라 결과 동일).
        if ("IN_COMBAT".equals(String.valueOf(progress.get("STATUS")))) {
            int floorNow = toInt(progress.get("CUR_FLOOR"));
            String monsterName = s5Service.currentFloorMonsterName(floorNow);
            if (monsterName != null) result.put("monsterName", monsterName);
            // [2026-09-21] 전투화면 재설계(스탯표 POWER/GUARD) -- 몬스터 ATK/DEF도 함께 전달.
            int[] atkDef = s5Service.currentFloorMonsterAtkDef(floorNow);
            if (atkDef != null) {
                result.put("monsterAtk", atkDef[0]);
                result.put("monsterDef", atkDef[1]);
            }
        }

        int floor = toInt(progress.get("CUR_FLOOR"));
        // [2026-09-28] 101층+ 계단 구역은 X0/X9층도 전부 9칸 전투층이라 층번호 끝자리와 무관하게 보드를 내려준다.
        if ((floor % 10 >= 1 && floor % 10 <= 8) || isStairZoneFloor(floor)) {
            result.put("floorInfo", s5Dao.selectFloorInfo(floor));
            result.put("tiles", buildTilesWithFogOfWar(userName, floor));
            result.put("myTile", s5Dao.selectUserFloorProgress(userName, floor));
        }
        if ("Y".equals(String.valueOf(progress.get("AUTO_HUNT_YN")))) {
            result.put("autoHunt", buildAutoHuntInfo(userName));
        }
        // [2026-09-30] "최근 전투했던 층으로 바로가기" -- TBOT_S5_AUTO_HUNT_LOG.FLOOR는 자동사냥 ON/OFF와 무관하게
        // "가장 최근에 실제로 몬스터를 처치한 층"으로 갱신되므로(BotS5ServiceImpl 처치 시 upsertAutoHuntLog) 그 값을 그대로 쓴다.
        HashMap<String, Object> huntLog = s5Dao.selectAutoHuntLog(userName);
        if (huntLog != null && huntLog.get("FLOOR") != null) result.put("lastBattleFloor", toInt(huntLog.get("FLOOR")));
        // 웹 SPA "층이동 탑 그림"에서 각 층(1~8, 사냥터만)의 탐사완료 여부를 보여주기 위함
        // [2026-10-23] 100층 이상(마을/계단 구역)은 화면이 이 값을 안 쓰므로(renderStairZoneNav) 조회하지 않는다.
        // 99층 이하도 굴릴 때마다 상태 조회로 같이 돌던 쿼리라 공유 캐시(60초, 탐사 기록이 바뀌면 즉시 무효)로 재사용한다.
        if (floor < 100) {
            int blockBase = floor - (floor % 10);
            result.put("floorBest", s5Dao.selectUserFloorBestRange(userName, blockBase + 1, blockBase + 8));
        }
        // [2026-09-05] 주사위 교체 위젯을 상점탭에서 '탑' 탭 보드 칸그리드 위 오버레이로
        // 옮기면서, 상태 조회(가장 자주 도는 호출)에 얹어 매번 최신 해금/사용중 정보를 준다.
        result.put("dice", s5Service.diceListInfo(userName));
        result.put("diceEnhance", s5Service.diceEnhanceInfo(userName));
        // [2026-09-21] "이동한도도 맵이동하는곳에 표기하면 좋을거같아" 요청.
        result.put("moveLimit", s5Service.moveLimitInfo(userName));
        result.put("mineOpen", s5Service.mineAccess(userName)); // [2026-10-23] 광산/광산상점 탭 노출 여부(config)
        result.put("stairZoneMax", s5Service.stairZoneMaxFloor()); // [2026-10-01] 계단 구역 오픈 상한(200 -> 300 오픈 준비)
        return ResponseEntity.ok(result);
    }

    /** 자동사냥 화면 표시용: 몇 층 기준으로 정산되는지 + 시간당 PP + 지금까지 누적 경과시간(분). */
    private HashMap<String, Object> buildAutoHuntInfo(String userName) {
        HashMap<String, Object> log = s5Dao.selectAutoHuntLog(userName);
        if (log == null) return null;

        HashMap<String, Object> info = new HashMap<>();
        int floor = toInt(log.get("FLOOR"));
        info.put("floor", floor);

        // [2026-09-28] 100층 이상은 블록 행이 없어서 블록10 행을 기준으로(BotS5ServiceImpl.monsterBlockNo와 동일).
        int blockNo = Math.min((floor / 10) + 1, 10);
        HashMap<String, Object> mon = s5Dao.selectMonster(blockNo, "N");
        if (mon != null) {
            info.put("monsterName", mon.get("MONSTER_NAME"));
            Object extObj = mon.get("PP_PER_KILL_EXT");
            PP perKill = PP.of(((Number) mon.get("PP_PER_KILL_VALUE")).doubleValue(), extObj == null ? "" : extObj.toString());
            int pos = floor % 10;
            double floorMult = isStairZoneFloor(floor) ? (1.7 + 0.01 * (floor - (STAIR_ZONE_START - 1)))
                    : ((pos < 1 || pos > 8) ? 1.0 : (1.0 + 0.1 * (pos - 1))); // BotS5ServiceImpl.floorPpMultiplier와 동일 공식
            // [2026-09-17] BotS5ServiceImpl.autoHuntFloorBonusMultiplier와 동일 공식(60층 미만
            // x5, 60~69층 x3, 그 외 x1) -- "저층 자동사냥 보상 늘려달라" 요청, 실제 정산 배율과
            // 이 미리보기가 따로 놀지 않도록 반드시 같이 맞춰야 함.
            double lowFloorBonus = floor < 60 ? 5.0 : (floor < 70 ? 3.0 : 1.0);
            // [2026-09-05 버그 수정] 하드코딩 6이 AUTO_HUNT_KILLS_PER_HOUR config화 때 여기는
            // 안 고쳐져서 실제 정산 속도와 이 예상치가 따로 놀 뻔했음 -- 같은 값을 쓰도록 통일.
            info.put("ppPerHourFormatted", perKill.multiply(s5Service.autoHuntKillsPerHour() * floorMult * lowFloorBonus).format());
        }

        // 정산 대기 중인(=아직 PP로 못 받은) 시간만 보여줘야 하므로 START_DATE(자동사냥이 최초 켜진 시점,
        // 계속 플레이해도 안 바뀜)가 아니라 LAST_SETTLE_DATE(가장 최근 정산 시점) 기준으로 계산한다.
        // 그렇지 않으면 한창 접속해서 플레이 중이어도 자동사냥을 켠 이후 누적 시간이 계속 불어나 보여
        // "지금 하고 있는데 왜 15시간 경과라고 뜨지?" 같은 혼란이 생긴다.
        Object lastSettleObj = log.get("LAST_SETTLE_DATE");
        Object startObj = log.get("START_DATE");
        Object baseObj = lastSettleObj != null ? lastSettleObj : startObj;
        if (baseObj instanceof java.util.Date) {
            long elapsedMs = System.currentTimeMillis() - ((java.util.Date) baseObj).getTime();
            info.put("elapsedMinutes", Math.max(0, elapsedMs / 60000));
        }
        return info;
    }

    @GetMapping("/api/tower-party")
    @ResponseBody
    public ResponseEntity<?> apiTowerParty(@RequestParam(value = "userName", defaultValue = "") String userName) {
        // [2026-09-14] "전체보기 카드마다 공/방/체 + 한계돌파 보너스를 다 보여달라" 요청 --
        // selectUserCompanions 직접 호출 대신 EFF_HP/EFF_ATK/EFF_DEF(+ _BASE) 필드까지 얹어
        // 돌려주는 companionsWithEffectiveStats()로 교체(N+1 없이 한 번에 계산).
        List<HashMap<String, Object>> companions = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Service.companionsWithEffectiveStats(userName);
        HashMap<String, Object> result = new HashMap<>();
        result.put("companions", companions);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-equip")
    @ResponseBody
    public ResponseEntity<?> apiTowerEquip(@RequestParam(value = "userName", defaultValue = "") String userName) {
        List<HashMap<String, Object>> equips = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserEquip(userName);
        // [2026-10-22] 인벤토리 탭 -- 장비마다 옵션 문구(능력치 보너스)를 같이 내려준다.
        for (HashMap<String, Object> e : equips) {
            e.put("OPTION_TEXT", s5Service.equipOptionText(String.valueOf(e.get("PART")), toInt(e.get("GRADE"))));
        }
        HashMap<String, Object> result = new HashMap<>();
        result.put("equips", equips);
        if (!userName.trim().isEmpty()) result.put("tickets", s5Service.enhanceTickets(userName)); // [2026-10-22] 강화권(전설강화 창, 인벤토리 기타 탭)
        return ResponseEntity.ok(result);
    }

    /** [2026-09-21] 전설제작 UI(상점 탭)용 -- 제작 가능한 ★7 전설장비 로스터. 유저 무관 공용 목록. */
    @GetMapping("/api/tower-legendary-roster")
    @ResponseBody
    public ResponseEntity<?> apiTowerLegendaryRoster() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("roster", s5Service.legendaryRoster());
        return ResponseEntity.ok(result);
    }

    /**
     * 웹 SPA 업데이트 공지/새로고침 유도(2026-09-07) -- userName은 선택(로그인 여부와 무관하게
     * 공지 자체는 항상 공개). userName이 있으면 그 유저가 이미 "다시 보지 않기"로 닫은 버전인지
     * dismissed에 담아준다(2026-09-08, "공지가 매번 뜬다" 신고 -- 웹뷰가 localStorage를 못
     * 지키는 환경이 있어서 서버에 유저별로 남기도록 변경). 페이지가 주기적으로 이 값을
     * 조회하다가 version이 바뀐 걸 감지하면 새로고침 안내 팝업을 띄운다. 관리자가 /공지등록으로
     * 값을 갱신한다.
     */
    @GetMapping("/api/tower-notice")
    @ResponseBody
    public ResponseEntity<?> apiTowerNotice(
            @RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> notice = s5Service.getNotice(userName);
        HashMap<String, Object> result = new HashMap<>();
        result.put("version", notice.get("APP_VERSION"));
        result.put("notice", notice.get("NOTICE_TEXT"));
        result.put("dismissed", notice.get("DISMISSED"));
        return ResponseEntity.ok(result);
    }

    /**
     * 웹 SPA 전용: 공지 모달의 "다시 보지 않기" 버튼 -- 현재 공지 버전을 이 유저가 봤다고
     * 서버에 남긴다(2026-09-08). 관리자가 새 공지를 등록하기 전까지는 같은 유저에게 다시
     * 뜨지 않는다.
     */
    @GetMapping("/api/tower-notice-dismiss")
    @ResponseBody
    public ResponseEntity<?> apiTowerNoticeDismiss(
            @RequestParam(value = "userName", defaultValue = "") String userName) {
        s5Service.dismissNotice(userName);
        HashMap<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return ResponseEntity.ok(result);
    }

    /** 캐릭터 클릭(확대) 상세 카드용 — 장비/스탯구매 보너스까지 반영한 유효 스탯. 장비 목록은
     *  이미 /api/tower-equip으로 받아둔 데이터를 화면에서 COMPANION_ID로 필터링해서 재사용한다. */
    @GetMapping("/api/tower-companion-stat")
    @ResponseBody
    public ResponseEntity<?> apiTowerCompanionStat(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "companionId", defaultValue = "0") int companionId) {
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        int[] eff = s5Service.companionEffectiveStat(userName, companionId);
        result.put("hp", eff[0]);
        result.put("atk", eff[1]);
        result.put("def", eff[2]);
        // [2026-09-14] "한계돌파0성스탯(+돌파로인한스탯)처럼 표기해달라" 요청 -- 한계돌파 배율만
        // 뺀 base 값도 같이 내려서, 화면에서 hp-hpBase 형태로 보너스분만 따로 보여줄 수 있게 함.
        result.put("hpBase", eff[3]);
        result.put("atkBase", eff[4]);
        result.put("defBase", eff[5]);
        return ResponseEntity.ok(result);
    }

    /** [2026-09-14] "핸드폰에서 이미지가 잘 안 뜬다" 근본 대응 -- 동료 초상화를 원본(장당
     *  1.7~3.2MB) 대신 서버가 160x160로 축소/캐싱해둔 JPEG로 서빙한다
     *  (BotS5ServiceImpl.getCompanionAvatarThumbnail 참고). 캐시 실패/원본 없음이면 404 --
     *  프론트(buildAvatarEl의 img.onerror)가 이미 이모지 폴백으로 자연스럽게 처리한다.
     *  브라우저 캐시도 7일(퍼블릭) 걸어서 같은 유저가 재접속해도 다시 안 받아오게 한다. */
    @GetMapping("/api/tower-avatar")
    public ResponseEntity<byte[]> apiTowerAvatar(
            @RequestParam(value = "companionId", defaultValue = "0") int companionId) {
        byte[] thumb = companionId > 0 ? s5Service.getCompanionAvatarThumbnail(companionId) : null;
        if (thumb == null) return new ResponseEntity<byte[]>(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(thumb);
    }

    @GetMapping("/api/tower-shop")
    @ResponseBody
    public ResponseEntity<?> apiTowerShop(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        int unlocked = 0;
        if (!userName.trim().isEmpty()) {
            HashMap<String, Object> progress = s5Service.selectUserProgress(userName);
            if (progress != null) unlocked = toInt(progress.get("UNLOCKED_BLOCK"));
            result.put("ppValue", progress == null ? 0 : progress.get("PP_VALUE"));
            result.put("ppExt", progress == null ? "" : progress.get("PP_EXT"));
            result.put("freeCompanionPullsLeft", s5Service.freeCompanionPullsLeft(userName));
            // [정책 변경] 등급 무관 범용 뽑기권(COMPANION_VOUCHER/EQUIP_VOUCHER)은 "하급 권으로
            // 최상급까지 뚫린다"는 문제로 폐지 -- 이제 항상 티어락 권만 쓴다(기존 잔량은 T1로
            // 일괄 이관, S5_VOUCHER_TIER_MIGRATION.sql). 아래 티어락 배열만 내려주면 충분.
            // 티어락 뽑기권(N층 완전탐사 보상) -- 인덱스 0=티어1(하급)...3=티어4(최상급), COMPANION_GACHA_ID와 매칭
            result.put("companionVoucherByTier", new int[]{
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T1")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T2")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T3")),
                    progress == null ? 0 : toInt(progress.get("COMPANION_VOUCHER_T4")),
            });
            // 장비도 동료와 동일하게 등급별 티어락 권이 생겼으므로(/이벤트지급 등급지급) 같은 방식으로 노출
            result.put("equipVoucherByTier", new int[]{
                    progress == null ? 0 : toInt(progress.get("EQUIP_VOUCHER_T1")),
                    progress == null ? 0 : toInt(progress.get("EQUIP_VOUCHER_T2")),
                    progress == null ? 0 : toInt(progress.get("EQUIP_VOUCHER_T3")),
                    progress == null ? 0 : toInt(progress.get("EQUIP_VOUCHER_T4")),
            });
            // [2026-09-15] "/이벤트지급으로 악세뽑기권도 지급할 수 있게" 요청으로 악세서리도
            // 동료/장비와 동일하게 등급별 티어락 권 노출.
            result.put("accessoryVoucherByTier", new int[]{
                    progress == null ? 0 : toInt(progress.get("ACCESSORY_VOUCHER_T1")),
                    progress == null ? 0 : toInt(progress.get("ACCESSORY_VOUCHER_T2")),
                    progress == null ? 0 : toInt(progress.get("ACCESSORY_VOUCHER_T3")),
                    progress == null ? 0 : toInt(progress.get("ACCESSORY_VOUCHER_T4")),
            });
        }
        result.put("unlockedBlock", unlocked);
        // [UX 개선] 예전엔 해금된 것만 내려줘서 미해금 등급은 무료뽑기권이 있어도 화면에 아예 안
        // 보였음("무료뽑기권 있으면 나오게 해달라" 요청) -- 이제 항상 4개 전체(선택권/등급 무관하게
        // UNLOCK_FLOOR 순, "999"는 사실상 무제한)를 내려주고, 그 안의 UNLOCK_FLOOR/등급별 권
        // 수량을 클라이언트가 비교해서 "해금됨" 또는 "미해금이지만 무료뽑기권 있음"인 것만 그리도록
        // 한다. 배열 인덱스(0부터)가 그대로 표시번호(1부터)이자 서버가 쓰는 등급(tier)이므로,
        // 앞부분만 잘라내면 이 번호 체계가 깨진다 -- 반드시 4개 전체를 순서 그대로 내려야 함.
        result.put("companionGacha", s5Dao.selectGachaList("COMPANION", 999));
        result.put("equipGacha", s5Dao.selectGachaList("EQUIP", 999));
        // [2026-09-15] 악세서리(목걸이/반지/팔찌) 전용 뽑기 신설.
        result.put("accessoryGacha", s5Dao.selectGachaList("ACCESSORY", 999));
        if (!userName.trim().isEmpty()) {
            result.put("stat", s5Service.statShopInfo(userName));
            result.put("dice", s5Service.diceListInfo(userName));
            result.put("diceEnhance", s5Service.diceEnhanceInfo(userName));
        }
        return ResponseEntity.ok(result);
    }

    /** "지나간 이전 메시지도 유저별로 볼 수 있게" 요청 -- 시즌5 전용 로그를 새로 만들지 않고,
     *  채팅 명령어(층변경/주사위/뽑기 등)가 원래 남기고 있던 TBOT_WORD_HIS(요청/응답)를 최신순
     *  10개까지 그대로 재사용해서 보여준다. */
    @GetMapping("/api/tower-messages")
    @ResponseBody
    public ResponseEntity<?> apiTowerMessages(@RequestParam(value = "userName", defaultValue = "") String userName) {
        List<HashMap<String, Object>> messages = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserRecentMessages(userName, 10);
        HashMap<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        return ResponseEntity.ok(result);
    }

    /** [2026-09-22] /tower-battle-log 전용 페이지네이션 API -- 채팅/웹 구분 없이 그 유저의
     *  TBOT_WORD_HIS 전체를 최신순으로 페이지 단위로 내려준다(keyword가 있으면 REQ/RES
     *  부분일치 검색). apiTowerMessages(최신 10개 고정)와 달리 과거 로그까지 전부 훑어볼 수
     *  있어야 해서 별도 엔드포인트로 분리. */
    @GetMapping("/api/tower-battle-log")
    @ResponseBody
    public ResponseEntity<?> apiTowerBattleLog(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize, HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 50;
        if (pageSize > 200) pageSize = 200; // 한 페이지 과다 조회 방지(NCLOB 변환 비용)

        HashMap<String, Object> params = new HashMap<>();
        params.put("userName", userName);
        params.put("keyword", keyword.trim());
        params.put("startRow", (page - 1) * pageSize);
        params.put("endRow", page * pageSize);

        int total = botDao.countWordHisByUser(params);
        List<HashMap<String, Object>> items = total > (page - 1) * pageSize
                ? botDao.selectWordHisByUserPaged(params) : new ArrayList<>();

        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", (int) Math.ceil(total / (double) pageSize));
        return ResponseEntity.ok(result);
    }

    /** [2026-09-22] /tower-battle-log 유저 콤보박스용 -- S5 전체 유저 목록("전체" 옵션은
     *  프론트에서 별도로 얹음, 여긴 실제 유저명만). */
    @GetMapping("/api/tower-battle-log-users")
    @ResponseBody
    public ResponseEntity<?> apiTowerBattleLogUsers(HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        HashMap<String, Object> result = new HashMap<>();
        result.put("userNames", s5Dao.selectAllS5UserNames());
        return ResponseEntity.ok(result);
    }

    /** [2026-09-22] "/로그 입력하면 랜딩, 전체유저 디폴트, 최근24시간, 5분/15분/60분/3시간/
     *  24시간은 DB 재조회 없이 필터링으로" 요청 -- userName이 비어있으면 전체 유저, hours(기본
     *  24)까지의 로그를 한 번에 내려준다. 이후 시간대 버튼은 프론트가 이 결과 안에서 JS로만
     *  필터링(추가 API 호출 없음). */
    @GetMapping("/api/tower-battle-log-recent")
    @ResponseBody
    public ResponseEntity<?> apiTowerBattleLogRecent(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "hours", defaultValue = "24") int hours,
            @RequestParam(value = "cat", defaultValue = "all") String cat, HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        if (hours < 1) hours = 1;
        if (hours > 24) hours = 24; // 이 페이지가 보장하는 최대 범위(버튼 중 가장 넓은 게 24시간)

        HashMap<String, Object> params = new HashMap<>();
        params.put("userName", userName.trim());
        params.put("hours", hours);
        params.put("cat", "mine".equals(cat) || "tower".equals(cat) ? cat : "all"); // [2026-10-24] 탑/광산 로그 분리
        params.put("maxRows", 1000); // 소규모 유저(21명) 기준 24시간 트래픽 상한 -- 안전장치

        List<HashMap<String, Object>> items = botDao.selectWordHisRecent(params);

        HashMap<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("hours", hours);
        result.put("capped", items.size() >= 1000);
        // [2026-10-28] 광산 접속 기록 -- 지금 플레이 중인 세션의 아직 안 쓴 부분(뷰어가 같은 sid의 DB 줄과 합쳐 "플레이 중" 카드로)
        if (!"tower".equals(params.get("cat"))) result.put("live", my.prac.core.prjbot.service.impl.S5MineSessionLog.liveRows(userName.trim()));
        return ResponseEntity.ok(result);
    }

    // [2026-10-01] /로그 뷰어는 시즌5 전용 테이블(TBOT_S5_WORD_HIS)만 조회한다(기존 TBOT_WORD_HIS는 게임 정보가 아니라 제외).

    /** [2026-09-30] "명령어를 최근에 사용한 사람만 따로, 사람당 몇 회(최근 1시간/24시간/3일)" 요청 --
     *  로그 본문 없이 유저별 사용 횟수만 집계해서 내려준다(구간 전환은 프론트가 이 결과로 처리). */
    @GetMapping("/api/tower-battle-log-summary")
    @ResponseBody
    public ResponseEntity<?> apiTowerBattleLogSummary(HttpSession session) {
        if (!isLogAdmin(session)) return logDenied(session);
        HashMap<String, Object> result = new HashMap<>();
        result.put("users", botDao.selectWordHisUserSummary(new HashMap<String, Object>()));
        return ResponseEntity.ok(result);
    }

    /** 로그 관련 API 공통: 카카오 로그인 안 됐을 때 응답(프론트는 401을 보면 로그인 화면으로 보낸다). */
    private static ResponseEntity<?> unauthorized() {
        HashMap<String, Object> r = new HashMap<>();
        r.put("error", "카카오 로그인이 필요합니다.");
        r.put("login", "/loa/s5/login");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(r);
    }

    // [2026-10-28] "로그 뷰는 관리자(전태환)만 볼 수 있도록" -- 로그 뷰어/광산 캐릭터 현황 페이지와 그 API는 카카오 로그인 + 관리자만.
    //  관리자 = TBOT_S5_CONFIG.LOG_ADMIN_KAKAO_IDS(카카오 회원번호, 쉼표/공백 구분)에 있는 계정. 이 값이 비어 있으면 카카오 닉네임이
    //  LOG_ADMIN_NICK인 계정(닉네임은 누구나 바꿀 수 있어 임시 -- 회원번호를 넣어 두면 닉네임은 보지 않는다). 설정값은 1분 기억.
    private static final String LOG_ADMIN_KEY = "LOG_ADMIN_KAKAO_IDS";
    private static final String LOG_ADMIN_NICK = "전태환";
    private volatile String logAdminIds = null;
    private volatile long logAdminAt = 0;

    @SuppressWarnings("unchecked")
    private boolean isLogAdmin(HttpSession session) {
        if (!Season5KakaoLoginController.isLoggedIn(session)) return false;
        Object o = session.getAttribute(Season5KakaoLoginController.SESSION_KEY);
        if (!(o instanceof java.util.Map)) return false;
        java.util.Map<String, String> me = (java.util.Map<String, String>) o;
        long now = System.currentTimeMillis();
        if (logAdminIds == null || now - logAdminAt > 60_000L) {
            String v = null;
            try { v = s5Dao.selectConfigValue(LOG_ADMIN_KEY); } catch (Exception e) { /* 못 읽으면 닉네임 기준 */ }
            logAdminIds = v == null ? "" : v.trim();
            logAdminAt = now;
        }
        String id = String.valueOf(me.get("kakaoId"));
        if (!logAdminIds.isEmpty()) {
            for (String a : logAdminIds.split("[,|\\s]+")) if (a.equals(id)) return true;
            return false;
        }
        return LOG_ADMIN_NICK.equals(me.get("nickname"));
    }

    /** 로그인 안 했으면 401(로그인 화면으로), 로그인했지만 관리자가 아니면 403. */
    private ResponseEntity<?> logDenied(HttpSession session) {
        if (!Season5KakaoLoginController.isLoggedIn(session)) return unauthorized();
        HashMap<String, Object> r = new HashMap<>();
        r.put("error", "관리자만 볼 수 있어요.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
    }

    /** [2026-10-08] 웹 도감 탭 -- 몬스터도감(층별 몬스터 스펙). 예전 /로그의 "몬스터 정보" 페이지(카카오 로그인 필요)를 웹 UI로 옮겼고 누구나 볼 수 있다. */
    @GetMapping("/api/tower-codex-monsters")
    @ResponseBody
    public ResponseEntity<?> apiTowerCodexMonsters() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("rows", s5Service.monsterCodex());
        return ResponseEntity.ok(result);
    }

    /** [2026-10-20] 성장형 동료 팝업용 -- 현황(탐사 진행, 배율, 직업별 예상 능력치). 없으면 자동 생성. */
    @GetMapping("/api/tower-growth")
    @ResponseBody
    public ResponseEntity<?> apiTowerGrowth(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        return ResponseEntity.ok(s5Service.growthStatus(userName));
    }

    /** [2026-10-23] 웹 광산 -- 공용 맵 지형/포탈(모든 유저 같음, 화면이 version으로 브라우저에 저장). 광석 등급은 보내지 않는다. */
    @GetMapping("/api/tower-mine-map")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineMap(@RequestParam(value = "mapId", defaultValue = "field") String mapId) {
        return ResponseEntity.ok(s5Service.mineMap(mapId));
    }

    /** [2026-10-23] "광산 접속/채굴/종료/광산상점 구매를 WEB으로 TBOT_S5_WORD_HIS에 쌓아줘" 요청 -- 시각은 INSERT_DATE.
     *  입장: "[웹] 광산 입장" / 맵 (x, y), 채굴: "[웹] 광산 채굴 (x, y)" / 결과 문구(광석 종류 포함), 퇴장: "[웹] 광산 퇴장" / 맵 (x, y),
     *  구매: "[웹] 광산상점 구매 N번 xM" / 결과 문구. 이동 중 위치 저장(몇 초마다)과 실패한 시도는 남기지 않는다. 로그 실패는 무시. */
    private void mineLog(String userName, String req, String res) {
        mineLog(userName, req, res, null);
    }

    /** [2026-10-28] "한 판(입장 -> 한 일 -> 퇴장)을 묶어서" + 서버 부하 -- 광산 화면의 행동은 메모리의 접속(세션)에 모았다가 몇 줄로 묶어 쓴다
     *  (S5MineSessionLog: 퇴장/30초 무응답/2분마다/서버 종료 때). 세션이 없는 광산상점 구매(탑 화면)만 예전처럼 바로 한 줄. out = 서비스 결과(처치 경험치/레벨). */
    private void mineLog(String userName, String req, String res, java.util.Map<String, Object> out) {
        try {
            if (my.prac.core.prjbot.service.impl.S5MineSessionLog.action(userName, req, res, out)) return;
            HashMap<String, Object> logMap = new HashMap<>();
            logMap.put("userName", userName);
            logMap.put("roomName", "MINE"); // [2026-10-24] 로그 뷰어에서 탑(WEB/채팅)과 광산 로그를 나눠 보도록
            logMap.put("req", req);
            logMap.put("res", res);
            botService.insertS5WordHisTx(logMap);
        } catch (Exception e) {
            // 로그 저장 실패는 무시
        }
    }

    /** [2026-10-23] 웹 광산 -- 내 곡괭이 내구도/저장 위치/캔 칸/광석 보유. enter=1이면 광산 입장(화면을 열 때/다시 돌아왔을 때) 로그. */
    @GetMapping("/api/tower-mine")
    @ResponseBody
    public ResponseEntity<?> apiTowerMine(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "enter", defaultValue = "") String enter) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        HashMap<String, Object> st = s5Service.mineState(userName);
        if ("1".equals(enter) && st.get("error") == null) {
            mineLog(userName, "[웹] 광산 입장", st.get("mapName") + " (" + st.get("x") + ", " + st.get("y") + ") · 곡괭이 내구도 "
                    + st.get("durability") + "/" + st.get("durabilityMax"));
        }
        return ResponseEntity.ok(st);
    }

    /** [2026-10-23] 웹 광산 -- (x, y) 칸 캐기(그 칸에 서 있어야 함, 곡괭이 내구도 1). v = 화면의 맵 version(바뀌었으면 reload). */
    @GetMapping("/api/tower-mine-dig")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineDig(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "v", defaultValue = "") String ver,
            @RequestParam(value = "dir", defaultValue = "down") String dir) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        HashMap<String, Object> d = s5Service.mineDig(userName, mapId, ver, x, y, dir);
        if (Boolean.TRUE.equals(d.get("ok"))) mineLog(userName, "[웹] 광산 채굴 (" + x + ", " + y + ")", String.valueOf(d.get("message")));
        return ResponseEntity.ok(d);
    }

    /** [2026-10-23] 웹 광산 -- 캐릭터 위치 저장(광산을 나갈 때 sendBeacon(POST)과 이동 중 주기적으로). */
    @RequestMapping(value = "/api/tower-mine-pos", method = { org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST })
    @ResponseBody
    public ResponseEntity<?> apiTowerMinePos(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "exit", defaultValue = "") String exit, @RequestParam(value = "v", defaultValue = "") String ver) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        my.prac.core.prjbot.service.impl.S5MineSessionLog.touch(userName); // [2026-10-28] 접속 기록 -- 화면이 살아 있음
        HashMap<String, Object> out = s5Service.mineSavePos(userName, mapId, ver, x, y);
        if ("1".equals(exit) && out.get("error") == null) { // 광산을 나갈 때/화면을 벗어날 때 -- 서버가 받아들인 최종 위치로 기록
            mineLog(userName, "[웹] 광산 퇴장", out.get("mapName") + " (" + out.get("x") + ", " + out.get("y") + ")");
        }
        if ("1".equals(exit)) s5Service.mineLeave(userName); // [2026-10-24] 다른 유저 화면에서 바로 사라지게
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 함께 보기 -- 내 위치/방향 알리기 + 같은 맵 다른 유저(이름 없이 익명 번호, 외형, 말풍선). 화면이 1초마다. 로그 없음. */
    @GetMapping("/api/tower-mine-presence")
    @ResponseBody
    public ResponseEntity<?> apiTowerMinePresence(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "d", defaultValue = "down") String dir) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        my.prac.core.prjbot.service.impl.S5MineSessionLog.touch(userName); // [2026-10-28] 접속 기록 -- 화면이 살아 있음(30초 넘게 없으면 강제 종료로 본다)
        return ResponseEntity.ok(s5Service.minePresence(userName, mapId, x, y, dir));
    }

    /** [2026-10-24] 웹 광산 "안녕하세요" -- 근처 다른 유저에게 말풍선, 하루 1회 오늘 요일석 5개. 보상 받을 때만 로그. */
    @GetMapping("/api/tower-mine-greet")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineGreet(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineGreet(userName);
        if (Boolean.TRUE.equals(out.get("reward"))) mineLog(userName, "[웹] 광산 인사 보상", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-23] 웹 광산 -- (x, y)에 서서 바로 옆 포탈(px, py)로 들어가기(지역 통로/동굴 입구/나가는 길/지하 문). */
    @GetMapping("/api/tower-mine-warp")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineWarp(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam("px") int px, @RequestParam("py") int py, @RequestParam(value = "v", defaultValue = "") String ver,
            @RequestParam(value = "dest", defaultValue = "") String dest) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineWarp(userName, mapId, ver, x, y, px, py, dest.isEmpty() ? null : dest);
        if (!dest.isEmpty() && Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 배 -> " + dest, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 몬스터 상태(체력이 줄었거나 쓰러졌거나 멈춘 것만) -- 화면이 1.5초마다. */
    @GetMapping("/api/tower-mine-mobs")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineMobs(@RequestParam(value = "mapId", defaultValue = "") String mapId) {
        return ResponseEntity.ok(s5Service.mineMobs(mapId));
    }

    /** [2026-10-24] 웹 광산 공격 -- (x, y)에 서서 바로 옆 몬스터(mob = 맵 안 번호)를 때린다. 처치/레벨 업/쓰러짐만 로그. */
    @GetMapping("/api/tower-mine-attack")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineAttack(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam("mob") int mob, @RequestParam(value = "v", defaultValue = "") String ver,
            @RequestParam(value = "mc", defaultValue = "-1") int mc) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineAttack(userName, mapId, ver, x, y, mob, mc); // mc = 화면이 보던 몬스터 칸(y * w + x)
        if (Boolean.TRUE.equals(out.get("killed")) || Boolean.TRUE.equals(out.get("died"))) mineLog(userName, "[웹] 광산 전투 " + mapId, String.valueOf(out.get("message")), out);
        return ResponseEntity.ok(out);
    }

    /** [2026-10-23] 광산 대장간 -- 평원 마을 대장장이에게 광석(grade 1~6)을 주고 곡괭이를 강화한다(최대/현재 내구도 함께 증가). times = 몇 번(보유 광석만큼으로 줄어듦). */
    @GetMapping("/api/tower-mine-pick-upgrade")
    @ResponseBody
    public ResponseEntity<?> apiTowerMinePickUpgrade(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam("grade") int grade, @RequestParam(value = "times", defaultValue = "1") int times) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.minePickUpgrade(userName, grade, times);
        if (Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 대장간 곡괭이 강화 " + grade + "등급 x" + times, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 대장장이 [구매] -- 장신구/장화 만들기·올리기(code), [구매]의 반지 바꿔 끼기. */
    @GetMapping("/api/tower-mine-craft")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineCraft(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam("code") String code) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineCraft(userName, code);
        if (Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 대장간 장비 " + code, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/api/tower-mine-ring")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineRing(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam("code") String code) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        return ResponseEntity.ok(s5Service.mineEquipRing(userName, code));
    }

    /** [2026-10-24] 웹 광산 대장장이 [합성] -- 요일석 7종 -> 칠요석. */
    @GetMapping("/api/tower-mine-synth")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineSynth(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "times", defaultValue = "1") int times) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineSynthSeven(userName, times);
        if (Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 대장간 칠요석 합성 x" + times, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 바닷가 선장 -- action = give(금광석 10개, 먹튀 가능) / craft(용왕의 반지). */
    @GetMapping("/api/tower-mine-captain")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineCaptain(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "action", defaultValue = "give") String action) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineCaptain(userName, action);
        if (Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 선장 " + action, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 명품상점 -- action = buy(code) / wear(code, 빈 code + slot = 기본) / gender. */
    @GetMapping("/api/tower-mine-look")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineLook(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "action", defaultValue = "wear") String action, @RequestParam(value = "code", defaultValue = "") String code,
            @RequestParam(value = "slot", defaultValue = "") String slot) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineLook(userName, action, code, slot);
        if (Boolean.TRUE.equals(out.get("ok")) && !"wear".equals(action)) mineLog(userName, "[웹] 광산 명품상점 " + action + " " + code, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 왕도 성당 신부 -- action = heal(체력 회복) / save(부활 지점 = 성당) / reset(부활 지점 = 평원 마을). */
    @GetMapping("/api/tower-mine-priest")
    @ResponseBody
    public ResponseEntity<?> apiTowerMinePriest(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "action", defaultValue = "heal") String action) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.minePriest(userName, action);
        if (!"heal".equals(action)) mineLog(userName, "[웹] 광산 성당 " + action, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 동굴 3층 보스 선공 -- 보스 2칸 안에 있으면 화면이 1.5초마다 부른다. 쓰러짐만 로그. */
    @GetMapping("/api/tower-mine-boss-hit")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineBossHit(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "v", defaultValue = "") String ver) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineBossHit(userName, mapId, ver, x, y);
        if (Boolean.TRUE.equals(out.get("died"))) mineLog(userName, "[웹] 광산 보스에게 쓰러짐", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-25] 웹 광산 전투 턴 -- 몬스터에게 인식돼 있거나 보스 2칸 안이면 화면이 1초마다 부른다. 쓰러짐만 로그. */
    @GetMapping("/api/tower-mine-combat-tick")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineCombatTick(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "v", defaultValue = "") String ver) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineCombatTick(userName, mapId, ver, x, y);
        if (Boolean.TRUE.equals(out.get("died"))) mineLog(userName, "[웹] 광산 몬스터에게 쓰러짐", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-24] 웹 광산 탐지 곡괭이 -- (x, y) 주변 3x3의 안 캔 광석 칸(화면이 멈춰 선 칸이 바뀔 때). 로그 없음. */
    @GetMapping("/api/tower-mine-scan")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineScan(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        return ResponseEntity.ok(s5Service.mineScan(userName, mapId, x, y));
    }

    /** [2026-10-24] 웹 광산 관리자 전용 좌표 이동. */
    @GetMapping("/api/tower-mine-admin-warp")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineAdminWarp(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineAdminWarp(userName, mapId, x, y);
        mineLog(userName, "[웹] 광산 관리자 좌표 이동 " + mapId + " (" + x + ", " + y + ")", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-23] 웹 광산 -- 마을로 이동([2026-10-25] 부활 지점: 성당에서 기도했으면 왕도 성당, 아니면 중앙 평원 시작점). */
    @GetMapping("/api/tower-mine-village")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineVillage(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        return ResponseEntity.ok(s5Service.mineGoVillage(userName));
    }

    /** [2026-10-26] 웹 광산 한기 턴 -- 한기 맵(설원/설산)에서 화면이 1초마다 부른다. 쓰러짐만 로그. */
    @GetMapping("/api/tower-mine-cold-tick")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineColdTick(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "v", defaultValue = "") String ver) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineColdTick(userName, mapId, ver, x, y);
        if (Boolean.TRUE.equals(out.get("died"))) mineLog(userName, "[웹] 광산 추위에 쓰러짐", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-28] 웹 광산 구리 정련 / 오늘의 구리 납품(대장장이 [합성]). */
    @GetMapping("/api/tower-mine-refine")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineRefine(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "times", defaultValue = "1") int times) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineRefine(userName, times);
        mineLog(userName, "[웹] 광산 구리 정련 x" + times, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-28] 웹 광산 요일 동굴 문지기 -- day 0(월) ~ 6(일). */
    @GetMapping("/api/tower-mine-daygate")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineDayGate(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "day", defaultValue = "-1") int day) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineDayGate(userName, day);
        mineLog(userName, "[웹] 광산 요일 동굴 문지기 " + day, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-28] 웹 광산 진입 공지 확인. */
    @GetMapping("/api/tower-mine-notice-ack")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineNoticeAck(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "id", defaultValue = "0") int id) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        return ResponseEntity.ok(s5Service.mineNoticeAck(userName, id));
    }

    @GetMapping("/api/tower-mine-deliver")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineDeliver(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineDeliver(userName);
        mineLog(userName, "[웹] 광산 구리 납품", String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-26] 웹 광산 방한 외투 -- action list/buy/wear. 사기/입기만 로그. */
    @GetMapping("/api/tower-mine-coat")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineCoat(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "action", defaultValue = "list") String action, @RequestParam(value = "code", defaultValue = "") String code) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineCoat(userName, action, code);
        if (!"list".equals(action)) mineLog(userName, "[웹] 광산 방한 외투 " + action + " " + code, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-28] 웹 광산 다도해(운영자만) -- 신궁 수문장/배 목수/뱃사공. 받기/보상/배 만들기/배 타기 로그. */
    @GetMapping("/api/tower-mine-archi")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineArchi(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "action", defaultValue = "") String action) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineArchi(userName, action);
        mineLog(userName, "[웹] 광산 다도해 " + action, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-25] 웹 광산 이동 안내원 --마을(평원 마을/왕도, 왕도는 아직 관리자만)끼리 무료 이동. */
    @GetMapping("/api/tower-mine-guide")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineGuide(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "mapId", defaultValue = "") String mapId, @RequestParam("x") int x, @RequestParam("y") int y,
            @RequestParam(value = "dest", defaultValue = "") String dest, @RequestParam(value = "v", defaultValue = "") String ver) {
        if (userName.trim().isEmpty()) return ResponseEntity.ok(new HashMap<String, Object>());
        HashMap<String, Object> out = s5Service.mineGuide(userName, mapId, ver, x, y, dest);
        if (Boolean.TRUE.equals(out.get("ok"))) mineLog(userName, "[웹] 광산 이동 안내원 " + mapId + " -> " + dest, String.valueOf(out.get("message")));
        return ResponseEntity.ok(out);
    }

    /** [2026-10-23] 광산상점 -- 광석으로 자동사냥 처치당 PP 올리기(상품/보유 광석/현재 보너스). */
    @GetMapping("/api/tower-mine-shop")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineShop(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        return ResponseEntity.ok(s5Service.mineShop(userName));
    }

    /** [2026-10-23] 광산상점 -- 광부 투구 구매/업그레이드. */
    @GetMapping("/api/tower-mine-helmet")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineHelmet(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> out = new HashMap<>();
        if (userName.trim().isEmpty()) {
            out.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(out);
        }
        String msg = s5Service.mineHelmetUpgrade(userName);
        mineLog(userName, "[웹] 광산상점 광부 투구", msg);
        out.put("message", msg);
        return ResponseEntity.ok(out);
    }

    /** [2026-10-23] 광산상점 구매 -- no = 상품 번호(1~6), times = 몇 번 살지. */
    @GetMapping("/api/tower-mine-shop-buy")
    @ResponseBody
    public ResponseEntity<?> apiTowerMineShopBuy(@RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam("no") int no, @RequestParam(value = "times", defaultValue = "1") int times) {
        HashMap<String, Object> out = new HashMap<>();
        if (userName.trim().isEmpty()) {
            out.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(out);
        }
        String msg = s5Service.mineShopBuy(userName, no, times);
        mineLog(userName, "[웹] 광산상점 구매 " + no + "번 x" + times, msg);
        out.put("message", msg);
        return ResponseEntity.ok(out);
    }

    /** [2026-10-20] 동료 상세 팝업용 -- 최종 능력치 분해(기본/한계돌파/장비)와 장비별 기여. */
    @GetMapping("/api/tower-companion-detail")
    @ResponseBody
    public ResponseEntity<?> apiTowerCompanionDetail(@RequestParam(value = "userName", defaultValue = "") String userName,
                                                     @RequestParam(value = "companionId", defaultValue = "0") int companionId) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        return ResponseEntity.ok(s5Service.companionDetail(userName, companionId));
    }

    /** [2026-10-20] 장비변경 팝업용 -- 낄 수 있는 장비의 기여 미리보기. */
    @GetMapping("/api/tower-equip-preview")
    @ResponseBody
    public ResponseEntity<?> apiTowerEquipPreview(@RequestParam(value = "userName", defaultValue = "") String userName,
                                                  @RequestParam(value = "companionId", defaultValue = "0") int companionId,
                                                  @RequestParam(value = "part", defaultValue = "") String part) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        return ResponseEntity.ok(s5Service.equipPreview(userName, companionId, part));
    }

    /** [2026-10-20] 편성 화면 장비 카드 표시용 -- 파티 동료별 장비 기여 수치(강화 포함). */
    @GetMapping("/api/tower-party-equip-stat")
    @ResponseBody
    public ResponseEntity<?> apiTowerPartyEquipStat(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }
        result.putAll(s5Service.partyEquipAndDetail(userName)); // byCompanion + detail (한 번에 계산)
        return ResponseEntity.ok(result);
    }

    /** [2026-10-20] ★7 동료 승급 팝업용 -- 재료/조각 현황과 직업별 ★7 예상 능력치. */
    @GetMapping("/api/tower-star7")
    @ResponseBody
    public ResponseEntity<?> apiTowerStar7(@RequestParam(value = "userName", defaultValue = "") String userName) {
        if (userName.trim().isEmpty()) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(err);
        }
        return ResponseEntity.ok(s5Service.star7Status(userName));
    }

    /** [2026-10-08] 웹 도감 탭 -- 아이템도감(★7 전설장비 목록, 보유 인원, 최고 강화). */
    @GetMapping("/api/tower-codex-items")
    @ResponseBody
    public ResponseEntity<?> apiTowerCodexItems() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("items", s5Service.legendaryCodex());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/tower-achievements")
    @ResponseBody
    public ResponseEntity<?> apiTowerAchievements(@RequestParam(value = "userName", defaultValue = "") String userName) {
        HashMap<String, Object> result = new HashMap<>();
        List<HashMap<String, Object>> all = s5Dao.selectAchievementList();
        List<HashMap<String, Object>> mine = userName.trim().isEmpty()
                ? new ArrayList<>() : s5Dao.selectUserAchievements(userName);
        HashMap<Integer, Boolean> cleared = new HashMap<>();
        for (HashMap<String, Object> m : mine) cleared.put(toInt(m.get("ACH_ID")), true);

        // 달성한 업적만 노출(미달성은 숨김/히든 여부 관계없이 전부 제외) -- /탑업적 채팅 명령어와 동일한 정책
        List<HashMap<String, Object>> visible = new ArrayList<>();
        for (HashMap<String, Object> a : all) {
            boolean done = cleared.containsKey(toInt(a.get("ACH_ID")));
            if (!done) continue;
            HashMap<String, Object> row = new HashMap<>(a);
            row.put("DONE", done);
            visible.add(row);
        }
        result.put("achievements", visible);
        result.put("total", all.size());
        result.put("clearedCount", mine.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 통합 액션 엔드포인트. 채팅 명령어(/주사위 등)와 동일한 BotS5Service 로직을 그대로 호출한다.
     * GET /loa/api/tower-action?userName=..&type=DICE|CHANGE_FLOOR|TOWER_DOWN|TOWER_UP|PARTY_TOGGLE|
     *     PARTY_SWAP|PARTY_UNASSIGN_ALL|GACHA_COMPANION|GACHA_EQUIP|GACHA_ACCESSORY|DICE_BUY|DICE_BONUS_BUY|
     *     DICE_MALUS_BUY|DICE_MIN_SELECT|STAT_BUY|EQUIP_WEAR|
     *     EQUIP_SYNTH|EQUIP_SYNTH_ALL|EQUIP_UNWEAR_ALL|EQUIP_UNWEAR_ONE|REDEEM_COMPANION_TICKET|REDEEM_WEAPON_TICKET
     *     &param1=&param2=
     * TOWER_DOWN(파라미터 없음)은 채팅 /탑내려가기(/탑다운)와 동일 -- 마을에서만 바로 아래
     * 10층 구간 마을로 이동(웹 '탑' 탭 층이동 그림 맨 아래 버튼용). TOWER_UP(파라미터 없음)은
     * 채팅 /탑올라가기(/탑업)와 동일 -- 마을에서만, 이 구간 보스를 이미 처치했으면 바로 위
     * 10층 구간 마을로 이동(웹 '탑' 탭 층이동 그림 맨 위 버튼용).
     * PARTY_SWAP(param1=/파티편성 목록 번호, param2=배치할 파티 슬롯 1~3)는 슬롯 탭 시트에서
     * 쓰는 파티 배치 통합 기능(웹 UI 전용) -- 이미 편성된 동료면 자리 이동/맞교환, 미편성
     * 동료면 그 자리에 배치(다른 동료가 있으면 축출 + 장비 자동 해제). PARTY_UNASSIGN_ALL
     * (파라미터 없음)은 편성된 동료 전원을 한 번에 해제하는 웹 UI 전용 기능.
     * REDEEM_*_TICKET(param1=직업 코드, param2=선택권 등급 3/4/5)는 10층 구간 완전탐사
     * 보상인 선택권을 쓰는 기능으로, 웹 UI 전용이다(채팅 명령어로는 노출 안 함 -- 의도적).
     */
    @GetMapping("/api/tower-action")
    @ResponseBody
    public ResponseEntity<?> apiTowerAction(
            @RequestParam(value = "userName", defaultValue = "") String userName,
            @RequestParam(value = "type", defaultValue = "") String type,
            @RequestParam(value = "param1", defaultValue = "") String param1,
            @RequestParam(value = "param2", defaultValue = "") String param2) {

        HashMap<String, Object> result = new HashMap<>();
        if (userName.trim().isEmpty()) {
            result.put("error", "유저명을 입력하세요.");
            return ResponseEntity.ok(result);
        }

        String message;
        try {
            switch (type) {
                case "DICE": {
                    // [2026-10-22] 연타 대응 -- 쿨타임이면 DB 없이 바로 거절(메모리 판정)
                    String fastCooldown = s5Service.diceCooldownFast(userName);
                    if (fastCooldown != null) {
                        message = fastCooldown;
                        break;
                    }
                    // [2026-10-22] 전투 영향 패치 확인 전/전설 규칙 위반이면 굴리지 않고 화면이 확인 팝업을 띄우도록 gate를 같이 내려준다.
                    HashMap<String, Object> gate = s5Service.diceGate(userName);
                    if (gate != null) {
                        message = String.valueOf(gate.get("text"));
                        result.put("gate", gate.get("type"));
                    } else {
                        message = s5Service.rollDice(userName, "WEB");
                    }
                    break;
                }
                case "SET_DOCK_LAYOUT":
                    message = s5Service.setDockLayout(userName, param1);
                    break;
                case "PATCH_ACK":
                    message = s5Service.ackPatch(userName);
                    break;
                case "USE_ENHANCE_TICKET":
                    message = s5Service.useEnhanceTicket(userName, Integer.parseInt(param1), Integer.parseInt(param2));
                    break;
                case "CHANGE_FLOOR":
                    message = s5Service.changeFloor(userName, Integer.parseInt(param1));
                    break;
                case "TOWER_DOWN": // 채팅 /탑내려가기(/탑다운)와 동일 -- 탑 그림 맨 아래 버튼용
                    message = s5Service.descendVillage(userName);
                    break;
                case "TOWER_UP": // 채팅 /탑올라가기(/탑업)와 동일 -- 탑 그림 맨 위 버튼용
                    message = s5Service.ascendVillage(userName);
                    break;
                case "PARTY_TOGGLE":
                    message = s5Service.partyToggle(userName, Integer.parseInt(param1));
                    break;
                case "PARTY_SWAP":
                    message = s5Service.partySwapSlot(userName, Integer.parseInt(param1), Integer.parseInt(param2));
                    break;
                case "PARTY_UNASSIGN_ALL":
                    message = s5Service.partyUnassignAll(userName);
                    break;
                case "COMPANION_HIDE":
                    message = s5Service.toggleCompanionHidden(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_COMPANION":
                    message = s5Service.gachaCompanion(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_COMPANION_10":
                    message = s5Service.gachaCompanionTen(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_EQUIP":
                    message = s5Service.gachaEquip(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_EQUIP_10":
                    message = s5Service.gachaEquipTen(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_ACCESSORY": // [2026-09-15] 목걸이/반지/팔찌 전용 뽑기
                    message = s5Service.gachaAccessory(userName, Integer.parseInt(param1));
                    break;
                case "GACHA_ACCESSORY_10":
                    message = s5Service.gachaAccessoryTen(userName, Integer.parseInt(param1));
                    break;
                case "DICE_BUY":
                    message = s5Service.diceShop(userName, param1.isEmpty() ? null : Integer.parseInt(param1));
                    break;
                case "DICE_BONUS_BUY":
                    message = s5Service.buyDiceBonus(userName);
                    break;
                case "DICE_MALUS_BUY":
                    message = s5Service.buyDiceMalus(userName);
                    break;
                case "DICE_MIN_SELECT":
                    message = s5Service.selectDiceMinAdjust(userName, Integer.parseInt(param1));
                    break;
                case "STAT_BUY":
                    message = s5Service.statShop(userName, param1.isEmpty() ? null : param1);
                    break;
                case "EQUIP_WEAR":
                    message = s5Service.equipWear(userName, Integer.parseInt(param1),
                            param2.isEmpty() ? null : Integer.parseInt(param2));
                    break;
                case "EQUIP_SYNTH":
                    message = s5Service.equipSynthesis(userName, Integer.parseInt(param1));
                    break;
                case "EQUIP_SYNTH_ALL":
                    message = s5Service.equipSynthesisAll(userName);
                    break;
                case "CRAFT_LEGENDARY":
                    message = s5Service.craftLegendary(userName, Integer.parseInt(param1));
                    break;
                case "DISENCHANT_LEGENDARY": // 예전 화면(번호 기준) 호환
                    message = s5Service.disenchantLegendary(userName, Integer.parseInt(param1));
                    break;
                case "EQUIP_DISMANTLE": // [2026-10-23] param1=EQUIP_ID, param2=개수
                    message = s5Service.dismantleEquip(userName, Integer.parseInt(param1), param2.isEmpty() ? 1 : Integer.parseInt(param2));
                    break;
                case "DISENCHANT_LEGENDARY_ID":
                    message = s5Service.disenchantLegendaryById(userName, Integer.parseInt(param1));
                    break;
                case "ENHANCE_LEGENDARY":
                    message = s5Service.enhanceLegendary(userName, Integer.parseInt(param1));
                    break;
                case "GROWTH_RENAME":
                    message = s5Service.growthRename(userName, param1);
                    break;
                case "COMPANION_IMAGE_SWAP":
                    message = s5Service.swapCompanionImage(userName, Integer.parseInt(param1), Integer.parseInt(param2));
                    break;
                case "GROWTH_JOB":
                    message = s5Service.growthChangeJob(userName, param1);
                    break;
                case "SUMMON_STAR7":
                    message = s5Service.summonStar7(userName, param1);
                    break;
                case "RELEASE_STAR7":
                    message = s5Service.releaseStar7(userName);
                    break;
                case "SET_BATTLE_SCREEN_VERSION":
                    message = s5Service.setBattleScreenVersion(userName, param1);
                    break;
                case "EQUIP_UNWEAR_ALL":
                    message = s5Service.equipUnwearAll(userName, Integer.parseInt(param1));
                    break;
                case "EQUIP_BEST_ALL":
                    message = s5Service.equipBestAll(userName, Integer.parseInt(param1));
                    break;
                case "EQUIP_UNWEAR_ONE":
                    message = s5Service.equipUnwearOne(userName, Integer.parseInt(param1));
                    break;
                case "REDEEM_COMPANION_TICKET":
                    message = s5Service.redeemCompanionChoiceTicket(userName, param1, Integer.parseInt(param2));
                    break;
                case "REDEEM_WEAPON_TICKET":
                    message = s5Service.redeemWeaponChoiceTicket(userName, param1, Integer.parseInt(param2));
                    break;
                default:
                    message = "알 수 없는 액션입니다.";
            }
        } catch (NumberFormatException e) {
            message = "잘못된 입력값입니다.";
        }

        // /탑통계용 활동 카운터(채팅 쪽은 Season5Controller에서 동일하게 적재)
        if ("DICE".equals(type)) {
            s5Service.bumpActivityStat(userName, "DICE_WEB");
            if (message != null && message.contains("파티 전멸")) {
                s5Service.bumpActivityStat(userName, "WIPE_WEB");
            }
        } else if (type != null && type.startsWith("GACHA_")) {
            s5Service.bumpActivityStat(userName, "GACHA_WEB");
        }

        // "웹 액션도 TBOT_WORD_HIS에 넣어달라" 요청 -- 지금까지는 채팅 명령어(/주사위 등)만
        // 이 로그에 남고 웹 SPA 버튼 액션은 안 남아서, 웹으로만 플레이한 유저는 "메시지" 탭
        // (/api/tower-messages)에 이력이 하나도 안 보이고, 관리자가 과거 이상행동을 감사(audit)
        // 할 때도 웹 채널은 사각지대였다(범용 뽑기권 악용 조사 때 실제로 이 한계에 부딪힘).
        // 로그 실패가 실제 액션 응답을 막으면 안 되므로 채팅 컨트롤러와 동일하게 try/catch로 격리.
        try {
            HashMap<String, Object> logMap = new HashMap<>();
            logMap.put("userName", userName);
            logMap.put("roomName", "WEB");
            logMap.put("req", "[웹] " + type + (param1.isEmpty() ? "" : " " + param1) + (param2.isEmpty() ? "" : " " + param2));
            logMap.put("res", message);
            botService.insertS5WordHisTx(logMap); // [2026-09-30] 시즌5 전용 로그 테이블
        } catch (Exception e) {
            // 로그 저장 실패는 무시(채팅 컨트롤러의 기존 관례와 동일)
        }

        result.put("message", message);
        return ResponseEntity.ok(result);
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return ((Number) o).intValue(); } catch (Exception e) { return 0; }
    }

    /**
     * 방문(발견)한 칸만 실제 TILE_TYPE을 노출하고, 미발견 칸은 종류를 감춘 채(DISCOVERED=false)
     * 내려준다 -- 화면에서 안개(fog of war) 처리를 서버가 보장.
     */
    /** BotS5ServiceImpl.STAIR_ZONE_START와 같은 값 -- 101층부터 9칸 고정 "계단 구역". */
    private static final int STAIR_ZONE_START = 101;

    /** BotS5ServiceImpl.isStairZone과 동일 -- 101층 이상이면서 100의 배수(마을)가 아닌 층. */
    private static boolean isStairZoneFloor(int floor) {
        return floor >= STAIR_ZONE_START && floor % 100 != 0;
    }

    private List<HashMap<String, Object>> buildTilesWithFogOfWar(String userName, int floor) {
        Set<Integer> visited = new HashSet<>();
        for (HashMap<String, Object> v : s5Dao.selectVisitedTileNos(userName, floor)) {
            visited.add(toInt(v.get("TILE_NO")));
        }
        List<HashMap<String, Object>> out = new ArrayList<>();
        // [2026-09-28] 계단 구역은 9칸 순서(계단-전투-전투-행운-중간보스-...)가 공개된 고정 틀이라
        // 안개 처리 없이 전부 보여준다(행운칸이 무엇으로 변할지는 밟을 때 정해지므로 여전히 비밀).
        boolean stairZone = isStairZoneFloor(floor);
        for (HashMap<String, Object> t : s5Service.ensureUserBoard(userName, floor)) {
            int tileNo = toInt(t.get("TILE_NO"));
            HashMap<String, Object> row = new HashMap<>();
            row.put("TILE_NO", tileNo);
            boolean discovered = stairZone || visited.contains(tileNo);
            row.put("DISCOVERED", discovered);
            if (discovered) row.put("TILE_TYPE", t.get("TILE_TYPE"));
            out.add(row);
        }
        return out;
    }
}
