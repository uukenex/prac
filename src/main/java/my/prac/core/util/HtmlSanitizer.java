package my.prac.core.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * [2026-10-07] 게시글 본문(스마트에디터 HTML) 출력용 살균. 본문 서식(글자 크기/색, 표, 이미지, 링크 등)은 유지하고
 * script, on* 이벤트 속성, javascript: 링크, iframe 같은 실행 가능한 요소만 제거한다(저장된 기존 글에도 읽을 때 적용).
 */
public final class HtmlSanitizer {
	private HtmlSanitizer() {}

	private static final Safelist LIST = Safelist.relaxed()
			.addTags("font", "center", "s", "hr", "del", "ins", "mark")
			.addAttributes(":all", "style", "class", "align", "dir")
			.addAttributes("font", "color", "size", "face")
			.addAttributes("table", "border", "cellpadding", "cellspacing", "width", "height", "bgcolor")
			.addAttributes("td", "colspan", "rowspan", "width", "height", "bgcolor", "valign")
			.addAttributes("th", "colspan", "rowspan", "width", "height", "bgcolor", "valign")
			.addAttributes("a", "target")
			.addProtocols("img", "src", "data")
			.preserveRelativeLinks(true);

	public static String clean(String html) {
		if (html == null) return "";
		Document.OutputSettings os = new Document.OutputSettings().prettyPrint(false);
		// baseUri가 비어 있으면 상대 경로(/upload/a.png)를 안전한 주소로 판정하지 못해 지워버리므로 더미 기준 주소를 준다(preserveRelativeLinks라 출력은 상대 경로 그대로).
		return Jsoup.clean(html, "http://localhost/", LIST, os);
	}
}
