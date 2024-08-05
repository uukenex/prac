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

import org.springframework.web.util.UrlPathHelper;

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
        	switch(request.getServerName()) {
        		case "http://dev-apc.com":
        		case "dev-apc.com":
        			httpServletResponse.sendError(600);
        		break;
        	
        	
        		//이미지 서버일땐 이미지 경로로만 접근
	        	case "http://rgb-tns.dev-apc.com":
	        	case "rgb-tns.dev-apc.com":
	    			if(httpServletRequest.getServletPath().indexOf("/index") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/free") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/share") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/game") >= 0
	    			) {
	        			httpServletResponse.sendError(600);
	    			}
				break;
				
	        	case "http://prod-api.dev-apc.com":
	        	case "prod-api.dev-apc.com":
	        		if(httpServletRequest.getServletPath().indexOf("/loa") >= 0) 
	        		{
	    			}else {
	    				
	    				httpServletResponse.sendError(600);
	    			}
				break;
				
	        	case "http://game.dev-apc.com":
	        	case "game.dev-apc.com":
	        		if(httpServletRequest.getServletPath().indexOf("/index") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/free") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/share") >= 0
	    			) {
	        			httpServletResponse.sendError(600);
	    			}
        		break;
	        	
	        	case "http://prd-web.dev-apc.com":
	        	case "prd-web.dev-apc.com":
        		break;
				
    			
        	}
        	
            chain.doFilter( request, response );
        }
        catch(Exception e) {
        	httpServletResponse.sendError(600);
        	chain.doFilter( request, response );
        }
        finally {

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
