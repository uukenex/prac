<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>비밀게시판 글쓰기 ::: TH보드</title>
<script type="text/javascript" src="//code.jquery.com/jquery-1.11.0.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/se2/js/HuskyEZCreator.js?v=<%=System.currentTimeMillis()%>" charset="utf-8"></script>
</head>
<body>
<div class="nb" data-room="secret">
  <jsp:include page="../nonsession/newboard/_chrome_top.jsp" />

  <div class="nb-board">
    <div class="nb-toolbar"><h1>글쓰기</h1></div>
    <div class="nb-room-note">🔒 비밀게시판 — 로그인한 유저만 열람 가능합니다</div>
    <form action="<%=request.getContextPath()%>/session/newboard/secretWrite" method="post" id="nbFrm">
      <div class="nb-field">
        <label>제목</label>
        <input type="text" name="title" id="nbTitle" placeholder="제목을 입력해주세요">
      </div>
      <div class="nb-field">
        <label>내용</label>
        <textarea name="content" id="content" rows="9" cols="100"
          style="width:100%; height:380px; min-width:200px; display:none;"></textarea>
      </div>
      <div class="nb-form-actions">
        <button type="button" class="nb-btn" onclick="history.back();">취소</button>
        <button type="button" class="nb-btn primary" id="nbSave">등록</button>
      </div>
    </form>
  </div>
</div>

<script src="<%=request.getContextPath()%>/game_set/js/comutil.js?v=<%=System.currentTimeMillis()%>"></script>
<script>
$(function(){
  var editor_object = [];
  nhn.husky.EZCreator.createInIFrame({
    oAppRef: editor_object, elPlaceHolder: "content", sSkinURI: "/se2/SmartEditor2Skin.html?v=8",
    htParams: { bUseToolbar:true, bUseVerticalResizer:false, bUseModeChanger:false }
  });

  $('#nbSave').click(function(){
    editor_object.getById["content"].exec("UPDATE_CONTENTS_FIELD", []);
    if ($('#nbTitle').val() === '') { alert('제목을 입력해주세요.'); return; }
    $('#content').val(base64toFile($('#content').val()));
    $('#nbFrm').submit();
  });

  function base64toFile(content) {
    let div = document.createElement("div");
    div.innerHTML = content;
    var base64Images = div.querySelectorAll("img");
    var set = new Set();
    base64Images.forEach(v => set.add(v.src));
    let imgFiles = [];
    for (const value of set) {
      if (value.startsWith("data:")) {
        let arr = value.split(',');
        let mime = arr[0].match(/:(.*?);/)[1];
        let bstr = atob(arr[1]);
        let n = bstr.length;
        let u8arr = new Uint8Array(n);
        while (n--) { u8arr[n] = bstr.charCodeAt(n); }
        imgFiles.push(new File([u8arr], "image", { type: mime }));
      }
    }
    let fdata = new FormData();
    imgFiles.forEach((f, idx) => fdata.append("file" + idx, f));
    fdata.append("length", imgFiles.length);
    $.ajax({
      url: "/base64imgUpload", data: fdata, method: "POST",
      enctype: "multipart/form-data; charset=utf-8", processData: false, contentType: false, cache: false, async: false,
      success: function (data) {
        if (data) {
          for (let i = 0; i < data.length; i++) {
            content = content.split(base64Images[i].src).join("/imgServer/" + data[i]);
          }
        } else { alert('이미지 업로드 실패'); }
      },
      error: function () { alert('이미지 업로드 실패'); }
    });
    return content;
  }
});
</script>
</body>
</html>
