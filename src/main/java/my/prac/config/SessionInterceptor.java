package my.prac.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

@Component
public class SessionInterceptor extends HandlerInterceptorAdapter {
	static Logger logger = LoggerFactory.getLogger(SessionInterceptor.class);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		HttpSession session = request.getSession();

		if (session.getAttribute("Users") == null) {
			// [2026-10-07] 예전에는 리다이렉트만 보내고 true를 반환해서 컨트롤러가 그대로 실행됐다. 컨트롤러도 "redirect:/loginCheck"를 돌려주면
			// 이미 응답이 나간 뒤 다시 리다이렉트를 시도해 IllegalStateException(500)이 났다(비로그인으로 비밀게시판 진입 시).
			// 이제 리다이렉트 후 false로 핸들러 실행을 막고, 로그인 뒤 원래 가려던 화면으로 돌아오도록 요청 주소(GET만)를 기억해 둔다.
			if ("GET".equalsIgnoreCase(request.getMethod())) {
				String uri = request.getRequestURI().substring(request.getContextPath().length());
				String qs = request.getQueryString();
				session.setAttribute("loginTarget", qs == null || qs.isEmpty() ? uri : uri + "?" + qs);
			}
			response.sendRedirect(request.getContextPath() + "/loginCheck");
			return false;
		}

		return true;
	}

}
