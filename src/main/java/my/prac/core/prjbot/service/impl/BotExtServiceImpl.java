package my.prac.core.prjbot.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
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

    
    private String makeTimeVal(Date startTimeKst) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd_HH");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul")); // 안전하게 KST 고정
        return fmt.format(startTimeKst);
    }
}
