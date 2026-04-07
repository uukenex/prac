package my.prac.api.loa.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import my.prac.core.game.dto.JobDef;
import my.prac.core.util.MiniGameUtil;

@Controller
@RequestMapping("/loa")
public class LoaJobViewController {

    /** JSP 뷰 페이지 */
    @GetMapping("/job-view")
    public String jobViewPage() {
        return "nonsession/loa/job_view";
    }

    /** REST API: 직업 목록 */
    @GetMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<?> getJobs() {
        List<Map<String, String>> jobs = new ArrayList<>();
        for (Map.Entry<String, JobDef> entry : MiniGameUtil.JOB_DEFS.entrySet()) {
            JobDef def = entry.getValue();
            Map<String, String> item = new HashMap<>();
            item.put("name",       def.name);
            item.put("listLine",   def.listLine   != null ? def.listLine   : "");
            item.put("attackLine", def.attackLine != null ? def.attackLine : "");
            jobs.add(item);
        }
        return ResponseEntity.ok(jobs);
    }
}
