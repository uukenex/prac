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
<title>자유게시판 ::: TH보드</title>
</head>
<body>
<%-- [2026-09-30] "inven 웹사이트처럼" 요청 -- 카드형 목록을 인벤 커뮤니티식 촘촘한 표(번호/제목/글쓴이/
     날짜/조회, 댓글수는 주황 [n])로 교체. 스타일은 newboard_inven.css(.nb.inven 스코프). --%>
<div class="nb inven" data-room="free">
  <jsp:include page="_chrome_top.jsp" />

  <div class="nb-board">
    <div class="ib-box">
      <div class="ib-head">
        <h1>자유게시판<span class="ib-sub">누구나 자유롭게 쓰는 잡담 게시판</span></h1>
        <a class="nb-btn primary" href="<%=request.getContextPath()%>/session/newboard/boardsign">✎ 글쓰기</a>
      </div>

      <table class="ib-list">
        <thead>
          <tr><th class="ib-no">번호</th><th>제목</th><th class="ib-nick">글쓴이</th><th class="ib-date">날짜</th><th class="ib-hit">조회</th></tr>
        </thead>
        <tbody id="nbList">
          <c:forEach var="comment" items="${comments}">
            <tr onclick="location.href='<%=request.getContextPath()%>/newboard/freeView?commentNo=${comment.commentNo}'">
              <td class="ib-no">${comment.commentNo}</td>
              <td class="ib-title"><c:if test="${comment.secretYn=='Y'}"><span class="ib-lock">🔒</span></c:if><a href="<%=request.getContextPath()%>/newboard/freeView?commentNo=${comment.commentNo}"><c:out value="${comment.commentName}"/></a><c:if test="${comment.replyCnt > 0}"><span class="ib-cnt">[${comment.replyCnt}]</span></c:if></td>
              <td class="ib-nick"><c:out value="${comment.userNick}"/></td>
              <td class="ib-date"><fmt:formatDate value="${comment.commentDate}" pattern="MM-dd"/></td>
              <td class="ib-hit">${comment.commentCount}</td>
            </tr>
          </c:forEach>
          <c:if test="${totalPage == 0}"><tr class="ib-empty"><td colspan="5">아직 작성된 글이 없습니다.</td></tr></c:if>
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
            <% if (nbCur > 1) { %><a href="<%=nbCtx%>/newboard/free?page=<%=nbCur - 1%>">&lt; 이전</a><% } %>
            <% for (int p = nbFrom; p <= nbTo; p++) { %>
              <% if (p == nbCur) { %><span class="cur"><%=p%></span><% } else { %><a href="<%=nbCtx%>/newboard/free?page=<%=p%>"><%=p%></a><% } %>
            <% } %>
            <% if (nbCur < nbTotal) { %><a href="<%=nbCtx%>/newboard/free?page=<%=nbCur + 1%>">다음 &gt;</a><% } %>
          <% } %>
        </div>
        <div class="ib-search">
          <select id="nbSearchCategory">
            <option value="제목">제목</option>
            <option value="내용">내용</option>
            <option value="닉네임">닉네임</option>
          </select>
          <input type="text" id="nbSearchInput" placeholder="검색어를 입력하세요">
          <button type="button" class="nb-btn" id="nbSearchBtn">검색</button>
        </div>
      </div>
    </div>
  </div>
</div>

<script src="<%=request.getContextPath()%>/assets/js/jquery.min.js"></script>
<script>
  function nbEsc(s){ return String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
  function nbSearch(){
    $.ajax({
      type:'post',
      url:'<%=request.getContextPath()%>/newboard/search',
      data:{ category: $('#nbSearchCategory').val(), keyword: $('#nbSearchInput').val() },
      success:function(res){
        var $list = $('#nbList').empty();
        if (!res || res.length === 0){ $list.append('<tr class="ib-empty"><td colspan="5">검색 결과가 없습니다.</td></tr>'); }
        $(res).each(function(idx, item){
          var d = new Date(item.commentDate);
          var m = ('0'+(d.getMonth()+1)).slice(-2), day = ('0'+d.getDate()).slice(-2);
          var lock = item.secretYn === 'Y' ? '<span class="ib-lock">🔒</span>' : '';
          var url = '<%=request.getContextPath()%>/newboard/freeView?commentNo=' + item.commentNo;
          var cnt = item.replyCnt > 0 ? '<span class="ib-cnt">[' + item.replyCnt + ']</span>' : '';
          $list.append(
            '<tr onclick="location.href=\'' + url + '\'">'
            + '<td class="ib-no">' + item.commentNo + '</td>'
            + '<td class="ib-title">' + lock + '<a href="' + url + '">' + nbEsc(item.commentName) + '</a>' + cnt + '</td>'
            + '<td class="ib-nick">' + nbEsc(item.userNick) + '</td>'
            + '<td class="ib-date">' + m + '-' + day + '</td>'
            + '<td class="ib-hit">' + item.commentCount + '</td></tr>'
          );
        });
        $('#nbPager').hide();
      },
      error:function(){ alert('검색 중 오류가 발생했습니다.'); }
    });
  }
  $('#nbSearchBtn').on('click', nbSearch);
  $('#nbSearchInput').on('keydown', function(e){ if (e.key === 'Enter') { e.preventDefault(); nbSearch(); } });
</script>
</body>
</html>
