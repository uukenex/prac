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
        // [2026-09-16 버그 수정] "/newboard/shareUpdateForm이 prd-web에서 500난다" 신고로
        // 확인 -- prd-web.dev-apc.com은 /bom만 막고 /newboard는 원래도 안 막혀있었다(진짜
        // 원인이 아니었음). 대신 이 필터 자체에 버그 2개가 있었다:
        //   1) chain.doFilter(뒤쪽 실제 컨트롤러/JSP 처리)가 이 try 안에 있어서, 다운스트림
        //      에서 예외가 나면 이 필터의 catch가 그걸 그대로 삼키고 sendError(600)을 부르는데,
        //      JSP가 이미 응답을 일부 flush한 뒤라면 "응답이 이미 커밋됨" 예외가 다시 터져서
        //      최종적으로 컨테이너 기본 500 페이지로 이어졌다(원래 의도한 커스텀 600이 아님).
        //   2) 차단 케이스(sendError(600) 호출)에서도 return 없이 그대로 chain.doFilter를
        //      계속 호출해서, "막았다"고 응답 상태만 찍어놓고 실제로는 요청 처리를 막지 못했다.
        // 그래서 호스트 판별(경로 차단)과 실제 요청 처리(chain.doFilter)를 분리하고, 차단 시엔
        // 확실히 return해서 이후 로직이 안 타게 한다.
        boolean blocked = false;
        try {
        	switch(request.getServerName()) {
        		case "http://dev-apc.com":
        		case "dev-apc.com":
        			httpServletResponse.sendError(600);
        			blocked = true;
        		break;


        		//이미지 서버일땐 이미지 경로로만 접근
	        	case "http://rgb-tns.dev-apc.com":
	        	case "rgb-tns.dev-apc.com":
	    			if(httpServletRequest.getServletPath().indexOf("/index") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/free") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/share") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/secret") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/newboard") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/game") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/bom") >= 0
	    			) {
	        			httpServletResponse.sendError(600);
	        			blocked = true;
	    			}
				break;

	        	case "http://prod-api.dev-apc.com":
	        	case "prod-api.dev-apc.com":
	        		if(httpServletRequest.getServletPath().indexOf("/loa") >= 0)
	        		{
	    			}else {

	    				httpServletResponse.sendError(600);
	    				blocked = true;
	    			}
				break;

	        	case "http://game.dev-apc.com":
	        	case "game.dev-apc.com":
	        		if(httpServletRequest.getServletPath().indexOf("/index") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/free") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/share") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/secret") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/newboard") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/wedding") >= 0
	    			 || httpServletRequest.getServletPath().indexOf("/bom") >= 0
	    			) {
	        			httpServletResponse.sendError(600);
	        			blocked = true;
	    			}
        		break;

	        	case "http://prd-web.dev-apc.com":
	        	case "prd-web.dev-apc.com":
	        		if (httpServletRequest.getServletPath().indexOf("/bom") >= 0) {
	        			httpServletResponse.sendError(600);
	        			blocked = true;
	        		}
        		break;

	        	case "http://bomin.dev-apc.com":
	        	case "bomin.dev-apc.com":
	        		if (httpServletRequest.getServletPath().indexOf("/bom") < 0) {
	        			httpServletResponse.sendError(600);
	        			blocked = true;
	        		}
        		break;

        		default:
        		break;

        	}
        }
        catch(Exception e) {
        	// 호스트/경로 판별 로직 자체에서 예외가 난 경우만 여기서 처리(아래
        	// chain.doFilter는 이 try 밖으로 뺐으므로 다운스트림 예외는 더 이상 여기서
        	// 삼켜지지 않는다 -- 컨트롤러/JSP 쪽 예외는 스프링/컨테이너의 정상적인
        	// 에러 처리(web.xml error-page 등)로 그대로 넘어가야 정확한 원인이 보인다).
        	httpServletResponse.sendError(600);
        	blocked = true;
        }

        if (!blocked) {
            chain.doFilter( request, response );
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
