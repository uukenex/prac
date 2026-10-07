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
<title><c:out value="${comment.commentName}"/> ::: TH보드</title>
</head>
<body>
<%-- [2026-09-30] 인벤 스타일 글 상세 -- 회색 제목 헤더 + 메타 한 줄 + 본문 박스 + 댓글 박스. 스타일은
     newboard_inven.css(.nb.inven 스코프). 기능(수정/삭제/비밀게시판 이동/댓글 등록/Fancybox)은 그대로. --%>
<div class="nb inven" data-room="free">
  <jsp:include page="_chrome_top.jsp" />

  <div class="nb-board">
    <c:choose>
      <c:when test="${secretLocked}">
        <div class="nb-locked">🔒 비밀글입니다. 로그인 후 확인할 수 있습니다.</div>
      </c:when>
      <c:otherwise>
        <div class="ib-box">
          <div class="ib-post-head">
            <div class="ib-crumbs">자유게시판 &gt; No.${comment.commentNo}</div>
            <h1><c:if test="${comment.secretYn=='Y'}">🔒 </c:if><c:out value="${comment.commentName}"/></h1>
            <div class="ib-byline">
              <span><b><c:out value="${comment.userNick}"/></b></span>
              <span><fmt:formatDate value="${comment.commentDate}" pattern="yyyy-MM-dd HH:mm"/></span>
              <span>조회 ${comment.commentCount}</span>
              <span>댓글 ${fn:length(replys)}</span>
            </div>
          </div>

          <div class="ib-post-body" id="nbPostBody">${comment.commentContent}</div>

          <div class="ib-actions">
            <a class="nb-btn" href="<%=request.getContextPath()%>/newboard/free">목록</a>
            <c:if test="${comment.userId==Users.userId}">
            <form action="<%=request.getContextPath()%>/session/newboard/freeUpdate" method="post" style="display:inline">
              <input type="hidden" name="commentNo" value="${comment.commentNo}">
              <button type="submit" class="nb-btn">수정</button>
            </form>
            <form action="<%=request.getContextPath()%>/newboard/freeDelete" method="post" style="display:inline"
                  onsubmit="return confirm('삭제하시겠습니까?');">
              <input type="hidden" name="commentNo" value="${comment.commentNo}">
              <button type="submit" class="nb-btn danger">삭제</button>
            </form>
            </c:if>
            <c:if test="${'THJEON'==Users.userId}">
            <form action="<%=request.getContextPath()%>/session/newboard/moveFreeToSecret" method="post" style="display:inline"
                  onsubmit="return confirm('이 글을 비밀게시판으로 이동할까요? (자유게시판에서는 사라집니다)');">
              <input type="hidden" name="commentNo" value="${comment.commentNo}">
              <button type="submit" class="nb-btn">🔒 비밀게시판으로 이동</button>
            </form>
            </c:if>
          </div>
        </div>

        <div class="ib-replies">
          <div class="ib-replies-head">댓글<em>${fn:length(replys)}</em></div>
          <c:if test="${fn:length(replys) == 0}"><div class="ib-noreply">첫 댓글을 남겨보세요.</div></c:if>
          <c:forEach var="reply" items="${replys}">
            <div class="ib-reply">
              <span class="ib-who"><c:out value="${reply.userNick}"/></span><span class="ib-when"><fmt:formatDate value="${reply.replyDate}" pattern="yy-MM-dd HH:mm"/></span>
              <div class="ib-txt"><c:out value="${reply.replyContent}"/></div>
            </div>
          </c:forEach>
          <c:if test="${!empty Users.userId}">
          <div class="ib-reply-box">
            <input type="text" id="nbReplyContent" placeholder="댓글을 입력하세요.">
            <button type="button" class="nb-btn primary" id="nbReplyBtn">등록</button>
          </div>
          </c:if>
          <c:if test="${empty Users.userId}">
          <div class="ib-reply-box">
            <input type="text" placeholder="로그인 후 댓글을 남길 수 있습니다." disabled>
          </div>
          </c:if>
        </div>
      </c:otherwise>
    </c:choose>
  </div>
</div>

<script src="<%=request.getContextPath()%>/assets/js/jquery.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/assets/js/jquery.fancybox.js"></script>
<script>
  // 본문 안의 이미지는 Fancybox로 확대(기존 window.open() 방식의 DIY 코드 대신).
  $('#nbPostBody img').each(function(){
    $(this).wrap('<a class="nb-fancy" href="'+$(this).attr('src')+'" data-fancybox-group="post"></a>');
  });
  $('.nb-fancy').fancybox();

  $('#nbReplyBtn').on('click', function(){
    $.ajax({
      type:'post',
      url:'<%=request.getContextPath()%>/session/replyRegist',
      data:{ userId:'${Users.userId}', replyContent:$('#nbReplyContent').val(), commentNo:'${comment.commentNo}' },
      success:function(){ alert('등록되었습니다.'); location.reload(); },
      error:function(xhr){ alert(xhr.responseText); }
    });
  });
</script>
</body>
</html>
