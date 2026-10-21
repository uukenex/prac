package my.prac.core.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import my.prac.core.prjbot.dao.S5RequestCache;

/** [2026-10-21 성능] 요청 한 건 동안만 살아있는 S5 DAO 읽기 캐시의 시작/끝(웹 API와 /loa/chat 모두 HTTP 요청이라 여기서 한 번에 처리). */
public class S5RequestCacheFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        S5RequestCache.begin();
        try {
            chain.doFilter(request, response);
        } finally {
            S5RequestCache.end();
        }
    }

    @Override
    public void destroy() {}
}
