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
<%-- [2026-09-30] 인벤 스타일(자유게시판과 동일 테마, newboard_inven.css) -- 카드형 목록을 표로 교체. 문서형 위키라
     "번호"는 글번호, 제목 옆 v.N은 현재 버전. --%>
<div class="nb inven" data-room="share">
  <jsp:include page="_chrome_top.jsp" />

  <div class="nb-board">
    <div class="ib-box">
      <div class="ib-head">
        <h1>공유게시판<span class="ib-sub">문서형 위키 · 수정하면 버전이 쌓입니다</span></h1>
        <a class="nb-btn primary" href="<%=request.getContextPath()%>/session/newboard/sharesign">✎ 글쓰기</a>
      </div>

      <table class="ib-list">
        <thead>
          <tr><th class="ib-no">글번호</th><th>제목</th><th class="ib-nick">최근수정</th><th class="ib-date">수정일</th></tr>
        </thead>
        <tbody>
          <c:forEach var="share" items="${shares}">
            <tr onclick="location.href='<%=request.getContextPath()%>/newboard/shareView?shareNo=${share.shareNo}'">
              <td class="ib-no">${share.shareNo}</td>
              <td class="ib-title"><a href="<%=request.getContextPath()%>/newboard/shareView?shareNo=${share.shareNo}"><c:out value="${share.shareName}"/></a><span class="ib-ver">v.${share.version}</span></td>
              <td class="ib-nick"><c:out value="${share.userNick}"/></td>
              <td class="ib-date"><fmt:formatDate value="${share.modifyDate}" pattern="MM-dd"/></td>
            </tr>
          </c:forEach>
          <c:if test="${totalPage == 0}"><tr class="ib-empty"><td colspan="4">아직 작성된 문서가 없습니다.</td></tr></c:if>
        </tbody>
      </table>

      <div class="ib-foot">
        <%
          int nbTotal = Integer.parseInt(String.valueOf(request.getAttribute("totalPage")));
          Object nbPageAttr = request.getAttribute("page");
          int nbCur = nbPageAttr == null ? 1 : Integer.parseInt(String.valueOf(nbPageAttr));
          int nbFrom = Math.max(1, nbCur - 4);
          int nbTo = Math.min(nbTotal, nbFrom + 9);
          nbFrom = Math.max(1, nbTo - 9);
          String nbCtx = request.getContextPath();
        %>
        <div class="ib-pager" id="nbPager">
          <% if (nbTotal > 0) { %>
            <% if (nbCur > 1) { %><a href="<%=nbCtx%>/newboard/share?page=<%=nbCur - 1%>">&lt; 이전</a><% } %>
            <% for (int p = nbFrom; p <= nbTo; p++) { %>
              <% if (p == nbCur) { %><span class="cur"><%=p%></span><% } else { %><a href="<%=nbCtx%>/newboard/share?page=<%=p%>"><%=p%></a><% } %>
            <% } %>
            <% if (nbCur < nbTotal) { %><a href="<%=nbCtx%>/newboard/share?page=<%=nbCur + 1%>">다음 &gt;</a><% } %>
          <% } %>
        </div>
      </div>
    </div>
  </div>
</div>
</body>
</html>
