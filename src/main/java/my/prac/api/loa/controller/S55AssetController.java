package my.prac.api.loa.controller;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * [2026-11-03] 시즌 5.5(광산) 경량화 -- "로딩도 빠르게" 요청.
 * 탑 화면(tower_view.jsp)이 열 때마다 CSS/JS 약 670KB(압축 후 약 210KB)를 HTML에 담아 매번 새로 받았다(화면은 저장 금지).
 * 그 CSS/JS를 원래 JSP 파일에서 그대로 뽑아 따로 내려주고(/loa/s55/파일?v=빌드 번호), 브라우저가 1년 동안 저장해 두게 한다.
 * 배포하면 빌드 번호(Season5ViewController.PAGE_BUILD)가 바뀌어 새 주소로 다시 받는다. 소스는 그대로 JSP 파일이라 수정하는 곳은 예전과 같다.
 *  - tower.css = tower_view.jsp의 첫 &lt;style&gt; 블록
 *  - tower.js  = tower_view.jsp의 &lt;script&gt; 블록 중 빌드 확인용(pageBuild를 쓰는 블록)을 뺀 것
 *  - mine.js   = _mine_ore.jsp + _mine_char.jsp + _tower_mine.jsp의 &lt;script&gt; 블록(이 순서)
 * 블록은 그 태그만 있는 줄로 시작/끝나야 한다(주석 글 속 태그 글자는 무시 -- 2026-11-03 tower.css 앞에 주석 조각이 섞였던 것).
 * 뽑을 때 JSP 식은 &lt;%=request.getContextPath()%&gt; 하나만 바꿔 넣는다(다른 JSP 식이 있으면 그 블록은 쓰지 않도록 화면 쪽에 두어야 한다).
 */
@Controller
@RequestMapping("/loa")
public class S55AssetController {

    private static final String DIR = "/WEB-INF/view/nonsession/loa/";
    // [2026-11-03] 블록은 태그만 있는 줄로 시작/끝나야 한다 -- 주석 글 속 "<style>" 글자를 블록 시작으로 잡아 tower.css 앞에 주석 조각이 섞였었다
    private static final Pattern SCRIPT = Pattern.compile("(?m)^[ \\t]*<script>[ \\t]*\\r?$([\\s\\S]*?)^[ \\t]*</script>");
    private static final Pattern STYLE = Pattern.compile("(?m)^[ \\t]*<style>[ \\t]*\\r?$([\\s\\S]*?)^[ \\t]*</style>");
    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<>();

    @Autowired
    private ServletContext servletContext;

    @GetMapping("/s55/{file:.+}")
    @ResponseBody
    public ResponseEntity<byte[]> asset(@PathVariable("file") String file, HttpServletRequest req) {
        String ctx = req.getContextPath();
        byte[] body = CACHE.computeIfAbsent(file + "|" + ctx, k -> build(file, ctx));
        if (body.length == 0) return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        MediaType type = file.endsWith(".css") ? MediaType.valueOf("text/css;charset=UTF-8") : MediaType.valueOf("application/javascript;charset=UTF-8");
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic()).body(body);
    }

    private byte[] build(String file, String ctx) {
        StringBuilder sb = new StringBuilder();
        if ("tower.css".equals(file)) {
            Matcher m = STYLE.matcher(read("tower_view.jsp"));
            if (m.find()) sb.append(m.group(1));
        } else if ("tower.js".equals(file)) {
            Matcher m = SCRIPT.matcher(read("tower_view.jsp"));
            while (m.find()) if (!m.group(1).contains("pageBuild")) sb.append(m.group(1)).append("\n;\n");
        } else if ("mine.js".equals(file)) {
            for (String f : new String[] { "_mine_ore.jsp", "_mine_char.jsp", "_tower_mine.jsp" }) {
                Matcher m = SCRIPT.matcher(read(f));
                while (m.find()) sb.append(m.group(1)).append("\n;\n");
            }
        }
        return sb.toString().replace("<%=request.getContextPath()%>", ctx).getBytes(StandardCharsets.UTF_8);
    }

    private String read(String name) {
        try (InputStream in = servletContext.getResourceAsStream(DIR + name)) {
            if (in == null) return "";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
