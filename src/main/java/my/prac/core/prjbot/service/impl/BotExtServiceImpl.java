package my.prac.core.prjbot.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import my.prac.core.ext.dto.MerchantMasterVO;
import my.prac.core.ext.dto.MerchantReportVO;
import my.prac.core.prjbot.dao.BotExtDAO;
import my.prac.core.prjbot.service.BotExtService;

@Service("core.prjbot.BotExtService")
public class BotExtServiceImpl implements BotExtService {
	
	@Resource(name = "core.prjbot.BotExtDAO")
	BotExtDAO botExtDAO;
	
	
	private static final String NL = "♬";
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * merchant_server5.json 내용을 파싱해서
     * 가장 최근 startTime 블럭만 DB에 저장.
     */
    @Override
    public int saveLatestMerchantReports(String json, int serverId) throws Exception {

        JsonNode root = mapper.readTree(json);
        if (!root.isArray() || root.size() == 0) {
            return 0;
        }

        // ★ 1) 기준데이터 전체 읽어서 itemId -> grade 맵 생성
        List<MerchantMasterVO> masterList = botExtDAO.selectMerchantMasterAll();
        Map<Integer, Integer> gradeMap = new HashMap<>();
        for (MerchantMasterVO m : masterList) {
            gradeMap.put(m.getItemId(), m.getItemGrade());
        }

        // ★ 2) 가장 최근 startTime 블럭 찾기
        JsonNode latestNode = null;
        Instant latestStartInstant = null;

        for (JsonNode node : root) {
            String startTimeStr = node.path("startTime").asText(null);
            if (startTimeStr == null) continue;

            Instant startInstant = Instant.parse(startTimeStr); // UTC

            if (latestStartInstant == null || startInstant.isAfter(latestStartInstant)) {
                latestStartInstant = startInstant;
                latestNode = node;
            }
        }

        if (latestNode == null) {
            return 0;
        }

        // ★ 3) startTime, endTime → KST 변환
        String startTimeStr = latestNode.path("startTime").asText();
        String endTimeStr   = latestNode.path("endTime").asText();

        Instant startUtc = Instant.parse(startTimeStr);
        Instant endUtc   = Instant.parse(endTimeStr);

        ZonedDateTime startKstZdt = startUtc.atZone(ZoneId.of("Asia/Seoul"));
        ZonedDateTime endKstZdt   = endUtc.atZone(ZoneId.of("Asia/Seoul"));

        Date startTimeKst = Date.from(startKstZdt.toInstant());
        Date endTimeKst   = Date.from(endKstZdt.toInstant());

        // ★ 4) time_val 생성 (yyyyMMdd_HH, start 기준)
        String timeVal = makeTimeVal(startTimeKst);

        // ★ 5) reports 파싱
        JsonNode reports = latestNode.path("reports");
        if (!reports.isArray()) {
            return 0;
        }

        int insertCount = 0;

        for (JsonNode r : reports) {
            int regionId = r.path("regionId").asInt(0);
            if (regionId == 0) continue;

            JsonNode itemIds = r.path("itemIds");
            if (!itemIds.isArray()) continue;

            for (JsonNode itemIdNode : itemIds) {
                int itemId = itemIdNode.asInt(0);
                if (itemId == 0) continue;

                // ★ 6) grade == 4 체크 (서비스에서)
                Integer grade = gradeMap.get(itemId);
                if (grade == null || grade != 4) {
                    continue;  // 전설(4) 아니면 스킵
                }

                MerchantReportVO vo = new MerchantReportVO();
                vo.setServerId(serverId);
                vo.setStartTimeKst(startTimeKst);
                vo.setEndTimeKst(endTimeKst);    // ★ endTime도 같이 저장
                vo.setTimeVal(timeVal);
                vo.setRegionId(regionId);
                vo.setItemId(itemId);

                try {
                	botExtDAO.insertMerchantReport(vo);
                }catch(Exception e) { continue;}
            }
        }

        return insertCount;
    }

    @Override
    public String buildMerchantMessage(int serverId) throws Exception {

        String timeVal = resolveTimeVal();
        if (timeVal == null) {
            return "지금은 떠상 정보 조회 시간이 아닙니다."+NL
                 + "04:20 / 10:20 / 16:20 / 22:20 이후에 조회해주세요.";
        }

        HashMap<String, Object> param = new HashMap<>();
        param.put("serverId", serverId);
        param.put("timeVal", timeVal);

        List<HashMap<String, Object>> rows = botExtDAO.selectMerchantByTimeVal(param);

        if (rows == null || rows.isEmpty()) {
            return "카단 서버 떠상 정보가 등록되어 있지 않습니다."+NL+"(" + timeVal + ")";
        }

        // REGION_NAME 별로 ITEM_NAME 묶기
        Map<String, List<String>> regionMap = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String regionName = (String) row.get("REGION_NAME");
            String itemName   = (String) row.get("ITEM_NAME");

            regionMap
                .computeIfAbsent(regionName, k -> new ArrayList<>())
                .add(itemName);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("카단 서버 떠상 정보").append(NL);

        for (Map.Entry<String, List<String>> e : regionMap.entrySet()) {
            sb.append(e.getKey()).append(" : ");
            sb.append(String.join(",", e.getValue()));
            sb.append(NL);
        }

        return sb.toString().trim();
    }
    
    private String makeTimeVal(Date startTimeKst) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd_HH");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul")); // 안전하게 KST 고정
        return fmt.format(startTimeKst);
    }
    
    private String resolveTimeVal() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        LocalTime time = now.toLocalTime();

        LocalTime t0330 = LocalTime.of(3, 30);
        LocalTime t0420 = LocalTime.of(4, 20);
        LocalTime t0930 = LocalTime.of(9, 30);
        LocalTime t1020 = LocalTime.of(10, 20);
        LocalTime t1530 = LocalTime.of(15, 30);
        LocalTime t1620 = LocalTime.of(16, 20);
        LocalTime t2130 = LocalTime.of(21, 30);
        LocalTime t2220 = LocalTime.of(22, 20);

        DateTimeFormatter dateFmt = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

        String datePart;
        String slot;

        if (time.isBefore(t0330)) {
            // 00:00 ~ 03:29 → 전일 22시 타임
            datePart = yesterday.format(dateFmt);
            slot = "22";
        } else if (time.isBefore(t0420)) {
            // 03:30 ~ 04:19 → 조회 불가
            return null;
        } else if (time.isBefore(t0930)) {
            // 04:20 ~ 09:29 → 오늘 04
            datePart = today.format(dateFmt);
            slot = "04";
        } else if (time.isBefore(t1020)) {
            // 09:30 ~ 10:19 → 조회 불가
            return null;
        } else if (time.isBefore(t1530)) {
            // 10:20 ~ 15:29 → 오늘 10
            datePart = today.format(dateFmt);
            slot = "10";
        } else if (time.isBefore(t1620)) {
            // 15:30 ~ 16:19 → 조회 불가
            return null;
        } else if (time.isBefore(t2130)) {
            // 16:20 ~ 21:29 → 오늘 16
            datePart = today.format(dateFmt);
            slot = "16";
        } else if (time.isBefore(t2220)) {
            // 21:30 ~ 22:19 → 조회 불가
            return null;
        } else {
            // 22:20 ~ 23:59 → 오늘 22  (여기 설명에 10이라 적혀있었는데 22가 맞는 걸로 가정)
            datePart = today.format(dateFmt);
            slot = "22";
        }

        return datePart + "_" + slot; // 예: 20251204_04
    }
}
