package my.prac.core.prjbot.service.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        // 1. 가장 최근 startTime 블럭 찾기
        JsonNode latestNode = null;
        Instant latestStartInstant = null;

        for (JsonNode node : root) {
            String startTimeStr = node.path("startTime").asText(null);
            if (startTimeStr == null) continue;

            // 예: 2025-12-02T13:00:00Z
            Instant startInstant = Instant.parse(startTimeStr);

            if (latestStartInstant == null || startInstant.isAfter(latestStartInstant)) {
                latestStartInstant = startInstant;
                latestNode = node;
            }
        }

        if (latestNode == null) {
            return 0;
        }

        // 2. UTC startTime 을 KST(+9) 로 변환
        ZonedDateTime kstDateTime = latestStartInstant.atZone(ZoneId.of("Asia/Seoul"));
        java.util.Date startTimeKst = java.util.Date.from(kstDateTime.toInstant());

        // 3. reports 배열 돌면서 regionId / itemIds 를 DB에 저장
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

                MerchantReportVO vo = new MerchantReportVO();
                vo.setServerId(serverId);          // 5
                vo.setStartTimeKst(startTimeKst);  // +9h 변환된 시작시간
                vo.setRegionId(regionId);
                vo.setItemId(itemId);

                try {
                	botExtDAO.insertMerchantReport(vo);
                    insertCount++;
                } catch (Exception e) {
                    // 이미 PK가 있으면 (중복) 무시하고 넘어가도 됨
                    // 로그만 남기고 continue 해도 됨
                    // logger.warn("중복 또는 insert 실패", e);
                }
            }
        }

        return insertCount;
    }
}
