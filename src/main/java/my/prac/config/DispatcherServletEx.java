package my.prac.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.DispatcherServlet;

/*
 disabled 
 3월 09, 2021 2:21:24 오후 org.springframework.web.servlet.PageNotFound noHandlerFound
경고: No mapping found for HTTP request with URI [/.env] in DispatcherServlet with name 'dispatcherServlet'

*/
/* https://stackoverflow.com/questions/23396100/spring-security-how-to-intercept-pagenotfound */
public class DispatcherServletEx extends DispatcherServlet {
	private static final long serialVersionUID = 1L;

	static Logger logger = LoggerFactory.getLogger(DispatcherServletEx.class);

	@Override
	protected void noHandlerFound(HttpServletRequest request, HttpServletResponse response) throws Exception {
		if (pageNotFoundLogger.isWarnEnabled()) {

		}

		response.sendError(404);
	}

}
