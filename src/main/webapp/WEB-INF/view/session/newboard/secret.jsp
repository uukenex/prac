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
<title>비밀게시판 ::: TH보드</title>
</head>
<body>
<div class="nb" data-room="secret">
  <jsp:include page="../nonsession/newboard/_chrome_top.jsp" />

  <div class="nb-board">
    <div class="nb-toolbar">
      <div>
        <h1>🔒 비밀게시판</h1>
        <div class="nb-sub">로그인한 유저만 볼 수 있습니다</div>
      </div>
      <a class="nb-btn primary" href="<%=request.getContextPath()%>/session/newboard/secretsign">✎ 글쓰기</a>
    </div>

    <div class="nb-search">
      <select id="nbSearchCategory">
        <option value="제목">제목</option>
        <option value="내용">내용</option>
        <option value="닉네임">닉네임</option>
      </select>
      <input type="text" id="nbSearchInput" placeholder="검색어를 입력하세요">
      <button type="button" class="nb-btn" id="nbSearchBtn">검색</button>
    </div>

    <ul class="nb-list" id="nbList">
      <c:forEach var="comment" items="${comments}">
        <li class="nb-row" onclick="location.href='<%=request.getContextPath()%>/session/newboard/secretView?commentNo=${comment.commentNo}'">
          <span class="nb-no mono">${comment.commentNo}</span>
          <span class="nb-avatar">${fn:length(comment.userNick) > 0 ? fn:substring(comment.userNick,0,1) : '?'}</span>
          <div class="nb-main">
            <span class="nb-ttl">${comment.commentName}<span class="nb-reply">(${comment.replyCnt})</span></span>
            <div class="nb-metaline"><span>${comment.userNick}</span><span><fmt:formatDate value="${comment.commentDate}" pattern="yy-MM-dd"/></span></div>
          </div>
          <span class="nb-stat">조회 ${comment.commentCount}</span>
        </li>
      </c:forEach>
      <c:if test="${totalPage == 0}"><li class="nb-empty">아직 작성된 글이 없습니다.</li></c:if>
    </ul>

    <p class="nb-pager" id="nbPager">
      <%!int i;%>
      <% for (int i = 1; i <= Integer.parseInt(String.valueOf(request.getAttribute("totalPage"))); i++) { %>
      <a href="<%=request.getContextPath()%>/session/newboard/secret?page=<%=i%>"><%=i%></a>
      <% } %>
    </p>
  </div>
</div>

<script src="<%=request.getContextPath()%>/assets/js/jquery.min.js"></script>
<script>
  $('#nbSearchBtn').on('click', function(){
    $.ajax({
      type:'post',
      url:'<%=request.getContextPath()%>/session/newboard/secretSearch',
      data:{ category: $('#nbSearchCategory').val(), keyword: $('#nbSearchInput').val() },
      success:function(res){
        var $list = $('#nbList').empty();
        if (!res || res.length === 0){ $list.append('<li class="nb-empty">검색 결과가 없습니다.</li>'); }
        $(res).each(function(idx, item){
          var d = new Date(item.commentDate);
          var y = String(d.getFullYear()).substr(2,2), m = ('0'+(d.getMonth()+1)).slice(-2), day = ('0'+d.getDate()).slice(-2);
          var initial = item.userNick ? item.userNick.charAt(0) : '?';
          $list.append(
            '<li class="nb-row" onclick="location.href=\'<%=request.getContextPath()%>/session/newboard/secretView?commentNo='+item.commentNo+'\'">'
            + '<span class="nb-no mono">'+item.commentNo+'</span>'
            + '<span class="nb-avatar">'+initial+'</span>'
            + '<div class="nb-main"><span class="nb-ttl">'+item.commentName+'<span class="nb-reply">('+item.replyCnt+')</span></span>'
            + '<div class="nb-metaline"><span>'+item.userNick+'</span><span>'+y+'-'+m+'-'+day+'</span></div></div>'
            + '<span class="nb-stat">조회 '+item.commentCount+'</span></li>'
          );
        });
        $('#nbPager').hide();
      },
      error:function(){ alert('검색 중 오류가 발생했습니다.'); }
    });
  });
</script>
</body>
</html>
