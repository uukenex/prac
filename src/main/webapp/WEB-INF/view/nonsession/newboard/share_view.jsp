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
<title>${share.shareName} ::: TH보드</title>
</head>
<body>
<%-- [2026-09-30] 인벤 스타일 문서 상세(자유게시판과 동일 테마). 버전 선택/수정/비밀게시판 이동 기능은 그대로. --%>
<div class="nb inven" data-room="share">
  <jsp:include page="_chrome_top.jsp" />

  <div class="nb-board">
    <div class="ib-box">
      <div class="ib-post-head">
        <div class="ib-crumbs">공유게시판 &gt; No.${share.shareNo}</div>
        <h1>${share.shareName} <span class="ib-ver">v.${share.version}</span></h1>
        <div class="ib-byline">
          <c:choose>
            <c:when test="${share.modifyId == '999999'}">
              <span><b>${share.userNick}</b> (${share.ipAddr})</span>
            </c:when>
            <c:otherwise>
              <span><b>${share.userNick}</b></span>
            </c:otherwise>
          </c:choose>
          <span><fmt:formatDate value="${share.modifyDate}" pattern="yyyy-MM-dd HH:mm"/> 수정</span>
        </div>
      </div>

      <c:if test="${share.version > 5}">
      <div class="nb-version-bar" style="padding:10px 14px; margin:0; border-bottom:1px solid var(--iv-line); background:#fafbfc;">
        <select id="nbVersionSel" onchange="location.href='<%=request.getContextPath()%>/newboard/shareView?shareNo=${share.shareNo}&version='+this.value">
          <option value="0">과거버전으로</option>
          <c:forEach var="v" items="${shareHist}">
            <option value="${v}">과거버전 ${v}</option>
          </c:forEach>
        </select>
        <button type="button" class="nb-btn" onclick="location.href='<%=request.getContextPath()%>/newboard/shareView?shareNo=${share.shareNo}&version=0';">최신버전으로</button>
      </div>
      </c:if>

      <div class="ib-post-body" id="nbPostBody">${share.shareContent}</div>

      <div class="ib-actions">
        <a class="nb-btn" href="<%=request.getContextPath()%>/newboard/share">목록</a>
        <form action="<%=request.getContextPath()%>/newboard/s/shareUpdate" method="post" style="display:inline">
          <input type="hidden" name="shareNo" value="${share.shareNo}">
          <button type="submit" class="nb-btn">수정</button>
        </form>
        <c:if test="${'THJEON'==Users.userId}">
        <form action="<%=request.getContextPath()%>/session/newboard/moveShareToSecret" method="post" style="display:inline"
              onsubmit="return confirm('이 글을 비밀게시판으로 이동할까요? (공유게시판 원본과 버전 이력은 삭제됩니다)');">
          <input type="hidden" name="shareNo" value="${share.shareNo}">
          <button type="submit" class="nb-btn">🔒 비밀게시판으로 이동</button>
        </form>
        </c:if>
      </div>
    </div>
  </div>
</div>

<script src="<%=request.getContextPath()%>/assets/js/jquery.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/assets/js/jquery.fancybox.js"></script>
<script>
  $('#nbPostBody img').each(function(){
    $(this).wrap('<a class="nb-fancy" href="'+$(this).attr('src')+'" data-fancybox-group="post"></a>');
  });
  $('.nb-fancy').fancybox();
</script>
</body>
</html>
