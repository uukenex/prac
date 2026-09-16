<%
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Cache-Control", "no-store");
    response.setDateHeader("Expires", 0);
    response.setHeader("Pragma", "no-cache");
%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>공유게시판 ::: TH보드</title>
</head>
<body>
<div class="nb" data-room="share">
  <jsp:include page="_chrome_top.jsp" />

  <div class="nb-board">
    <div class="nb-toolbar">
      <div>
        <h1>공유게시판</h1>
        <div class="nb-sub">문서형 위키 · 수정하면 버전이 쌓입니다</div>
      </div>
      <a class="nb-btn primary" href="<%=request.getContextPath()%>/newboard/shareSign">✎ 글쓰기</a>
    </div>

    <ul class="nb-list">
      <c:forEach var="share" items="${shares}">
        <li class="nb-row" onclick="location.href='<%=request.getContextPath()%>/newboard/shareView?shareNo=${share.shareNo}'">
          <span class="nb-no mono">v.${share.version}</span>
          <span class="nb-avatar">${fn:length(share.userNick) > 0 ? fn:substring(share.userNick,0,1) : '?'}</span>
          <div class="nb-main">
            <span class="nb-ttl">${share.shareName}</span>
            <div class="nb-metaline"><span>최근수정 ${share.userNick}</span><span><fmt:formatDate value="${share.modifyDate}" pattern="yy-MM-dd"/></span></div>
          </div>
          <span class="nb-stat">글번호 ${share.shareNo}</span>
        </li>
      </c:forEach>
      <c:if test="${totalPage == 0}"><li class="nb-empty">아직 작성된 문서가 없습니다.</li></c:if>
    </ul>

    <p class="nb-pager">
      <%!int i;%>
      <% for (int i = 1; i <= Integer.parseInt(String.valueOf(request.getAttribute("totalPage"))); i++) { %>
      <a href="<%=request.getContextPath()%>/newboard/share?page=<%=i%>"><%=i%></a>
      <% } %>
    </p>
  </div>
</div>
</body>
</html>
