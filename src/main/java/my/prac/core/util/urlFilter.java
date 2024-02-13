package my.prac.core.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <pre>
 * 1. 개요 : <br>
 * 2. 작성일 : 2017. 5. 19.<br>
 * 3. 작성자 : ghkim<br>
 * 4. 설명 : <br>
 * </pre>
 */
public class urlFilter implements Filter {

    @Override
    public void init ( FilterConfig paramFilterConfig ) throws ServletException {

    }

    /*
     * (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
     * javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    @Override
    public void doFilter ( ServletRequest request, ServletResponse response, FilterChain chain )
            throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        try {
        	switch(request.getRemoteHost()) {
        		//일반서버일땐 이미지경로 차단
	        	case "http://dev-apc.com":
	        	case "dev-apc.com":
	        		if(httpServletRequest.getServletPath().indexOf("/loa/imgs")>=0) {
	        			System.out.println("일반서버에서 이미지 접근 ");
	        			httpServletResponse.sendError(600);
	        		}
        		break;
        		//이미지 서버일땐 이미지 경로로만 접근
	        	case "http://imgwww.dev-apc.com":
	        	case "imgwww.dev-apc.com":
	    			if(httpServletRequest.getServletPath().indexOf("/loa/imgs") < 0) {
	    				System.out.println("이미지서버에서 일반 접근 ");
	        			httpServletResponse.sendError(600);
	    			}
				break;
        		
        		case "0:0:0:0:0:0:0:1":
        			if(httpServletRequest.getServletPath().indexOf("/loa/imgs")>=0) {
        				System.out.println("여기에걸렸습니다 ");
            			httpServletResponse.sendError(600);
        			}
    			break;
        	}
        	
            chain.doFilter( request, response );
        } finally {

        }

    }

    /*
     * (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    @Override
    public void destroy () {

    }

}
