package my.prac.core.util;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

public class XssUtils {

	//현재는 미사용 ...
	public static String cleanXss(String text) {
		Pattern onEventPattern = Pattern.compile("(<\\s?[^>]+\\s)" +
			"(on[\\w\\s]+?=\\s*'[^']*'" +
			"|on[\\w\\s]+?=\\s*\"[^\"]*\"" +
			"|on[\\w\\s]+?=[^>]*)" +
			"([^>]*>)");
		Pattern blacklist = Pattern.compile("script|iframe|frame(set)|eval|javascript", Pattern.CASE_INSENSITIVE); // 위험한 태그들, etc...

		String unescaped = StringEscapeUtils.unescapeHtml3(text);
		String eventRemoved = onEventPattern.matcher(unescaped)
			.replaceAll("$1$3");

		return blacklist.matcher(eventRemoved)
			.replaceAll("");
	}
}
